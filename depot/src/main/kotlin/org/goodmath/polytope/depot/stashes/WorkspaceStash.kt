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

package org.goodmath.polytope.depot.stashes

import org.goodmath.polytope.Config
import org.goodmath.polytope.common.*
import org.goodmath.polytope.common.agents.*
import org.goodmath.polytope.common.stashable.*
import org.goodmath.polytope.depot.Depot
import org.goodmath.polytope.depot.util.*
import org.rocksdb.ColumnFamilyHandle
import java.time.Instant

class WorkspaceStash(
    private val depot: Depot,
    private val workspaceColumn: ColumnFamilyHandle
) : Stash {

    private val db = depot.db
    private val wsCache = HashMap<Id<Workspace>, Workspace>()
    val cachedBaselines: MutableMap<Id<ArtifactVersion>, Baseline> = HashMap()

    override fun initStorage(config: Config) {
        val idx = WorkspaceIndex(mutableMapOf())
        db.putTyped(workspaceColumn, indexKey, idx)
    }


    data class WorkspaceIndexKey(
        val project: String,
        val name: String,
    )

    data class WorkspaceIndex(
        val entries: MutableMap<WorkspaceIndexKey, Id<Workspace>>
    ) {
        fun get(project: String, name: String): Id<Workspace>? {
            return entries[WorkspaceIndexKey(project, name)]
        }

        fun add(project: String, name: String, id: Id<Workspace>) {
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

    private fun addToIndex(project: String, name: String, id: Id<Workspace>) {
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
        history: String,
        name: String,
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
        val ws = Workspace(
            id = newId<Workspace>(ID_WORKSPACE),
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

        db.putTyped(workspaceColumn, ws.id, ws)
        addToIndex(project, name, ws.id)
        return ws
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
            key.project == projectName && namePattern?.matches(key.name) ?: true
        }.map { (_, id) ->
            val ws = retrieveWorkspaceById(auth, projectName, id)
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
    ): Id<Workspace> {
        depot.users.validatePermissions(auth, Action.readProject(project))
        return getWorkspaceIndex().get(project, name) ?: throw PtException(
            PtException.Kind.NotFound,
            "Workspace $name not found in project $project"
        )
    }

    private fun retrieveWorkspaceById(
        auth: AuthenticatedUser,
        project: String,
        id: Id<Workspace>
    ): Workspace {
        depot.users.validatePermissions(auth, Action.readProject(project))
        if (wsCache.containsKey(id)) {
            return wsCache[id]!!
        }
        val record: Workspace = db.getTyped(workspaceColumn, id) ?: throw PtException(
            PtException.Kind.NotFound,
            "workspace $id not found in project $project"
        )
        wsCache[id] = record
        if (record.project != project) {
            throw PtException(
                PtException.Kind.NotFound,
                "workspace $id not found in project $project"
            )
        }
        return record
    }


    /**
     * Retrieve a workspace. Workspaces are cached, so this will only read from the
     * database if the workspace isn't already in the cache.
     * @param auth the authenticated user performing the operation
     * @param project the name of the project containing the workspace.
     * @param name the name of the workspace
     */
    fun retrieveWorkspace(
        auth: AuthenticatedUser,
        project: String,
        name: String
    ): Workspace {
        depot.users.validatePermissions(auth, Action.readProject(project))
        val id = getIdentifierForWorkspace(auth, project, name)
        return retrieveWorkspaceById(auth, project, id)
    }

    // Methods for working with individual workspaces


    /**
     * Update the stored version of the workspace after a change.
     */
    private fun updateStoredWorkspace(ws: Workspace) {
        db.updateTyped(workspaceColumn, ws.id, ws)
    }

    private fun getBaselineVersion(
        auth: AuthenticatedUser,
        ws: Workspace,
        id: Id<ArtifactVersion>
    ): ArtifactVersion {
        return depot.artifacts.retrieveVersion(
            auth, ws.project, ws.baselineId, id
        )
    }


    private fun getBaseline(
        auth: AuthenticatedUser,
        ws: Workspace,
        id: Id<ArtifactVersion>
    ): Baseline {
        val base = getBaselineVersion(auth, ws, id)
        return BaselineAgent.decodeFromString(base.content)
    }

    fun currentBaseline(auth: AuthenticatedUser, ws: Workspace): Baseline {
        return getBaseline(auth, ws, ws.baselineVersion)
    }

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
     * @param auth the authenticated user performing the action.
     * @param ws the workspace
     * @param changeName
     * @param description
     */
    fun createChange(
        auth: AuthenticatedUser,
        ws: Workspace,
        changeName: String,
        description: String
    ): Change {
        depot.users.validatePermissions(auth, Action.writeProject(ws.project))
        val ch = depot.changes.createChange(
            auth, ws.project, ws.history, changeName,
            ws.basis,
            description
        )
        ws.change = ch.name
        updateStoredWorkspace(ws)
        return ch
    }

    fun openHistory(
            auth: AuthenticatedUser,
            ws: Workspace,
            history: String): Workspace {
        depot.users.validatePermissions(auth, Action.writeProject(ws.project))
        if (ws.modifiedArtifacts.isNotEmpty()) {
            throw PtException(
                    PtException.Kind.UserError,
                    "A new history can't be opened in a workspace with unsaved changes"
            )
        }
        ws.history = history
        ws.change = null
        val basis = ProjectVersionSpecifier.history(ws.project, history)
        setBasis(auth, ws, basis)
        return ws
    }


    /**
     * Configure and populate the workspace for an existing in-progress change.
     * @param auth the authenticated user performing the action.
     * @param ws the workspace
     * @param history the name of the history containing the change.
     * @param changeName the name of the change.
     */
    fun openChange(
        auth: AuthenticatedUser,
        ws: Workspace,
        history: String,
        changeName: String
    ) {
        depot.users.validatePermissions(auth, Action.writeProject(ws.project))
        if (ws.modifiedArtifacts.isNotEmpty()) {
            throw PtException(
                PtException.Kind.UserError,
                "A new change can't be opened in a workspace with unsaved changes"
            )
        }
        ws.change = null
        ws.history = history
        val basis = ProjectVersionSpecifier.change(ws.project, history, changeName)
        setBasis(auth, ws, basis)
    }

    /**
     * Populate the workspace from a basis point. Most of the time, this should
     * be a history version.
     * @param auth the authenticated user performing the action.
     * @param ws the workspace
     * @param projectVersionSpecifier
     */
    private fun setBasis(
        auth: AuthenticatedUser,
        ws: Workspace,
        projectVersionSpecifier: ProjectVersionSpecifier
    ) {
        depot.users.validatePermissions(auth, Action.writeProject(ws.project))
        if (ws.modifiedArtifacts.isNotEmpty()) {
            throw PtException(
                PtException.Kind.UserError,
                "Basis can't be changed in a workspace with unsaved changes"
            )
        }
        if (ws.conflicts.isNotEmpty()) {
            throw PtException(
                PtException.Kind.UserError,
                "Basis can't be changed in a workspace with unresolved conflicts"
            )
        }
        val resolved = depot.projects.resolveProjectVersionSpecifier(auth, projectVersionSpecifier)
        ws.basis = projectVersionSpecifier
        ws.baselineVersion = resolved
        updateStoredWorkspace(ws)
    }

    /**
     * Get the current root directory of the workspace.
     * @param auth the authenticated user performing the action.
     * @param ws the workspace
     * @return the directory
     */
    private fun getRootDir(
        auth: AuthenticatedUser,
        ws: Workspace
    ): Directory {
        val baseline = currentBaseline(auth, ws)
        val dirVersion = depot.artifacts.retrieveVersion(
            auth,
            ws.project,
            baseline.rootDir,
            baseline.entries[baseline.rootDir]!!
        )
        return DirectoryAgent.decodeFromString(dirVersion.content)
    }


    private fun getArtifactAtPath(
        auth: AuthenticatedUser,
        ws: Workspace,
        pathParts: List<String>
    ): Id<Artifact> {
        val cbl = currentBaseline(auth, ws)
        val rootDir = cbl.rootDir
        var artId: Id<Artifact> = rootDir
        for (part in pathParts) {
            val art = depot.artifacts.retrieveVersion(
                auth, ws.project,
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
     * @param auth the authenticated user performing the action.
     * @param ws the workspace
     * @param pathParts the directory path, structured as a list of path segments.
     * @return a pair of the directory artifact version, and the decoded directory content.
     */
    private fun getDir(
        auth: AuthenticatedUser,
        ws: Workspace,
        pathParts: List<String>
    ): Pair<ArtifactVersion, Directory> {
        val artId = getArtifactAtPath(auth, ws, pathParts)
        val art = depot.artifacts.retrieveVersion(
            auth, ws.project,
            artId, currentBaseline(auth, ws).get(artId)!!
        )
        if (art.artifactType != DirectoryAgent.artifactType) {
            throw PtException(
                PtException.Kind.TypeError,
                "Artifact at ${pathParts.joinToString("/")} is not a directory"
            )
        }
        return Pair(art, DirectoryAgent.decodeFromString(art.content))
    }

    // *************************************************
    // change operations within a populated workspace
    // *************************************************

    /**
     * Get or create a new working version of an artifact in the workspace.
     * @param auth the authenticated user performing the action.
     * @param ws the workspace
     * @param id the ID of the artifact.
     * @return an ArtifactVersion for the new, modifiable working version of
     *    the artifact.
     */
    private fun getOrCreateWorkingVersion(
        auth: AuthenticatedUser,
        ws: Workspace,
        id: Id<Artifact>
    ): ArtifactVersion {
        if (ws.change == null) {
            throw PtException(
                PtException.Kind.Constraint,
                "Artifacts can only be modified in an open change"
            )
        }

        return if (id !in ws.workingVersions) {
            val working = depot.artifacts.createWorkingVersion(
                auth,
                ws.project, id, if (id == ws.baselineId) {
                    ws.baselineVersion
                } else {
                    currentBaseline(auth, ws).get(id)!!
                }
            )
            ws.workingVersions[id] = working.id
            if (id != ws.baselineId) {
                ws.modifiedArtifacts.add(id)
                val workingBaselineVersion = getOrCreateWorkingVersion(
                    auth, ws,
                    ws.baselineId
                )
                val workingBaseline = BaselineAgent.decodeFromString(workingBaselineVersion.content)
                workingBaseline.change(id, working.id)
                depot.artifacts.updateWorkingVersion(
                    auth, ws.project, ws.baselineId,
                    workingBaselineVersion.id,
                    BaselineAgent.encodeToString(workingBaseline), null
                )
            } else {
                ws.baselineVersion = working.id
                updateStoredWorkspace(ws)
            }
            working
        } else {
            depot.artifacts.retrieveVersion(auth, ws.project, id, ws.workingVersions[id]!!)
        }
    }


    /**
     * Add a new file to the project in the workspace.
     * @param auth the authenticated user performing the action.
     * @param ws the workspace
     * @param path the path of the new object.
     * @param artifactType the type of the new object.
     * @param content the contents of the new object.
     * @return the ID of the newly created artifact.
     */
    fun addFile(
        auth: AuthenticatedUser,
        ws: Workspace,
        path: String,
        artifactType: String,
        content: String
    ): Id<Artifact> {
        depot.users.validatePermissions(auth, Action.writeProject(ws.project))
        if (ws.change == null) {
            throw PtException(
                PtException.Kind.Constraint,
                "Artifacts can only be modified in an open change"
            )
        }
        val pathParts = path.split("/")
        val name = pathParts.last()
        val dirPathParts = pathParts.dropLast(1)

        val (dArt, _) = getDir(auth, ws, dirPathParts)
        val workingDirVer = getOrCreateWorkingVersion(auth, ws, dArt.artifactId)
        val workingDir = DirectoryAgent.decodeFromString(workingDirVer.content)

        val baselineVer = getOrCreateWorkingVersion(auth, ws, ws.baselineId)
        val baseline = BaselineAgent.decodeFromString(baselineVer.content)

        val (newArt, newVer) = depot.artifacts.createArtifact(
            auth = auth,
            project = ws.project,
            artifactType = artifactType,
            initialContent = content,
            metadata = emptyMap()
        )
        ws.modifiedArtifacts.add(newArt.id)


        workingDir.addBinding(name, newArt.id)
        depot.artifacts.updateWorkingVersion(
            auth, ws.project, dArt.artifactId, workingDirVer.id,
            DirectoryAgent.encodeToString(workingDir), null
        )
        baseline.add(newArt.id, newVer.id)
        baseline.change(dArt.artifactId, workingDirVer.id)

        depot.artifacts.updateWorkingVersion(
            auth = auth,
            project = ws.project,
            artifactId = ws.baselineId,
            versionId = baselineVer.id,
            updatedContent = BaselineAgent.encodeToString(baseline),
            updatedMetadata = null
        )
        return newArt.id
    }

    /**
     * Move a file from its current location to a new path.
     * @param auth the authenticated user performing the action.
     * @param ws the workspace
     * @param oldPath the path to the file in the current workspace.
     * @param newPath the path to move the file to.
     */
    fun moveFile(
        auth: AuthenticatedUser,
        ws: Workspace,
        oldPath: String, newPath: String
    ) {
        depot.users.validatePermissions(auth, Action.writeProject(ws.project))
        if (ws.change == null) {
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
            val oldDirId = getArtifactAtPath(auth, ws, oldPathDir)
            val workingDirArt = getOrCreateWorkingVersion(auth, ws, oldDirId)
            val workingDir = DirectoryAgent.decodeFromString(workingDirArt.content)
            val target = workingDir.getBinding(oldName)!!
            workingDir.removeBinding(oldName)
            workingDir.addBinding(newName, target)
            depot.artifacts.updateWorkingVersion(
                auth, ws.project, oldDirId,
                workingDirArt.id, DirectoryAgent.encodeToString(workingDir),
                null
            )
        } else {
            val srcDirId = getArtifactAtPath(auth, ws, oldPathDir)
            val tgtDirId = getArtifactAtPath(auth, ws, newPathDir)
            val workingSrcDirVer = getOrCreateWorkingVersion(auth, ws, srcDirId)
            val workingTgtDirVer = getOrCreateWorkingVersion(auth, ws, tgtDirId)
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
                auth, ws.project, srcDirId, workingSrcDirVer.id,
                DirectoryAgent.encodeToString(updatedSrcDir), null
            )
            depot.artifacts.updateWorkingVersion(
                auth, ws.project, tgtDirId, workingTgtDirVer.id,
                DirectoryAgent.encodeToString(updatedTgtDir), null
            )
        }
    }

    private fun addTransitiveContentsToList(
        auth: AuthenticatedUser,
        ws: Workspace,
        dir: Directory, result: MutableSet<Id<Artifact>>
    ) {
        for (entry in dir.entries) {
            result.add(entry.artifact)
            val art = depot.artifacts.retrieveVersion(
                auth,
                ws.project, entry.artifact,
                currentBaseline(auth, ws).get(entry.artifact)!!
            )
            if (art.artifactType == DirectoryAgent.artifactType) {
                val childDir = DirectoryAgent.decodeFromString(art.content)
                addTransitiveContentsToList(auth, ws, childDir, result)
            }
        }
    }


    private fun splitDirAndName(path: String): Pair<List<String>, String> {
        val parts = path.split("/")
        return Pair(parts.dropLast(1), parts.last())
    }

    /**
     * Delete a file from a project.
     * @param auth the authenticated user performing the action.
     * @param ws the workspace
     * @param path the path to the object to be deleted.
     * @return a list of the artifact IDs of objects removed as a result of
     *   this operation. (If the artifact isn't a directory, this will be just
     *   a singleton list of the ID of the deleted abject; if it was a directory,
     *   then this will be a list of IDs of all artifacts transitively contained
     *   in that directory.)
     */
    fun deleteFile(auth: AuthenticatedUser, ws: Workspace, path: String): Set<String> {
        if (ws.change == null) {
            throw PtException(
                PtException.Kind.Constraint,
                "Artifacts can only be modified in an open change"
            )
        }
        val (dirParts, name) = splitDirAndName(path)
        val deletedId = getArtifactAtPath(auth, ws, path.split("/"))
        val dirId = getArtifactAtPath(auth, ws, dirParts)
        val workingDirVer = getOrCreateWorkingVersion(auth, ws, dirId)
        val dir = DirectoryAgent.decodeFromString(workingDirVer.content)
        dir.removeBinding(name)
        depot.artifacts.updateWorkingVersion(
            auth,
            ws.project, workingDirVer.artifactId, workingDirVer.id,
            DirectoryAgent.encodeToString(dir), emptyMap()
        )
        val deletedVer = depot.artifacts.retrieveVersion(
            auth, ws.project, deletedId,
            currentBaseline(auth, ws).get(deletedId)!!
        )
        val result = mutableSetOf(deletedId)
        if (deletedVer.artifactType == DirectoryAgent.artifactType) {
            val deletedDir = DirectoryAgent.decodeFromString(deletedVer.content)
            addTransitiveContentsToList(auth, ws, deletedDir, result)
        }
        val workingBaselineVer = getOrCreateWorkingVersion(auth, ws, ws.baselineId)
        val workingBaseline = BaselineAgent.decodeFromString(workingBaselineVer.content)
        for (id in result) {
            workingBaseline.remove(id)
        }
        ws.modifiedArtifacts.addAll(result)
        updateStoredWorkspace(ws)
        return result
    }


    /**
     * Modify the file at a path in the workspace.
     * @param auth the authenticated user performing the action.
     * @param ws the workspace
     * @param path the path of the artifact
     * @param newContent the updated content of the artifact, encoded to
     *   a string by the appropriate agent.
     */
    fun modifyFile(
        auth: AuthenticatedUser,
        ws: Workspace,
        path: String, newContent: String
    ) {
        if (ws.change == null) {
            throw PtException(
                PtException.Kind.Constraint,
                "Artifacts can only be modified in an open change"
            )
        }

        val id = getArtifactAtPath(auth, ws, path.split("/"))
        val working = getOrCreateWorkingVersion(auth, ws, id)
        depot.artifacts.updateWorkingVersion(
            auth, ws.project,
            id, working.id, newContent, null
        )
    }

    /**
     * Get the contents of the artifact at a path.
     * @param auth the authenticated user performing the action.
     * @param ws the workspace
     * @param path the path
     * @return the contents of the artifact, in the format used by the appropriate
     *   agent.
     */
    fun getFileContents(auth: AuthenticatedUser, ws: Workspace, path: String): WorkspaceFileContents {

        val id = getArtifactAtPath(auth, ws, path.split("/"))
        val ver = depot.artifacts.retrieveVersion(
            auth, ws.project, id,
            currentBaseline(auth, ws).get(id)!!
        )
        return WorkspaceFileContents(path, ver.artifactType, ver.content)
    }

    /**
     * List all the paths in a workspace.
     * @param auth the authenticated user performing the action.
     * @param ws the workspace
     */
    fun listPaths(
        auth: AuthenticatedUser, ws: Workspace,
        dirOpt: Directory? = null
    ): List<String> {
        depot.users.validatePermissions(auth, Action.readProject(ws.project))
        val result = ArrayList<String>()
        val dir = if (dirOpt == null) {
            val dirArt = depot.artifacts.retrieveVersion(
                auth,
                ws.project,
                currentBaseline(auth, ws).rootDir,
                currentBaseline(auth, ws).get(currentBaseline(auth, ws).rootDir)!!
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
                ws.project, e.artifact, currentBaseline(auth, ws).get(e.artifact)!!
            )
            if (eVer.artifactType == DirectoryAgent.artifactType) {
                val eDir = DirectoryAgent.decodeFromString(eVer.content)
                val paths = listPaths(auth, ws, eDir)
                for (p in paths) {
                    result.add("${e.name}/$p")
                }
            }
        }
        return result
    }

    // *************************************************
    // ** workspace state updates and merges
    // *************************************************

    /**
     * Save the current changes in this workspace as a savepoint.
     * @param auth the authenticated user performing the action.
     * @param ws the workspace
     * @param description the savepoint description
     * @param resolved a list of the merge conflicts that were resolved in the saved changes.
     * @return the new savepoint.
     */
    fun save(
        auth: AuthenticatedUser,
        ws: Workspace,
        description: String,
        resolved: List<Id<MergeConflict>>
    ): SavePoint {
        if (ws.change == null) {
            throw PtException(
                PtException.Kind.Constraint,
                "Artifacts can only be modified in an open change"
            )
        }
        for ((artId, verId) in ws.workingVersions) {
            depot.artifacts.commitWorkingVersion(auth, ws.project, artId, verId)
        }
        val sp = depot.changes.createSavePoint(
            auth, ws.project,
            ws.history,
            ws.change!!,
            ws.modifiedArtifacts.toList(),
            description,
            ws.basis,
            ws.baselineVersion
        )
        ws.basis = ProjectVersionSpecifier.savePoint(ws.project, ws.history, sp.id)
        ws.modifiedArtifacts.clear()
        ws.workingVersions.clear()
        ws.conflicts.removeAll { it.id in resolved }
        updateStoredWorkspace(ws)
        return sp
    }


    /**
     * Deliver the current change to its history.
     * @param auth the authenticated user performing the action.
     * @param ws the workspace
     * @param description a description of the change.
     */
    fun deliver(
        auth: AuthenticatedUser,
        ws: Workspace,
        description: String
    ) {
        depot.users.validatePermissions(auth, Action.writeProject(ws.project))
        if (ws.modifiedArtifacts.isNotEmpty()) {
            throw PtException(
                PtException.Kind.UserError,
                "Workspace must be clean before calling deliver, but you have unsaved changes"
            )
        }
        if (ws.conflicts.isNotEmpty()) {
            throw PtException(
                PtException.Kind.UserError,
                "Workspace must be clean before calling deliver, but you have unresolved conflicts"
            )
        }
        if (ws.change == null) {
            throw PtException(
                PtException.Kind.UserError,
                "No change in progress to deliver"
            )
        }
        if (!upToDate(auth, ws)) {
            throw PtException(
                PtException.Kind.UserError,
                "History ${ws.history} has steps that haven't been merged into your workspace; " +
                        "run update before delivering"
            )

        }

        depot.changes.updateChangeStatus(
            auth, ws.project, ws.history, ws.change!!,
            ChangeStatus.Closed
        )
        val step = depot.histories.addHistoryStep(
            auth, ws.project, ws.history,
            ws.change!!,
            getBaselineVersion(auth, ws, ws.baselineVersion),
            description
        )
        ws.change = null
        ws.basis = ProjectVersionSpecifier.history(step.project, step.historyName, step.number)
        updateStoredWorkspace(ws)
    }

    fun upToDate(
        auth: AuthenticatedUser,
        ws: Workspace
    ): Boolean {
        val topOfHistory = depot.histories.retrieveHistoryStep(auth, ws.project, ws.history)
        return depot.artifacts.versionIsAncestor(
            auth, ws.project,
            ws.baselineId, topOfHistory.baselineVersionId,
            ws.baselineVersion
        )

    }

    /**
     * Get up-to-date with the parent history.
     * @param auth the authenticated user performing the action.
     * @param ws the workspace
     *
     */
    fun update(
        auth: AuthenticatedUser,
        ws: Workspace
    ): List<MergeConflict> {
        depot.users.validatePermissions(auth, Action.writeProject(ws.project))
        if (ws.modifiedArtifacts.isNotEmpty()) {
            throw PtException(
                PtException.Kind.Constraint,
                "Workspace must be clean before calling update, but you have unsaved changes"
            )
        }
        if (ws.conflicts.isNotEmpty()) {
            throw PtException(
                PtException.Kind.Constraint,
                "Workspace must be clean before calling update, but you have unresolved conflicts"
            )
        }
        val latest = depot.histories.retrieveHistoryStep(auth, ws.project, ws.history)
        if (depot.artifacts.versionIsAncestor(
                auth, ws.project,
                ws.baselineId,
                latest.baselineVersionId,
                ws.baselineVersion
            )
        ) {
            // No merge necessary.
            return emptyList()
        }
        val latestBaselineVer =
            depot.artifacts.retrieveVersion(auth, ws.project, latest.baselineId, latest.baselineVersionId)
        val wsBaselineVer = depot.artifacts.retrieveVersion(auth, ws.project, ws.baselineId, ws.baselineVersion)
        val ancestorId = depot.artifacts.nearestCommonAncestor(
            auth, ws.project, ws.baselineId,
            latestBaselineVer.id, ws.baselineId
        )
        val ancestor = depot.artifacts.retrieveVersion(auth, ws.project, latest.baselineId, ancestorId)
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
                    val artMerge = mergeSingleArtifact(
                        auth, ws, baselineConflict.artifactId,
                        baselineConflict.mergeSourceVersion!!, baselineConflict.mergeTargetVersion!!
                    )
                    // Create a working version of the artifact, and store its proposed
                    // merge there; then add its conflicts to the workspace conflicts list.
                    val working = getOrCreateWorkingVersion(auth, ws, baselineConflict.artifactId)
                    depot.artifacts.updateWorkingVersion(
                        auth, ws.project, baselineConflict.artifactId,
                        working.id, artMerge.proposedMerge,
                        updatedParents = listOf(
                            baselineConflict.mergeSourceVersion!!,
                            baselineConflict.mergeTargetVersion!!
                        )
                    )
                    if (artMerge.conflicts.isNotEmpty()) {
                        conflicts.addAll(artMerge.conflicts)
                    }
                }
            }
        }
        return baselineMergeResult.conflicts
    }


    private fun mergeSingleArtifact(
        auth: AuthenticatedUser,
        ws: Workspace,
        id: Id<Artifact>, sourceId: Id<ArtifactVersion>, targetId: Id<ArtifactVersion>
    ): MergeResult {
        val ancestorId = depot.artifacts.nearestCommonAncestor(
            auth, ws.project,
            id, sourceId, targetId
        )
        val anc = depot.artifacts.retrieveVersion(auth, ws.project, id, ancestorId)
        val src = depot.artifacts.retrieveVersion(auth, ws.project, id, sourceId)
        val tgt = depot.artifacts.retrieveVersion(auth, ws.project, id, targetId)
        val agent = depot.agentFor(anc.artifactType)
        return agent.merge(anc, src, tgt)
    }

    /**
     * Integrate changes from some other location into a workspace. Useful
     * for cherry-picking.
     * @param auth the authenticated user performing the action.
     * @param ws the workspace
     * @param from the start point of the changes to integrate to this workspace.
     * @param to the target point of the changes to integrate to this workspace.
     */
    fun integrate(
        auth: AuthenticatedUser,
        ws: Workspace,
        from: ProjectVersionSpecifier,
        to: ProjectVersionSpecifier
    ): List<MergeConflict> {
        depot.users.validatePermissions(auth, Action.writeProject(ws.project))
        if (ws.modifiedArtifacts.isNotEmpty()) {
            throw PtException(
                PtException.Kind.UserError,
                "Workspace must be clean before calling integrate, but you have unsaved changes"
            )
        }
        if (ws.conflicts.isNotEmpty()) {
            throw PtException(
                PtException.Kind.UserError,
                "Workspace must be clean before calling integrate, but you have unresolved conflicts"
            )
        }

        System.err.println("Integrate from $from to $to")
        TODO()
    }

    fun deleteWorkspace(auth: AuthenticatedUser,
                        ws: Workspace) {
        depot.users.validatePermissions(auth, Action.writeProject(ws.project))
        TODO()
    }

    fun abandonChanges(auth: AuthenticatedUser,
                       ws: Workspace,
                       reason: String) {
        depot.users.validatePermissions(auth, Action.writeProject(ws.project))
        System.err.println("Abandon change to $ws because $reason")
        TODO()
    }

}
