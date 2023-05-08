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

import maryk.rocksdb.ColumnFamilyHandle
import maryk.rocksdb.RocksDB
import org.goodmath.polytope.Config
import org.goodmath.polytope.common.*
import org.goodmath.polytope.common.stashable.*
import org.goodmath.polytope.depot.Depot
import org.goodmath.polytope.depot.util.*
import java.time.Instant


class ChangeStash(
    private val db: RocksDB,
    private val changesColumn: ColumnFamilyHandle,
    private val savePointsColumn: ColumnFamilyHandle,
    private val depot: Depot
): Stash {

    /**
     * Retrieve a change.
     * @param auth the user performing the operation.
     * @param project the project containing the change.
     * @param id the change ID.
     * @return the change
     */
    private fun retrieveChange(auth: AuthenticatedUser, project: String, id: Id<Change>): Change {
        depot.users.validatePermissions(auth, Action.readProject(project))
        val c = db.getTyped<Change>(changesColumn, id) ?: throw PtException(
            PtException.Kind.NotFound,
            "Change $id not found"
        )
        if (c.project != project) {
            throw PtException(
                PtException.Kind.InvalidParameter,
                "Change $id doesn't belong to project $project"
            )
        }
        return c
    }

    /**
     * Get the ID for a named change.
     * @param project the project containing the change.
     * @param history the name of the history containing the change.
     * @param changeName the name of the change.
     * @return the change ID.
     * @throws PtException if no change with the name is found.
     */
    private fun getChangeId(project: String, history: String, changeName: String): Id<Change> {
        val idx = getCollectionIndex()
        return idx.entries[ChangeIndex.ChangeIndexKey(project, history, changeName)] ?: throw PtException(
            PtException.Kind.NotFound,
            "Change named $changeName not found in project $project"
        )
    }

    /**
     * Retrieve a change by name.
     * @param auth the user performing the operation.
     * @param project the project containing the change.
     * @param history the name of the history containing the change
     * @param changeName the name of the change.
     * @returns the change
     * @throws PtException if no change with the name is found.
     */
    fun retrieveChangeByName(auth: AuthenticatedUser, project: String, history: String, changeName: String): Change {
        depot.users.validatePermissions(auth, Action.writeProject(project))
        val changeId = getChangeId(project, history, changeName)
        return retrieveChange(auth, project, changeId)
    }

    /**
     * Create a new change
     * @param auth the user performing the operation.
     * @param projectName the project containing the change.
     * @param history the name of the history that will contain the change.
     * @param changeName the name of the new change.
     * @param basis the project version specifier of the baseline of the change.
     * @param description a description of the change.
     * @return the new change.
     */
    fun createChange(
        auth: AuthenticatedUser,
        projectName: String,
        history: String,
        changeName: String,
        basis: ProjectVersionSpecifier,
        description: String
    ): Change {
        depot.users.validatePermissions(auth, Action.writeProject(projectName))
        val project = depot.projects.retrieveProject(auth, projectName)
        val change = Change(
            id = newId<Change>(ID_CHANGE),
            project = projectName,
            name = changeName,
            history = history,
            baseline = project.baseline,
            basis = basis,
            description = description,
            savePoints = emptyList(),
            status = ChangeStatus.Open,
            timestamp = Instant.now().toEpochMilli()
        )
        db.putTyped(changesColumn, change.id, change)
        updateCollectionIndex(projectName, history, changeName, change.id)
        return change
    }

    /**
     * Update the status of an in-progress change.
     * @param auth the user performing the operation.
     * @param project the project containing the change.
     * @param history the history containing the change.
     * @param changeName the name of the change.
     * @param status the new status of the change.
     */
    fun updateChangeStatus(
        auth: AuthenticatedUser,
        project: String,
        history: String,
        changeName: String,
        status: ChangeStatus
    ) {
        depot.users.validatePermissions(auth, Action.writeProject(project))
        val changeId = getChangeId(project, history, changeName)
        val change = retrieveChange(auth, project, changeId)
        val updated = change.copy(timestamp = Instant.now().toEpochMilli(),
            status = status)
        db.updateTyped(changesColumn, changeId, updated)
    }

    /**
     * Get the status of a change.
     * @param auth the user performing the operation.
     * @param project the project containing the change.
     * @param history the history containing the change.
     * @param changeName the name of the change.
     * @return the change status of the change.
     */
    fun getChangeStatus(
        auth: AuthenticatedUser,
        project: String, history: String, changeName: String
    ): ChangeStatus {
        depot.users.validatePermissions(auth, Action.readProject(project))
        val change = retrieveChangeByName(auth, project, history, changeName)
        return change.status
    }

    /**
     * Create a save point in an open change.
     * @param auth the user performing the operation.
     * @param project the project containing the change.
     * @param history the name of the history containing the change.
     * @param changeName the name of the change.
     * @param changedArtifacts a list of the artifacts that were modified since the last
     *    baseline.
     * @param description a description of the savepoint.
     * @param basis a PVS for the starting point of the change.
     * @param baselineVersion the version ID of the new baseline.
     * @return a new savepoint.
     */
    fun createSavePoint(
        auth: AuthenticatedUser,
        project: String,
        history: String,
        changeName: String,
        changedArtifacts: List<Id<Artifact>>,
        description: String,
        basis: ProjectVersionSpecifier,
        baselineVersion: Id<ArtifactVersion>
    ): SavePoint {
        depot.users.validatePermissions(auth, Action.writeProject(project))
        val ch = retrieveChangeByName(auth, project, history, changeName)
        val savePoint = SavePoint(
            id = newId<SavePoint>("cs"),
            changeId = ch.id,
            modifiedArtifacts = changedArtifacts,
            basis = basis,
            baselineVersion = baselineVersion,
            creator = auth.userId,
            description = description,
            timestamp = Instant.now().toEpochMilli()
        )
        val updatedChange = ch.copy(savePoints = ch.savePoints + savePoint.id)
        db.putTyped(savePointsColumn, savePoint.id, savePoint)
        db.updateTyped(changesColumn, updatedChange.id, updatedChange)
        return savePoint
    }

    /**
     * Retrieve a savepoint
     * @param auth the user performing the operation.
     * @param project the project containing the change.
     * @param history the name of the history containing the change.
     * @param changeName the name of the change.
     * @param
     *
     */
    fun retrieveSavePoint(
        auth: AuthenticatedUser,
        project: String,
        history: String,
        changeName: String,
        saveId: Id<SavePoint>
    ): SavePoint {
        depot.users.validatePermissions(auth, Action.readProject(project))
        val ch = retrieveChangeByName(auth, project, history, changeName)
        return if (ch.savePoints.contains(saveId)) {
            db.getTyped<SavePoint>(savePointsColumn, saveId)
                ?: throw PtException(
                    PtException.Kind.NotFound,
                    "Save point not found"
                )
        } else {
            throw PtException(
                PtException.Kind.NotFound,
                "Save point not found"
            )
        }
    }

    /**
     * List the save points in a change.
     * @param auth the user performing the operation.
     * @param project the project containing the change.
     * @param history the name of the history containing the change.
     * @param change the name of the change.
     * @return a list of save point IDs.
     */
    fun listSavePoints(
        auth: AuthenticatedUser,
        project: String,
        history: String,
        change: String
    ): List<SavePoint> {
        depot.users.validatePermissions(auth, Action.readProject(project))
        val ch = retrieveChangeByName(auth, project, history, change)
        return ch.savePoints.map { retrieveSavePoint(auth, project, history, change, it) }
    }

    /**
     * List the changes in a history.
     * @param auth the user performing the operation.
     * @param project the project containing the change.
     * @param historyName the name of the history.
     * @return a list of changes.
     */
    fun listChanges(
        auth: AuthenticatedUser,
        project: String,
        historyName: String
    ): List<Change> {
        depot.users.validatePermissions(auth, Action.readProject(project))
        val index = getCollectionIndex()
        return index.entries.entries.filter { it.key.project == project && it.key.history == historyName }
            .map { entry -> retrieveChange(auth, project, entry.value)}

    }

    override fun initStorage(config: Config) {
        val cIdx = ChangeIndex(mutableMapOf())
        db.putTyped(changesColumn, indexKey, cIdx)
    }

    /**
     * An index of changes, which lets us find and list changes by name without having
     * to scan all changes in the DB.
     */
    data class ChangeIndex(
        val entries: MutableMap<ChangeIndexKey, Id<Change>>
    ) {
        data class ChangeIndexKey(val project: String, val history: String, val changeName: String)

        fun get(projectName: String, historyName: String, changeName: String): Id<History>? {
            return entries[ChangeIndexKey(projectName, historyName, changeName)]
        }

        fun add(projectName: String, historyName: String, changeName: String, id: Id<Change>) {
            entries[ChangeIndexKey(projectName, historyName, changeName)] = id
        }
    }

    private val indexKey = "__change__index__"
    private var changeIndex: ChangeIndex? = null
    private fun getCollectionIndex(): ChangeIndex {
        if (changeIndex == null) {
            changeIndex = db.getTyped(changesColumn, indexKey)
        }
        return changeIndex!!
    }

    private fun updateCollectionIndex(project: String, history: String, changeName: String, id: Id<History>) {
        val idx = getCollectionIndex()
        idx.add(project, history, changeName, id)
        db.updateTyped(changesColumn, indexKey, idx)
    }



}
