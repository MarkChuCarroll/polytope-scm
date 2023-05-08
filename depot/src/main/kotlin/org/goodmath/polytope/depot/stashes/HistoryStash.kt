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
import java.time.Instant
import org.goodmath.polytope.depot.Depot
import org.goodmath.polytope.depot.util.*


class HistoryStash(
    private val db: RocksDB,
    private val historiesColumn: ColumnFamilyHandle,
    private val historyVersionsColumn: ColumnFamilyHandle,
    private val depot: Depot
): Stash {
    /**
     * Retrieve a history.
     * @param auth the authenticated user performing the operation.
     * @param project the name of the project containing the history.
     * @param history the name of the history
     * @return the history
     */
    fun retrieveHistory(
        auth: AuthenticatedUser,
        project: String, history: String
    ): History {
        depot.users.validatePermissions(auth, Action.readProject(project))
        return getCollectionIndex().entries[Pair(project, history)]?.let {
            db.getTyped<History>(historiesColumn, it)
        } ?: throw PtException(PtException.Kind.NotFound,
            "History $history not found in project $project"
        )
    }

    /**
     * Retrieve a specific step of a history.
     * @param auth the authenticated user performing the operation.
     * @param project the name of the project containing the history.
     * @param historyName the name of the history.
     * @param number the step number to retrieve. If null, then this will
     *   retrieve the most recent step.
     * @return the history step.
     *
     */
    fun retrieveHistoryStep(
        auth: AuthenticatedUser,
        project: String,
        historyName: String,
        number: Int? = null
    ): HistoryStep {
        val history = retrieveHistory(auth, project, historyName)
        val step = number ?: (history.steps.size - 1)

        if (step <= history.steps.size) {
            return db.getTyped<HistoryStep>(historyVersionsColumn, history.steps[step]) ?:
            throw PtException(PtException.Kind.NotFound,
                "History step ${history.steps[step]} not found")
        } else {
            throw PtException(PtException.Kind.NotFound,
                "History step $historyName@$number not found")
        }
    }

    /**
     * Retrieve the step number of the most recent step in a history.
     * @param auth the authenticated user performing the operation.
     * @param project the name of the project containing the history.
     * @param history the name of the history.
     * @return the step index
     */
    fun currentStep(
        auth: AuthenticatedUser,
        project: String, history: String
    ): Int {
        return retrieveHistory(auth, project, history).steps.size - 1
    }

    /**
     * Retrieve a list of the steps in a history.
     * @param auth the authenticated user performing the operation.
     * @param project the name of the project containing the history.
     * @param history the name of the history.
     * @param limit the maximum number of steps to retrieve. If null,
     *    then all steps in the history will be retrieved.
     * @return a list of history steps
     */
    fun listHistorySteps(
        auth: AuthenticatedUser,
        project: String, history: String, limit: Int? = null
    ): List<HistoryStep> {
        val hist = retrieveHistory(auth, project, history)
        val versions = if (limit != null) {
            hist.steps.takeLast(limit)
        } else {
            hist.steps
        }
        return versions.map {
            db.getTyped<HistoryStep>(historyVersionsColumn, it) ?: throw PtException(
                PtException.Kind.NotFound,
                "History step with id $it not found"
            )
        }
    }

    /**
     * Create a new history
     * @param auth the authenticated user performing the operation.
     * @param project the name of the project containing the history.
     * @param name the name of the new history to create.
     * @param description a description of the new history.
     * @param fromHistory the name of the history that the new history branches from.
     * @param atStep the step number of the history to use as a starting point. If null,
     *    then this will use the most recent step.
     *@return the new history
     */
    fun createHistory(
        auth: AuthenticatedUser,
        project: String, name: String, description: String,
        fromHistory: String, atStep: Int? = null
    ): History {
        depot.users.validatePermissions(auth, Action.writeProject(project))
        if (historyExistsInProject(project, name)) {
            throw PtException(PtException.Kind.Conflict,
                "Project $project already has a history named $name")
        }
        val baseStep = retrieveHistoryStep(auth, project, fromHistory, atStep)
        val historyId = newId<History>(ID_HISTORY)
        val historyFirstStep = baseStep.copy(id = newId<HistoryStep>(
            ID_HISTORY_STEP
        ),
            historyName = name, number = 0,
            description = "branch into new history")
        val history = History(
            id=historyId,
            project=project,
            name=name,
            description,
            Instant.now().toEpochMilli(),
            ProjectVersionSpecifier.history(project, fromHistory, atStep),
            steps = mutableListOf(historyFirstStep.id))

        db.putTyped(historiesColumn, historyId, history)
        db.putTyped(historyVersionsColumn, historyFirstStep.id, historyFirstStep)
        updateCollectionIndex(project, name, historyId)
        return history
    }

    /**
     * Check if a history exists in a project
     * @param project the name of the project containing the history.
     * @param name the name of the history.
     * @returns true if a history with the name already exists in the project.
     *
      */
    private fun historyExistsInProject(project: String, name: String): Boolean {
        val historyIndex = getCollectionIndex()
        return historyIndex.entries.containsKey(Pair(project, name))
    }

    /**
     * Add a new step to a history.
     * @param auth the authenticated user performing the operation.
     * @param project the name of the project containing the history.
     * @param history the name of the history.
     * @param change the ID of the change being added to the history.
     * @param baselineVersion the final baseline of the change being added to the history.
     * @param description a description of the change.
     */
    fun addHistoryStep(
        auth: AuthenticatedUser,
        project: String,
        history: String,
        change: Id<Change>,
        baselineVersion: ArtifactVersion,
        description: String
    ): HistoryStep {
        depot.users.validatePermissions(auth, Action.writeProject(project))
        val hist = retrieveHistory(auth, project, history)
        val newStep = HistoryStep(
            id = newId<HistoryStep>(ID_HISTORY_STEP),
            project = project,
            historyName = history,
            number = hist.steps.size,
            baselineId = baselineVersion.artifactId,
            baselineVersionId = baselineVersion.id,
            change = change,
            description = description
        )
        hist.steps.add(newStep.id)
        db.putTyped(historyVersionsColumn, newStep.id, newStep)
        db.updateTyped(historiesColumn, hist.id, hist)
        return newStep
    }


    /**
     * List the histories of a project
     * @param auth the authenticated user performing the operation.
     * @param project the name of the project containing the history.
     * @return a list of histories
     */
    fun listHistories(
        auth: AuthenticatedUser,
        project: String
    ): List<History> {
        val historyIndex = getCollectionIndex()
        return historyIndex.entries.keys.filter { it.first == project }
            .map { it.second }
            .map { retrieveHistory(auth, project, it)}
    }

    private val initialHistoryName = "main"

    /**
     * Create the initial history for a new project.
     * @param auth the authenticated user performing the operation.
     * @param project the name of the project containing the history.
     * @param baselineVersion the initial baseline of the history.
     * @return the new history.
     */
    fun createInitialHistory(
        auth: AuthenticatedUser,
        project: String,
        baselineVersion: ArtifactVersion,
    ): History {
        depot.users.validatePermissions(auth, Action.writeProject(project))
        val historyId = newId<History>(ID_HISTORY)
        val history = History(historyId,
            project,
            initialHistoryName,
            "initial project history",
            Instant.now().toEpochMilli(),
            ProjectVersionSpecifier.baseline(project, initialHistoryName, baselineVersion.id),
            steps = mutableListOf())


        val step = HistoryStep(
            id = newId<HistoryStep>(ID_HISTORY_STEP),
            project = project,
            historyName = initialHistoryName,
            number = 0,
            baselineId = baselineVersion.artifactId,
            baselineVersionId = baselineVersion.id,
            change = null,
            description = "initial project version")

        history.steps.add(step.id)

        db.putTyped(historiesColumn, historyId, history)
        db.putTyped(historyVersionsColumn, step.id, step)
        updateCollectionIndex(project, initialHistoryName, historyId)
        return history
    }

    override fun initStorage(config: Config) {
        val hIndex = HistoryIndex(mutableMapOf())
        db.putTyped(historiesColumn, indexKey, hIndex)
    }

    data class HistoryIndex(
        val entries: MutableMap<Pair<String, String>, Id<History>>
    ) {
        fun get(projectName: String, historyName: String): Id<History>? {
            return entries[Pair(projectName, historyName)]
        }

        fun add(projectName: String, historyName: String, id: Id<History>) {
            entries[Pair(projectName, historyName)] = id
        }
    }


    private val indexKey = "__history__index__"
    private var historyIndex: HistoryIndex? = null
    private fun getCollectionIndex(): HistoryIndex {
        if (historyIndex == null) {
            historyIndex = db.getTyped(historiesColumn, indexKey)
        }
        return historyIndex!!
    }

    private fun updateCollectionIndex(project: String, history: String, id: Id<History>) {
        val idx = getCollectionIndex()
        idx.add(project, history, id)
        db.updateTyped(historiesColumn, indexKey, idx)
    }


}
