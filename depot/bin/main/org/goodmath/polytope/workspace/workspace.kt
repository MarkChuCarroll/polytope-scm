/*
 * Copyright 2023 Mark C. Chu-Carroll
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.goodmath.polytope.workspace

import org.goodmath.polytope.PtException
import org.goodmath.polytope.depot.Stash
import org.goodmath.polytope.depot.Config
import org.goodmath.polytope.depot.Depot
import org.goodmath.polytope.depot.agents.*
import org.goodmath.polytope.depot.stashes.*
import org.goodmath.polytope.depot.util.*
import org.rocksdb.ColumnFamilyHandle
import org.rocksdb.RocksDB
import java.time.Instant

data class WorkspaceDescriptor(
    val id: Id<WorkspaceRecord>,
    val wsName: String,
    val project: String,
    val creator: String,
    val description: String,
    val createdAt: Long,
    val lastModified: Long
)

data class WorkspaceRecord(
    val id: Id<WorkspaceRecord>,
    val project: String,
    val name: String,
    val creator: String,
    val createdAt: Long,
    var lastModified: Long,
    val description: String,
    var basis: ProjectVersionSpecifier,
    val baselineId: Id<Artifact>,
    var baselineVersion: Id<ArtifactVersion>,
    var history: String,
    var change: String?,
    val workingVersions: MutableMap<Id<Artifact>, Id<ArtifactVersion>>,
    val modifiedArtifacts: MutableSet<Id<Artifact>> = HashSet(),
    val conflicts: MutableList<MergeConflict> = arrayListOf()
)


class WorkspaceStash(
    private val depot: Depot,
    private val workspaceColumn: ColumnFamilyHandle
): Stash {

    private val db = depot.db

    data class WorkspaceIndexKey(
        val project: String,
        val name: String,
    )

    data class WorkspaceIndex(
        val entries: MutableMap<WorkspaceIndexKey, Id<WorkspaceRecord>>
    ) {
        fun get(project: String, name: String): Id<WorkspaceRecord>? {
            return entries[WorkspaceIndexKey(project, name)]
        }

        fun add(project: String, name: String, id: Id<WorkspaceRecord>) {
            entries[WorkspaceIndexKey(project, name)] = id
        }
    }

    private val indexKey = "__WORKSPACE__INDEX__"
    private var index: WorkspaceIndex? = null
    private fun getWorkspaceIndex(): WorkspaceIndex {
        if (index == null) {
            index = db.getTyped(workspaceColumn, indexKey)!!
        }
        return index!!
    }

    private fun addToIndex(project: String, name: String, id: Id<WorkspaceRecord>) {
        val idx = getWorkspaceIndex()
        idx.add(project, name, id)
        db.updateTyped(workspaceColumn, indexKey, idx)
    }

    /**
     * Create a new workspace for a project.
     * @param auth the authenticated user performing the operation.
     * @param project the name of the project
     * @param name the name of the new workspace
     * @param history the name of the history that will initially populate the workspace.
     * @param description a description of the workspace.
     * @return an initialized, populated workspace.
     */
    fun createWorkspace(
        auth: AuthenticatedUser,
        project: String,
        name: String,
        history: String,
        description: String
    ): Workspace {
        depot.users.validatePermissions(auth, Action.writeProject(project))
        if (workspaceExists(auth, project, name)) {
            throw PtException(
                PtException.Kind.Conflict,
                "A workspace named $name already exists in project $project"
            )
        }
        val histStep = depot.histories.retrieveHistoryStep(
            auth, project,
            history
        )
        val wsRecord = WorkspaceRecord(
            id = newId<WorkspaceRecord>(ID_WORKSPACE),
            name = name,
            project = project,
            history = history,
            baselineId = histStep.baselineId,
            baselineVersion = histStep.baselineVersionId,
            creator = auth.userId,
            createdAt = Instant.now().toEpochMilli(),
            lastModified = Instant.now().toEpochMilli(),
            basis = ProjectVersionSpecifier.history(project, histStep.historyName, histStep.number),
            description = description,
            change = null,
            workingVersions = HashMap()
        )

        db.putTyped(workspaceColumn, wsRecord.id, wsRecord)
        addToIndex(project, name, wsRecord.id)
        return Workspace(auth, depot, db, workspaceColumn, wsRecord)
    }

    /**
     * Get a list of the workspaces created under a project.
     * @param auth the authenticated user performing the operation.
     * @param projectName the name of the project.
     * @param namePattern an optional regex; if provided, only workspaces whose name
     *    matches the pattern will be returned.
     * @return a list of workspace descriptors
     */
    fun listWorkspaces(
        auth: AuthenticatedUser,
        projectName: String,
        namePattern: Regex? = null
    ): List<WorkspaceDescriptor> {
        return getWorkspaceIndex().entries.filter { (key, _) ->
            key.project == projectName && namePattern?.matches(key.name)?:true
        }.map { (_, id) ->
            val ws = retrieveWorkspaceRecordById(auth, projectName, id)
            WorkspaceDescriptor(
                id = id,
                wsName = ws.name,
                project = projectName,
                creator = ws.creator,
                description = ws.description,
                createdAt = ws.createdAt,
                lastModified = ws.lastModified
            )
        }
    }

    /**
     * Check if a workspace with a name exists in a project.
     * @param auth the authenticated user performing the operation.
     * @param project the name of the project
     * @param name the name
     * @return true if a workspace with the name exists in the project.
     */
    private fun workspaceExists(
        auth: AuthenticatedUser,
        project: String,
        name: String
    ): Boolean {
        depot.users.validatePermissions(auth, Action.readProject(project))
        return (getWorkspaceIndex().get(project, name) != null)
    }

    private fun getIdentifierForWorkspace(
        auth: AuthenticatedUser,
        project: String,
        name: String
    ): Id<WorkspaceRecord> {
        depot.users.validatePermissions(auth, Action.readProject(project))
        return getWorkspaceIndex().get(project, name) ?: throw PtException(
            PtException.Kind.NotFound,
            "Workspace $name not found in project $project"
        )
    }

    /**
     * Get a workspace for a project given its name.
     * @param auth the authenticated user performing the operation.
     * @param project the name of the project
     * @return the workspace
     */
    fun retrieveWorkspaceByName(
        auth: AuthenticatedUser,
        project: String,
        name: String
    ): Workspace {
        val id = getIdentifierForWorkspace(auth, project, name)
        return Workspace(
            auth, depot, db, workspaceColumn, retrieveWorkspaceRecordById(
                auth,
                project, id
            )
        )
    }

    private fun retrieveWorkspaceRecordById(
        auth: AuthenticatedUser,
        project: String,
        id: Id<WorkspaceRecord>
    ): WorkspaceRecord {
        depot.users.validatePermissions(auth, Action.readProject(project))
        val record: WorkspaceRecord = db.getTyped(workspaceColumn, id) ?: throw PtException(
            PtException.Kind.NotFound,
            "workspace $id not found in project $project"
        )
        if (record.project != project) {
            throw PtException(
                PtException.Kind.NotFound,
                "workspace $id not found in project $project"
            )
        }
        return record

    }


    override fun initStorage(config: Config) {
        val idx = WorkspaceIndex(mutableMapOf())
        db.putTyped(workspaceColumn, indexKey, idx)
    }

}

class Workspace(
    private val auth: AuthenticatedUser,
    private val depot: Depot,
    private val db: RocksDB,
    private val wsColumn: ColumnFamilyHandle,
    private var wsRecord: WorkspaceRecord
) {

    private fun getBaselineVersion(id: Id<ArtifactVersion>): ArtifactVersion {
        return depot.artifacts.retrieveVersion(
            auth, wsRecord.project, wsRecord.baselineId, id)
    }

    val cachedBaselines: MutableMap<Id<ArtifactVersion>, Baseline> = HashMap()
    private fun getBaseline(id: Id<ArtifactVersion>): Baseline {
            val base = getBaselineVersion(id)
            if (base.artifactType != BaselineAgent.artifactType || base.artifactId != wsRecord.baselineId) {
                throw PtException(PtException.Kind.InvalidParameter, "not a baseline")
            }
        return BaselineAgent.decodeFromString(base.content)
    }

    fun currentBaseline(): Baseline =
        getBaseline(wsRecord.baselineVersion)

    val project
        get() = wsRecord.project

    val id
        get() = wsRecord.id

    val name
        get() = wsRecord.name

    // *************************************************
    //  workspace population methods.
    // *************************************************

    /**
     * Create a new change, and set up the workspace to operate in it.
     *
     * The basis for the change will be the current basis of the workspace.
     * To choose a starting point, just populate the workspace with whatever
     * basis you want, and then start a change.
     *
     * Most of the time, the starting basis will be the current version
     * of a history; while it's possible to do other things, you can't
     * deliver a change to a history without being up-to-date with it,
     * so you'll need to do some kind of merging to be able to deliver.
     * @param auth
     * @param changeName
     * @param description
     */
    fun createChange(
        auth: AuthenticatedUser,
        changeName: String,
        description: String
    ): Change {
        depot.users.validatePermissions(auth, Action.writeProject(project))
        val ch = depot.changes.createChange(auth, project, wsRecord.history,changeName,
            wsRecord.basis,
            description)
        wsRecord.change = ch.name
        updateStoredWorkspace()
        return ch
    }

    /**
     * Configure and populate the workspace for an existing in-progress change.
     * @param history the name of the history containing the change.
     * @param changeName the name of the change.
     */
    fun openChange(
        history: String,
        changeName: String
    ) {
        depot.users.validatePermissions(auth, Action.writeProject(project))
        if (wsRecord.modifiedArtifacts.isNotEmpty()) {
            throw PtException(PtException.Kind.UserError,
                "A new change can't be opened in a workspace with unsaved changes")
        }
        val basis = ProjectVersionSpecifier.change(wsRecord.project, history, changeName)
        setBasis(basis)
    }

    suspend fun createChange(name: String, description: String) {
        TODO()
    }

    suspend fun openHistory(name: String) {

    }

    
    /**
     * Populate the workspace from a basis point. Most of the time, this should
     * be a history version.
     * @param projectVersionSpecifier
     */
    fun setBasis(
        projectVersionSpecifier: ProjectVersionSpecifier
    ) {
        depot.users.validatePermissions(auth, Action.writeProject(project))
        if (wsRecord.modifiedArtifacts.isNotEmpty()) {
            throw PtException(PtException.Kind.UserError,
                "Basis can't be changed in a workspace with unsaved changes")
        }
        if (wsRecord.conflicts.isNotEmpty()) {
            throw PtException(PtException.Kind.UserError,
                "Basis can't be changed in a workspace with unresolved conflicts")
        }
        val resolved = depot.projects.resolveProjectVersionSpecifier(auth, projectVersionSpecifier)
        wsRecord.basis = projectVersionSpecifier
        wsRecord.baselineVersion = resolved
        updateStoredWorkspace()
    }

    /**
     * Get the current root directory of the workspace.
     */
    fun getRootDir(): Directory {
        val baseline = currentBaseline()
        val dirVersion = depot.artifacts.retrieveVersion(
            auth,
            project,
            baseline.rootDir,
            baseline.entries[baseline.rootDir]!!
        )
        return DirectoryAgent.decodeFromString(dirVersion.content)
    }

    private fun getArtifactAtPath(pathParts: List<String>): Id<Artifact> {
        val cbl = currentBaseline()
        val rootDir = cbl.rootDir
        var artId: Id<Artifact> = rootDir
        for (part in pathParts) {
            System.err.println("PathParts = ${pathParts}, currently at $part")
            val art = depot.artifacts.retrieveVersion(
                auth, project,
                artId, cbl.entries[artId]!!
            )
            if (art.artifactType != DirectoryAgent.artifactType) {
                throw PtException(
                    PtException.Kind.NotFound,
                    "Invalid path ${pathParts.joinToString("/")}"
                )
            }
            val dir = DirectoryAgent.decodeFromString(art.content)
            artId = dir.getBinding(part) ?: throw PtException(
                PtException.Kind.NotFound,
                "Path ${pathParts.joinToString("/")} not found in workspace"
            )
        }
        return artId
    }

    /**
     * Get the current contents of the directory object located at a path in
     * the workspace.
     * @param pathParts the directory path, structured as a list of path segments.
     * @return a pair of the directory artifact version, and the decoded directory content.
     */
    private fun getDir(pathParts: List<String>): Pair<ArtifactVersion, Directory> {
        val artId = getArtifactAtPath(pathParts)
        val art = depot.artifacts.retrieveVersion(
            auth, project,
            artId, currentBaseline().get(artId)!!
        )
        if (art.artifactType != DirectoryAgent.artifactType) {
            throw PtException(
                PtException.Kind.TypeError,
                "Artifact at ${pathParts.joinToString("/")} is not a directory"
            )
        }
        return Pair(art, DirectoryAgent.decodeFromString(art.content))
    }

    /**
     * Update the stored version of the workspace after a change.
     */
    private fun updateStoredWorkspace() {
        db.updateTyped(wsColumn, id, wsRecord)
    }

    // *************************************************
    // change operations within a populated workspace
    // *************************************************

    /**
     * Get or create a new working version of an artifact in the workspace.
     * @param id the ID of the artifact.
     * @return an ArtifactVersion for the new, modifiable working version of
     *    the artifact.
     */
    private fun getOrCreateWorkingVersion(id: Id<Artifact>): ArtifactVersion {
        if (wsRecord.change == null) {
            throw PtException(
                PtException.Kind.Constraint,
                "Artifacts can only be modified in an open change"
            )
        }

        return if (id !in wsRecord.workingVersions) {
            val working = depot.artifacts.createWorkingVersion(
                auth,
                wsRecord.project, id, if (id == wsRecord.baselineId) {
                    wsRecord.baselineVersion
                } else {
                    currentBaseline().get(id)!!
                })
            wsRecord.workingVersions[id] = working.id
            if (id != wsRecord.baselineId) {
                wsRecord.modifiedArtifacts.add(id)
                val workingBaselineVersion = getOrCreateWorkingVersion(wsRecord.baselineId)
                val workingBaseline = BaselineAgent.decodeFromString(workingBaselineVersion.content)
                workingBaseline.change(id, working.id)
                depot.artifacts.updateWorkingVersion(
                    auth, wsRecord.project, wsRecord.baselineId,
                    workingBaselineVersion.id,
                    BaselineAgent.encodeToString(workingBaseline), null
                )
            } else {
                wsRecord.baselineVersion = working.id
                updateStoredWorkspace()
            }
            working
        } else {
            depot.artifacts.retrieveVersion(auth, wsRecord.project, id,  wsRecord.workingVersions[id]!!)
        }
    }


    /**
     * Add a new file to the project in the workspace.
     * @param path the path of the new object.
     * @param artifactType the type of the new object.
     * @param content the contents of the new object.
     * @return the ID of the newly created artifact.
     */
    fun addFile(
        path: String,
        artifactType: String,
        content: String
    ): Id<Artifact> {
        if (wsRecord.change == null) {
            throw PtException(
                PtException.Kind.Constraint,
                "Artifacts can only be modified in an open change"
            )
        }
        val pathParts = path.split("/")
        val name = pathParts.last()
        val dirPathParts = pathParts.dropLast(1)

        val (dArt, _) = getDir(dirPathParts)
        val workingDirVer = getOrCreateWorkingVersion(dArt.artifactId)
        val workingDir = DirectoryAgent.decodeFromString(workingDirVer.content)

        val baselineVer = getOrCreateWorkingVersion(wsRecord.baselineId)
        val baseline = BaselineAgent.decodeFromString(baselineVer.content)

        val (newArt, newVer) = depot.artifacts.createArtifact(
            auth = auth,
            project = wsRecord.project,
            artifactType = artifactType,
            initialContent = content,
            metadata = emptyMap()
        )
        wsRecord.modifiedArtifacts.add(newArt.id)


        workingDir.addBinding(name, newArt.id)
        depot.artifacts.updateWorkingVersion(auth, project, dArt.artifactId, workingDirVer.id,
            DirectoryAgent.encodeToString(workingDir), null)
        baseline.add(newArt.id, newVer.id)
        baseline.change(dArt.artifactId, workingDirVer.id)

        depot.artifacts.updateWorkingVersion(
            auth = auth,
            project = wsRecord.project,
            artifactId = wsRecord.baselineId,
            versionId = baselineVer.id,
            updatedContent = BaselineAgent.encodeToString(baseline),
            updatedMetadata = null
        )
        return newArt.id
    }

    /**
     * Move a file from its current location to a new path.
     * @param oldPath the path to the file in the current workspace.
     * @param newPath the path to move the file to.
     */
    fun moveFile(
        oldPath: String, newPath: String
    ) {
        if (wsRecord.change == null) {
            throw PtException(
                PtException.Kind.Constraint,
                "Artifacts can only be modified in an open change"
            )
        }
        val oldPathParts = oldPath.split("/")
        val oldPathDir = oldPathParts.dropLast(1)
        val oldName = oldPathParts.last()
        val newPathParts = newPath.split("/")
        val newPathDir = newPathParts.dropLast(1)
        val newName = newPathParts.last()

        if (oldPathDir.joinToString("/") == newPathDir.joinToString("/")) {
            val oldDirId = getArtifactAtPath(oldPathDir)
            val workingDirArt = getOrCreateWorkingVersion(oldDirId)
            val workingDir = DirectoryAgent.decodeFromString(workingDirArt.content)
            val target = workingDir.getBinding(oldName)!!
             workingDir.removeBinding(oldName)
            workingDir.addBinding(newName, target)
            depot.artifacts.updateWorkingVersion(
                auth, project, oldDirId,
                workingDirArt.id, DirectoryAgent.encodeToString(workingDir),
                null
            )
        } else {
            val srcDirId = getArtifactAtPath(oldPathDir)
            val tgtDirId = getArtifactAtPath(newPathDir)
            val workingSrcDirVer = getOrCreateWorkingVersion(srcDirId)
            val workingTgtDirVer = getOrCreateWorkingVersion(tgtDirId)
            val srcDir = DirectoryAgent.decodeFromString(workingSrcDirVer.content)
            val tgtDir = DirectoryAgent.decodeFromString(workingTgtDirVer.content)
            val movedId = srcDir.getBinding(oldName)
                ?: throw PtException(
                    PtException.Kind.NotFound,
                    "Path $oldPath not found"
                )
            val updatedSrcDir = srcDir.copy()
            updatedSrcDir.removeBinding(oldName)
            val updatedTgtDir = tgtDir.copy()
            tgtDir.addBinding(newName, movedId)
            depot.artifacts.updateWorkingVersion(
                auth, project, srcDirId, workingSrcDirVer.id,
                DirectoryAgent.encodeToString(updatedSrcDir), null
            )
            depot.artifacts.updateWorkingVersion(
                auth, project, tgtDirId, workingTgtDirVer.id,
                DirectoryAgent.encodeToString(updatedTgtDir), null
            )
        }
    }

    private fun addTransitiveContentsToList(dir: Directory, result: MutableSet<Id<Artifact>>) {
        for (entry in dir.entries) {
            result.add(entry.artifact)
            val art = depot.artifacts.retrieveVersion(
                auth, project, entry.artifact,
                currentBaseline().get(entry.artifact)!!
            )
            if (art.artifactType == DirectoryAgent.artifactType) {
                val childDir = DirectoryAgent.decodeFromString(art.content)
                addTransitiveContentsToList(childDir, result)
            }
        }
    }

    private fun splitDirAndName(path: String): Pair<List<String>, String> {
        val parts = path.split("/")
        return Pair(parts.dropLast(1), parts.last())
    }

    /**
     * Delete a file from a project.
     * @param path the path to the object to be deleted.
     * @return a list of the artifact IDs of objects removed as a result of
     *   this operation. (If the artifact isn't a directory, this will be just
     *   a singleton list of the ID of the deleted abject; if it was a directory,
     *   then this will be a list of IDs of all artifacts transitively contained
     *   in that directory.)
     */
    fun deleteFile(path: String): Set<String> {
        if (wsRecord.change == null) {
            throw PtException(
                PtException.Kind.Constraint,
                "Artifacts can only be modified in an open change"
            )
        }
        val (dirParts, name) = splitDirAndName(path)
        val deletedId = getArtifactAtPath(path.split("/"))
        val dirId = getArtifactAtPath(dirParts)
        val workingDirVer = getOrCreateWorkingVersion(dirId)
        val dir = DirectoryAgent.decodeFromString(workingDirVer.content)
        dir.removeBinding(name)
        depot.artifacts.updateWorkingVersion(auth,
            project, workingDirVer.artifactId, workingDirVer.id,
            DirectoryAgent.encodeToString(dir), emptyMap()
        )
        val deletedVer = depot.artifacts.retrieveVersion(
            auth, project, deletedId,
            currentBaseline().get(deletedId)!!
        )
        val result = mutableSetOf(deletedId)
        if (deletedVer.artifactType == DirectoryAgent.artifactType) {
            val deletedDir = DirectoryAgent.decodeFromString(deletedVer.content)
            addTransitiveContentsToList(deletedDir, result)
        }
        val workingBaselineVer = getOrCreateWorkingVersion(wsRecord.baselineId)
        val workingBaseline = BaselineAgent.decodeFromString(workingBaselineVer.content)
        for (id in result) {
            workingBaseline.remove(id)
        }
        wsRecord.modifiedArtifacts.addAll(result)
        updateStoredWorkspace()
        return result
    }

    /**
     * Modify the file at a path in the workspace.
     * @param path the path of the artifact
     * @param newContent the updated content of the artifact, encoded to
     *   a string by the appropriate agent.
     */
    fun modifyFile(path: String, newContent: String) {
        if (wsRecord.change == null) {
            throw PtException(
                PtException.Kind.Constraint,
                "Artifacts can only be modified in an open change"
            )
        }

        val id = getArtifactAtPath(path.split("/"))
        val working = getOrCreateWorkingVersion(id)
        depot.artifacts.updateWorkingVersion(
            auth, project,
            id, working.id, newContent, null
        )
    }

    /**
     * Get the contents of the artifact at a path.
     * @param path the path
     * @return the contents of the artifact, in the format used by the appropriate
     *   agent.
     */
    fun getFileContents(path: String): String {

        val id = getArtifactAtPath(path.split("/"))
        val ver = depot.artifacts.retrieveVersion(
            auth, project, id,
            currentBaseline().get(id)!!
        )
        return ver.content
    }

    /**
     * List all the paths in a workspace.
     */
    fun listPaths(dirOpt: Directory? = null): List<String> {
        val result = ArrayList<String>()
        val dir = if (dirOpt == null) {
            val dirArt = depot.artifacts.retrieveVersion(
                auth,
                project, currentBaseline().rootDir, currentBaseline().get(currentBaseline().rootDir)!!
            )
            result.add("")
            DirectoryAgent.decodeFromString(dirArt.content)
        } else {
            dirOpt
        }

        for (e in dir.entries) {
            result.add(e.name)
            val eVer = depot.artifacts.retrieveVersion(
                auth,
                project, e.artifact, currentBaseline().get(e.artifact)!!
            )
            if (eVer.artifactType == DirectoryAgent.artifactType) {
                val eDir = DirectoryAgent.decodeFromString(eVer.content)
                val paths = listPaths(eDir)
                for (p in paths) {
                    result.add("${e.name}/$p")
                }
            }
        }
        return result
    }

    // *************************************************
    // u workspace state updates and merges
    // *************************************************

    /**
     * Save the current changes in this workspace as a savepoint.
     * @param description the savepoint description
     * @param resolved a list of the merge conflicts that were resolved in the saved changes.
     * @return the new savepoint.
     */
    fun save(description: String, resolved: List<Id<MergeConflict>>): SavePoint {
        if (wsRecord.change == null) {
            throw PtException(
                PtException.Kind.Constraint,
                "Artifacts can only be modified in an open change"
            )
        }
        for ((artId, verId) in wsRecord.workingVersions) {
            depot.artifacts.commitWorkingVersion(auth, project, artId, verId)
        }
        val sp = depot.changes.createSavePoint(
            auth, project,
            wsRecord.history,
            wsRecord.change!!,
            wsRecord.modifiedArtifacts.toList(),
            description,
            wsRecord.basis,
            wsRecord.baselineVersion
        )
        wsRecord.basis = ProjectVersionSpecifier.savePoint(project, wsRecord.history, sp.id)
        wsRecord.modifiedArtifacts.clear()
        wsRecord.workingVersions.clear()
        wsRecord.conflicts.removeAll { it.id in resolved }
        updateStoredWorkspace()
        return sp
    }

    /**
     * Deliver the current change to its history.
     * @param description a description of the change.
     */
    fun deliver(description: String) {
        depot.users.validatePermissions(auth, Action.writeProject(project))
        if (wsRecord.modifiedArtifacts.isNotEmpty() ) {
            throw PtException(PtException.Kind.Constraint,
                "Workspace must be clean before calling deliver, but you have unsaved changes")
        }
        if (wsRecord.conflicts.isNotEmpty()) {
            throw PtException(PtException.Kind.Constraint,
                "Workspace must be clean before calling deliver, but you have unresolved conflicts")
        }

        System.err.println("Deliver $description")
        TODO()
    }

    /**
     * Get up-to-date with the parent history.
     */
    fun update(): List<MergeConflict> {
        depot.users.validatePermissions(auth, Action.writeProject(project))
        if (wsRecord.modifiedArtifacts.isNotEmpty() ) {
            throw PtException(PtException.Kind.Constraint,
                "Workspace must be clean before calling update, but you have unsaved changes")
        }
        if (wsRecord.conflicts.isNotEmpty()) {
            throw PtException(PtException.Kind.Constraint,
                "Workspace must be clean before calling update, but you have unresolved conflicts")
        }
        val latest = depot.histories.retrieveHistoryStep(auth, project, wsRecord.history)
        if (depot.artifacts.versionIsAncestor(auth, project,
                wsRecord.baselineId,
                latest.baselineVersionId,
                wsRecord.baselineVersion)) {
            // No merge necessary.
            return emptyList()
        }
        val latestBaselineVer = depot.artifacts.retrieveVersion(auth, project, latest.baselineId, latest.baselineVersionId)
        val wsBaselineVer = depot.artifacts.retrieveVersion(auth, project, wsRecord.baselineId, wsRecord.baselineVersion)
        val ancestorId = depot.artifacts.nearestCommonAncestor(auth, project, wsRecord.baselineId,
            latestBaselineVer.id, wsRecord.baselineId)
        val ancestor = depot.artifacts.retrieveVersion(auth, project, latest.baselineId, ancestorId)
        val baselineMergeResult = BaselineAgent.merge(ancestor, latestBaselineVer, wsBaselineVer)
        val conflicts: MutableList<MergeConflict> = arrayListOf()
        for (conflict in baselineMergeResult.conflicts) {
            val baselineConflict = BaselineConflict.decodeFromString(conflict.details)
            when (baselineConflict.type) {
                BaselineConflictType.MOD_DEL -> {
                    // just add this to the conflicts list: there's already a baseline entry
                    // for the modified thing, so the user can choose whether to delete
                    // it or not in order to resolve.
                    conflicts.add(conflict)
                }
                BaselineConflictType.DEL_MOD -> {
                    // Same as above: just add this to the conflicts list: there's already a baseline entry
                    // for the modified thing, so the user can choose whether to delete
                    // it or not in order to resolve.
                    conflicts.add(conflict)
                }
                BaselineConflictType.MOD_MOD -> {
                    // Do a merge of the source and target versions.
                    val artMerge = mergeSingleArtifact(baselineConflict.artifactId,
                        baselineConflict.mergeSourceVersion!!, baselineConflict.mergeTargetVersion!!)
                    // Create a working version of the artifact, and store its proposed
                    // merge there; then add its conflicts to the workspace conflicts list.
                    val working = getOrCreateWorkingVersion(baselineConflict.artifactId)
                    depot.artifacts.updateWorkingVersion(auth, project, baselineConflict.artifactId,
                        working.id, artMerge.proposedMerge, updatedParents = listOf(baselineConflict.mergeSourceVersion,
                            baselineConflict.mergeTargetVersion))
                    if (artMerge.conflicts.isNotEmpty()) {
                        conflicts.addAll(artMerge.conflicts)
                    }
                }
            }
        }
        return baselineMergeResult.conflicts
    }

    private fun mergeSingleArtifact(id: Id<Artifact>, sourceId: Id<ArtifactVersion>, targetId: Id<ArtifactVersion>): MergeResult {
        val ancestorId = depot.artifacts.nearestCommonAncestor(auth, project,
            id, sourceId, targetId)
        val anc = depot.artifacts.retrieveVersion(auth, project, id, ancestorId)
        val src = depot.artifacts.retrieveVersion(auth, project, id, sourceId)
        val tgt = depot.artifacts.retrieveVersion(auth, project, id, targetId)
        val agent = depot.agentFor(anc.artifactType)
        return agent.merge(anc, src, tgt)
    }

    /**
     * Integrate changes from some other location into a workspace. Useful
     * for cherry-picking.
     * @param from the start point of the changes to integrate to this workspace.
     * @param to the target point of the changes to integrate to this workspace.
     */
    fun integrate(
        from: ProjectVersionSpecifier,
        to: ProjectVersionSpecifier
    ): List<MergeConflict> {
        depot.users.validatePermissions(auth, Action.writeProject(project))
        depot.users.validatePermissions(auth, Action.writeProject(project))
        if (wsRecord.modifiedArtifacts.isNotEmpty() ) {
            throw PtException(PtException.Kind.UserError,
                "Workspace must be clean before calling integrate, but you have unsaved changes")
        }
        if (wsRecord.conflicts.isNotEmpty()) {
            throw PtException(PtException.Kind.UserError,
                "Workspace must be clean before calling integrate, but you have unresolved conflicts")
        }

        System.err.println("Integrate from $from to $to")
        TODO()
    }
}
