package org.goodmath.polytope.client.workspace

import org.goodmath.polytope.client.ClientConfig
import org.goodmath.polytope.client.rest.RestApiClient
import org.goodmath.polytope.common.stashable.Workspace
import java.nio.file.Path
import kotlin.io.path.div
import maryk.rocksdb.RocksDB
import maryk.rocksdb.openRocksDB
import org.goodmath.polytope.client.ClientException
import kotlin.io.path.exists

const val stateKey = "workspace"

typealias StateMap = Map<Path, String>
class ClientWorkspace(cfg: ClientConfig) {
    private val apiClient = RestApiClient(cfg.serverUrl, cfg.userId, cfg.password)
    private val stateDbPath = cfg.wsPath / ".polytope" / "wsDb"
    private val stateDb: RocksDB = openRocksDB(stateDbPath.toString())

    private var wsState: Workspace? = null
    private fun getState(): Workspace {
        if (wsState == null) {
            wsState = stateDb.getTyped(stateKey)
        }
        return wsState!!
    }



    fun populate() {


    }

    fun findChanges(): List<Path> {
        TODO()
    }

    fun update(newState: Workspace) {
        val currentState = getState()
        if (currentState.modifiedArtifacts.isNotEmpty()) {
            throw ClientException("populating workspace", "workspace contains unsaved changes")
        }
        stateDb.putTyped(stateKey, newState)
        // Delete any artifacts that are not in the new state. We can do
        // that by getting a list of paths from the new state, and comparing
        // to the current list of paths.

        // Next, we'll iterate through the paths in the workspace.
        // For each file, we'll download the current content, and compare
        // it to the existing filesystem; if the path doesn't exist in the
        // filesystem, or if we detected that the content is different, we'll
        // write the file.


    }

    fun close() {
        TODO()
    }

    companion object {
        fun createWorkspace(cfg: ClientConfig, initial: Workspace): ClientWorkspace {
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
            db.putTyped(stateKey, initial)
            // populate the workspace.
            val cWorkspace = ClientWorkspace(cfg)
            cWorkspace.populate()
            return cWorkspace
        }
    }

}