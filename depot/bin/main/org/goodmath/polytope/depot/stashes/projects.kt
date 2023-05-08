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
import org.goodmath.polytope.PtException
import org.goodmath.polytope.depot.Stash
import org.goodmath.polytope.depot.Config
import org.goodmath.polytope.depot.Depot
import org.goodmath.polytope.depot.agents.Baseline
import org.goodmath.polytope.depot.agents.BaselineAgent
import org.goodmath.polytope.depot.agents.Directory
import org.goodmath.polytope.depot.agents.DirectoryAgent
import org.goodmath.polytope.depot.util.*
import java.time.Instant

data class Project(
    val name: String,
    val creator: String,
    val timestamp: Long,
    val description: String,
    val rootDir: Id<Artifact>,
    val baseline: Id<Artifact>,
    val histories: List<Id<History>>
)

class ProjectStash(
    private val db: RocksDB,
    private val projectsColumn: ColumnFamilyHandle,
    private val depot: Depot
): Stash {

    override fun initStorage(config: Config) {
    }


    /**
     * Compute the versionID of the project baseline associated with a project version
     * specifier.
     * @param auth the authenticated user performing the operation
     * @param pvs the project version specifier.
     * @return the version identifier of the baseline version.
     */
    fun resolveProjectVersionSpecifier(
        auth: AuthenticatedUser,
        pvs: ProjectVersionSpecifier
    ): Id<ArtifactVersion> {
        return when (pvs.kind) {
            ProjectVersionSpecifierKind.Baseline -> pvs.baselineId!!
            ProjectVersionSpecifierKind.History -> {
                val hStep = depot.histories.retrieveHistoryStep(auth, pvs.project,
                    pvs.history, pvs.number)
                hStep.baselineVersionId
            }
            ProjectVersionSpecifierKind.SavePoint -> {
                val sp = depot.changes.retrieveSavePoint(auth, pvs.project, pvs.history, pvs.changeName!!, pvs.savePointId!!)
                sp.baselineVersion
            }
            ProjectVersionSpecifierKind.Change -> {
                val ch = depot.changes.retrieveChangeByName(auth, pvs.project, pvs.history, pvs.changeName!!)
                val latest = depot.changes.retrieveSavePoint(auth, pvs.project, pvs.history, pvs.changeName, ch.savePoints.last())
                latest.baselineVersion
            }
        }
    }

    /**
     * Create a new project.
     * @param auth the authenticated user performing the operation.
     * @param projectName the name of the new project.
     * @param description a description of the new project.
     * @return the new project.xs
     */
    fun createProject(
        auth: AuthenticatedUser,
        projectName: String,
        description: String
    ): Project {
        depot.users.validatePermissions(auth, Action.createProject)
        if (projectExists(auth, projectName)) {
            throw  PtException(PtException.Kind.Conflict,
            "A project with name $projectName already exists"
            )
        }

        val (baseline, rootDir, initialHistory) = createInitialContents(auth, projectName)
        val project = Project(
            name = projectName,
            creator = auth.userId,
            timestamp =  Instant.now().toEpochMilli(),
            description = description,
            rootDir = rootDir.id,
            baseline = baseline.id,
            histories = listOf(initialHistory.id))

        db.putTyped(projectsColumn, projectName, project)
        return project
    }

    private data class ProjectContents(val baseline: Artifact,
        val rootDir: Artifact,
        val history: History)

    /**
     * Create the initial contents of a new project.
     */
    private fun createInitialContents(
       auth: AuthenticatedUser,
       projectName: String,
    ): ProjectContents {
        // Initial contents is a baseline and an empty directory,
        // plus a single history.  Maybe later set
        // up templates?
        val dir =  Directory(ArrayList())
        val (dirArt, dirVersion) = depot.artifacts.createArtifact(
            auth,
            projectName,
            "directory",
            DirectoryAgent.encodeToString(dir),
            emptyMap())

        val baseline = Baseline(dirArt.id, mutableMapOf(Pair(dirArt.id, dirVersion.id)))
        val (baselineArt, baselineVersion) = depot.artifacts.createArtifact(
            auth,
            projectName,
            "baseline",
            BaselineAgent.encodeToString(baseline),
            emptyMap()
        )

        val history = depot.histories.createInitialHistory(
            auth,
            projectName,
            baselineVersion
        )
        return ProjectContents(baselineArt, dirArt, history)
    }

    fun retrieveProject(
        auth: AuthenticatedUser,
        name: String
    ): Project {
        depot.users.validatePermissions(auth, Action.readProject(name))
        return db.getTyped<Project>(projectsColumn, name) ?: throw PtException(
            PtException.Kind.NotFound,
            "Project '$name' not found"
        )
    }

    fun updateProject(
        auth: AuthenticatedUser,
        project: Project
    ) {
        depot.users.validatePermissions(auth, Action.writeProject(project.name))
        db.updateTyped(projectsColumn, project.name, project)
    }

    fun listProjects(auth: AuthenticatedUser): List<Project> {
        depot.users.validatePermissions(auth, Action.readDepot)
        return db.listAllInColumn(projectsColumn)
    }

    private fun projectExists(
        auth: AuthenticatedUser,
        projectName: String
    ): Boolean {
        depot.users.validatePermissions(auth, Action.readDepot)
        val p = db.getTyped<Project>(projectsColumn, projectName)
        return p != null
    }

}
