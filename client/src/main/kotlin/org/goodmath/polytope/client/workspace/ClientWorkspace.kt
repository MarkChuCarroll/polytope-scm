package org.goodmath.polytope.client.workspace

import org.goodmath.polytope.client.ClientConfig
import org.goodmath.polytope.client.rest.RestApiClient
import org.goodmath.polytope.common.stashable.Workspace
import maryk.rocksdb.RocksDB
import maryk.rocksdb.openRocksDB
import org.goodmath.polytope.client.ClientException
import org.goodmath.polytope.common.PtException
import org.goodmath.polytope.common.WorkspaceFileContents
import org.goodmath.polytope.common.agents.text.TextAgent
import java.io.*
import java.nio.file.Path
import java.security.MessageDigest
import java.util.*
import kotlin.io.path.*

private const val workspaceRecordKey = "workspace"
private const val stateMapKey = "file_states"


data class FileState(
    val path: String,
    val hash: String,
)

/**
 * A structure for storing state in the client.
 *
 * For each file in the workspace, there's an entry in the map
 *
 * This can be used in two main ways:
 * - it provides the local source of truth for which files are currently tracked
 *    in the workspace;
 * - it allows us to detect which files in the workspace have changed.
 */
data class StateMap(
        private val fileStates: MutableMap<String, FileState>
) {
    val paths: Set<String>
        get() = fileStates.keys
    operator fun get(path: String): FileState? = fileStates[path]
    operator fun set(path: String, state: FileState) {
        fileStates[path] = state
    }

    fun remove(path: String) {
        fileStates.remove(path)
    }
}

/**
 * When we do a save of a workspace, we want to know what uncommitted
 * changes have occurred. For the simple workspace, we're only really
 * tracking text files: directories are tracked by the server,
 * not the client; and we don't yet handle non-text files. So there are
 * three ways that we can have changes:
 * 1. A file could be added. If the user hasn't told us to add it to the workspace,
 *    then we assume it's a non-tracked file.
 * 2. A file could be deleted. Users *should* have told us about these, but
 *   if they didn't, they're easy enough to find.
 * 3. A file could be modified. This is something we need to check ourselves,
 *    by computing a hash code for the file.
 *
 * This class is a container for the list of altered things that we detected.
 */
data class FileChanges(
    val modifiedFiles: List<String>,
    val deletedFiles: List<String>
)

class ClientWorkspace(cfg: ClientConfig) {

    /**
     * List the paths of all tracked files in the workspace
     */
    fun listFilePaths(): List<Path> {
        val stateMap = getStateMap()
        return stateMap.paths.map { Path(it) }
    }


    /**
     * Populate the workspace, so that its contents match the new state
     * from the server.
     * @param newState the new workspace state
     */
    suspend fun populate(newState: Workspace) {
        val stateMap = getStateMap()
        val paths = apiClient.workspaceListPaths(newState.project, newState.name)
        val newStateMap = StateMap(mutableMapOf())
        for (p in paths) {
            // For a first pass, we'll only deal with text files.
            val file = apiClient.workspaceGetFile(newState.project, newState.name, p)
            if (file.artifactType == TextAgent.artifactType) {
                val fileHash = computeHash(StringReader(file.content))
                if (file.path !in stateMap.paths) {
                    System.err.println("Adding new retrieved file ${file.path}")
                    val path = Path(file.path)
                    if (!path.parent.exists()) {
                        path.parent.toFile().mkdirs()
                    }
                    val out = FileWriter(path.toFile())
                    out.write(file.content)
                    out.close()
                } else {
                    if (fileHash != stateMap[file.path]?.hash) {
                        System.err.println("Updating existing file ${file.path}")
                        val out = FileWriter(file.path)
                        out.write(file.content)
                        out.close()
                    } else {
                        System.err.println("File ${file.path} did not need to be updated")
                    }
                }
                newStateMap[file.path] = FileState(file.path, fileHash)
            }
        }
        val deadPaths = stateMap.paths - paths.toSet()
        for (path in deadPaths) {
            File(path).delete()
        }
        saveState(newState, newStateMap)
    }

    suspend fun addFile(path: String) {
        val stateMap = getStateMap()
        val wsState = getState()
        val content = WorkspaceFileContents(path, TextAgent.artifactType, File(path).readText())
        val updated = apiClient.workspaceAddFile(wsState.project, wsState.name, path,
                content)
        stateMap[path] = FileState(path, computeHash(StringReader(content.content)))
        saveState(updated, stateMap)
    }

    suspend fun deleteFile(path: String, really: Boolean): List<String> {
        val stateMap = getStateMap()
        if (path !in stateMap.paths && !Path(path).exists())   {
            throw PtException(PtException.Kind.NotFound,
                    "File $path not found in workspace")
        }
        val wsState = getState()
        val deleted = apiClient.workspaceDeleteFile(wsState.project, wsState.name,
                path)
        if (really) {
            File(path).deleteRecursively()
        }
        val state = apiClient.workspaceGet(wsState.project, wsState.name)
        for (d in deleted) {
            stateMap.remove(d)
        }
        saveState(state, stateMap)
        return deleted
    }

    suspend fun moveFile(fromPath: String, toPath: String) {
        val ws = getState()
        val updated = apiClient.workspaceMoveFile(
                ws.project, ws.name, fromPath, toPath)
        populate(updated)
    }

    suspend fun setChange(changeName: String) {
        val ws = getState()
        if (ws.modifiedArtifacts.isNotEmpty()) {
            throw PtException(PtException.Kind.Constraint,
                    "Can't set change: The workspace contains unsaved changes")
        }
        // TODO
    }

    suspend fun save(desc: String, resolved: List<String>) {
        val ws = getState()
        val changes = findChanges()
        for (d in changes.deletedFiles) {
            apiClient.workspaceDeleteFile(ws.project, ws.name, d)
        }
        for (m in changes.modifiedFiles) {
            val f = WorkspaceFileContents(m, TextAgent.artifactType, File(m).readText())
            apiClient.workspaceModifyFile(ws.project, ws.name, m, f.content)
        }
        val updated = apiClient.workspaceSave(ws.project, ws.name, desc, resolved)
        populate(updated)
    }

    private fun findChanges(): FileChanges {
        val stateMap = getStateMap()
        val modified = arrayListOf<String>()
        val deleted = arrayListOf<String>()
        for (path in stateMap.paths) {
            val fileState = stateMap[path]!!
            val file = File(fileState.path)
            if (file.exists()) {
                val currentHash = computeHash(FileReader(path))
                if (currentHash != fileState.hash) {
                    modified.add(path)
                }
            } else {
                deleted.add(path)
            }
        }
        return FileChanges(modified, deleted)
    }


    fun close() {
        stateDb.close()
        apiClient.close()
    }



    /**
     * A utility function, which will compute a cryptographic hash string
     * for an input. This is used for comparing file contents to detect
     * changes.
     */
    private fun computeHash(input: Reader): String {
        val base64encoder = Base64.getEncoder()
        val digest = MessageDigest.getInstance("SHA-512")
        val content = input.readText()
        input.close()
        digest.update(content.toByteArray())
        return base64encoder.encodeToString(digest.digest())
    }


    private fun saveState(newState: Workspace, stateMap: StateMap) {
        stateDb.updateTyped(workspaceRecordKey, newState)
        stateDb.updateTyped(stateMapKey, stateMap)
    }

    private fun getStateMap(): StateMap {
        return stateDb.getTyped(stateMapKey) ?: throw PtException(PtException.Kind.Internal,
                "database error in polytope client; could not read state map")
    }

    private fun getState(): Workspace {
        if (wsState == null) {
            wsState = stateDb.getTyped(workspaceRecordKey)
        }
        return wsState!!
    }

    private var wsState: Workspace? = null
    private val apiClient = RestApiClient(cfg.serverUrl, cfg.userId, cfg.password)
    private val stateDbPath = cfg.wsPath / ".polytope" / "wsDb"
    private val stateDb: RocksDB = openRocksDB(stateDbPath.toString())



    companion object {
        suspend fun createWorkspace(cfg: ClientConfig, initial: Workspace): ClientWorkspace {
            // Create the WS directory, if it doesn't already exist.
            if (!cfg.wsPath.exists()) {
                if (!cfg.wsPath.toFile().mkdirs()) {
                    throw ClientException("creating workspace", "could not create workspace directory")
                }
            }
            // Create the .polytope state directory.
            val ptDirPath = cfg.wsPath / ".polytope"
            if (ptDirPath.exists()) {
                throw ClientException("creating workspace", "workspace already exists")
            }
            ptDirPath.toFile().mkdirs()
            // create the local DB.
            val stateDbPath = cfg.wsPath / ".polytope" / "wsDb"
            val db = openRocksDB(stateDbPath.toString())
            // Put the state into the DB,
            db.putTyped(workspaceRecordKey, initial)
            db.putTyped(stateMapKey, StateMap(hashMapOf()))
            // populate the workspace.
            val cWorkspace = ClientWorkspace(cfg)
            cWorkspace.populate(initial)
            return cWorkspace
        }
    }

}