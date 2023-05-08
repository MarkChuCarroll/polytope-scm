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

/**
 * The stash that manages storage of artifacts and versions for the depot.
 * @param db the DB where the artifacts will be stored.
 * @param artifactsColumn the column in the DB where the artifacts will be stored.
 * @param versionsColumn the column in the DB where the versions will be stored.
 * @param depot the depot.
 */
class ArtifactStash(
    private val db: RocksDB,
    private val artifactsColumn: ColumnFamilyHandle,
    private val versionsColumn: ColumnFamilyHandle,
    private val depot: Depot
): Stash {

    /**
     * Retrieve an artifact from the database
     * @param auth the authenticated user performing the operation
     * @param project the project containing the artifact.
     * @param id the artifact ID.
     * @return the full Artifact record.
     * @throws PtException if the artifact isn't found, if the user
     *    doesn't have permission to read it, or if there's some internal
     *    error retrieving it.
     */
    fun retrieveArtifact(
        auth: AuthenticatedUser,
        project: String, id: Id<Artifact>
    ): Artifact {
        depot.users.validatePermissions(auth, Action.readProject(project))
        return db.getTyped<Artifact>(artifactsColumn, id)
            ?: throw PtException(PtException.Kind.NotFound, "Artifact $id not found")
    }

    /**
     * Retrieve a version of an artifact from the database.
     * @param auth the authenticated user performing the operation.
     * @param project the project containing the artifact.
     * @param artifactId the artifact ID
     * @param versionId the version ID.
     * @return the full ArtifactVersion record.
     * @throws PtException if the version isn't found, if the user
     *    doesn't have permission to read it, or if there's some internal
     *    error retrieving it.
     */
    fun retrieveVersion(
        auth: AuthenticatedUser,
        project: String,
        artifactId: Id<Artifact>,
        versionId: Id<ArtifactVersion>
    ): ArtifactVersion {
        depot.users.validatePermissions(auth, Action.readProject(project))
        val ver = db.getTyped<ArtifactVersion>(versionsColumn, versionId)
            ?: throw PtException(PtException.Kind.NotFound, "Artifact Version $versionId not found")
        if (ver.artifactId != artifactId) {
            throw PtException(PtException.Kind.NotFound, "Artifact Version $versionId not found")
        } else {
            return ver
        }
    }

    /**
     * Store a new artifact from the database.
     * @param auth the authenticated user performing the operation
     * @param project the project containing the artifact.
     * @param artifactType the type of the new artifact.
     * @param initialContent the content of the new artifact.
     * @param metadata metadata to apply to the new artifact.
     * @throws PtException if the artifact already exists, if the user
     *    doesn't have permission to write it, or if there's some internal
     *    error storing it.
     */
    fun createArtifact(
        auth: AuthenticatedUser,
        project: String,
        artifactType: String,
        initialContent: String,
        metadata: Map<String, String>
    ): Pair<Artifact, ArtifactVersion> {
        depot.users.validatePermissions(auth, Action.writeProject(project))
        val artId = newId<Artifact>("$ID_ARTIFACT($artifactType)")
        val now = Instant.now().toEpochMilli()
        val initialVersion = ArtifactVersion(
            id = newId<ArtifactVersion>(ID_VERSION),
            artId,
            artifactType,
            now,
            auth.userId,
            initialContent,
            emptyList(),
            metadata,
            VersionStatus.Committed)

        val artifact = Artifact(
            id = artId,
            artifactType = artifactType,
            now,
            auth.userId,
            project,
            metadata,
            listOf(initialVersion.id)
        )
        db.putTyped(artifactsColumn, artifact.id, artifact)
        db.putTyped(versionsColumn, initialVersion.id, initialVersion)

        return Pair(artifact, initialVersion)
    }

    /**
     * Create a new version af an artifact
     * @param auth the authenticated user performing the operation
     * @param project the project containing the artifact.
     * @param artifactId the ID of the artifact
     * @param content the content of the new artifact version.
     * @param parents a list of versions that will be parents of the new version.
     * @param metadata a map of metadata for the new version.
     * @return the new version.
     */
    fun createVersion(
        auth: AuthenticatedUser,
        project: String,
        artifactId: Id<Artifact>,
        artifactType: String,
        content: String,
        parents: List<Id<ArtifactVersion>>,
        metadata: Map<String, String>
    ): ArtifactVersion {
        depot.users.validatePermissions(auth, Action.writeProject(project))
        val ver = ArtifactVersion(
            id = newId<ArtifactVersion>("ver"),
            artifactId = artifactId,
            creator = auth.userId,
            content = content,
            timestamp = Instant.now().toEpochMilli(),
            parents = parents,
            metadata = metadata,
            artifactType = artifactType,
            status = VersionStatus.Committed
        )
        db.putTyped(versionsColumn, ver.id, ver)
        return ver
    }


    /**
     * Create a working version of an artifact for an in-progress change.
     * @param auth the authenticated user performing the operation
     * @param project the project containing the artifact
     * @param artifactId the ID of the artifact.
     * @param baseVersion the ID of the version that's going to be edited into a new version.
     * @return a new working version.
     */
    fun createWorkingVersion(
        auth: AuthenticatedUser,
        project: String,
        artifactId: Id<Artifact>,
        baseVersion: Id<ArtifactVersion>
    ): ArtifactVersion {
        depot.users.validatePermissions(auth, Action.writeProject(project))
        val base = retrieveVersion(auth, project, artifactId, baseVersion)
        val working = ArtifactVersion(
            id = newId<ArtifactVersion>(ID_VERSION),
            artifactId = base.artifactId,
            artifactType = base.artifactType,
            content = base.content,
            creator = auth.userId,
            timestamp = Instant.now().toEpochMilli(),
            metadata = base.metadata,
            parents = listOf(base.id),
            status = VersionStatus.Working
        )
        db.putTyped(versionsColumn, working.id, working)
        return working
    }

    /**
     * Update a working version
     * @param auth the authenticated user performing the operation
     * @param project the project containing the artifact
     * @param artifactId the artifact ID
     * @param versionId the version ID
     * @param updatedContent the updated content of the artifact, or null if the content is
     *   unchanged.
     * @param updatedMetadata the updated metadata of the artifact, or null if the metadata
     *   is unchanged
     * @return the updated version.
     */
    fun updateWorkingVersion(
        auth: AuthenticatedUser,
        project: String,
        artifactId: Id<Artifact>,
        versionId: Id<ArtifactVersion>,
        updatedContent: String?,
        updatedMetadata: Map<String, String>? = null,
        updatedParents: List<Id<ArtifactVersion>>? = null
    ): ArtifactVersion {
        depot.users.validatePermissions(auth, Action.writeProject(project))
        if (updatedContent == null && updatedMetadata == null) {
            throw PtException(PtException.Kind.InvalidParameter,
                "Update must updated something")
        }
        val old = try {
            retrieveVersion(
                auth, project, artifactId,
                versionId
            )
        } catch (e: PtException) {
            if (e.kind == PtException.Kind.NotFound) {
                throw PtException(
                    PtException.Kind.NotFound,
                    "Attempted to update a non-existent working version "
                )
            } else {
                throw e
            }
        }
        if (old.status != VersionStatus.Working) {
            throw PtException(PtException.Kind.Constraint,
                "Can only update a working version")
        }
        var newVersion: ArtifactVersion = old.copy(timestamp =  Instant.now().toEpochMilli())
        if (updatedContent != null) {
            newVersion = newVersion.copy(content = updatedContent)
        }
        if (updatedMetadata != null) {
            newVersion = newVersion.copy(metadata = updatedMetadata)
        }
        if (updatedParents != null) {
            newVersion = newVersion.copy(parents = updatedParents)
        }
        db.updateTyped(versionsColumn, newVersion.id, newVersion)
        return newVersion
    }

    /**
     * Commit a working version as a final, immutable version in
     * the artifact history.
     * @param auth the authenticated user performing the operation
     * @param project the name of the project containing the artifact.
     * @param artifactId the ID of the artifact
     * @param versionId the ID of the working version to be committed.
     */
    fun commitWorkingVersion(
        auth: AuthenticatedUser,
        project: String,
        artifactId: Id<Artifact>,
        versionId: Id<ArtifactVersion>
    ) {
        depot.users.validatePermissions(auth, Action.writeProject(project))
        val now = Instant.now().toEpochMilli()
        val version = try {
            retrieveVersion(auth, project, artifactId, versionId)
        } catch (e: PtException) {
            if (e.kind == PtException.Kind.NotFound) {
                throw PtException(
                    PtException.Kind.NotFound,
                    "Attempted to commit a non-existent working version "
                )
            } else {
                throw e
            }
        }
        if (version.status != VersionStatus.Working) {
            throw PtException(PtException.Kind.Constraint,
                "Can only commit a working version"
                )
        }
        db.putTyped(
            versionsColumn, version.id, version.copy(
                status = VersionStatus.Committed,
                timestamp = now
            )
        )
    }

    /**
     * Abort an in-progress version, discarding its content
     * and removing it from the depot
     * @param auth the authenticated user performing the operation
     * @param project the project containing the artifact
     * @param artifactId the artifact ID
     * @param versionId the versionId of the working version.
     */
    fun abortWorkingVersion(
        auth: AuthenticatedUser,
        project: String,
        artifactId: Id<Artifact>,
        versionId: Id<ArtifactVersion>
    ) {
        depot.users.validatePermissions(auth, Action.writeProject(project))
        val now = Instant.now().toEpochMilli()
        val version = try {
            retrieveVersion(auth, project, artifactId, versionId)
        } catch (e: PtException) {
            if (e.kind == PtException.Kind.NotFound) {
                throw PtException(
                    PtException.Kind.NotFound,
                    "Attempted to abort a non-existent working version "
                )
            } else {
                throw e
            }
        }
        if (version.status != VersionStatus.Working) {
            throw PtException(PtException.Kind.Constraint,
                "Can only abort a working version"
            )
        }
        db.putTyped(
            versionsColumn, version.id,
            version.copy(
                status = VersionStatus.Aborted,
                timestamp = now,
                content = ""
            )
        )

    }

    /**
     * Check the status of a version.
     * @param auth the authenticated user performing the operation
     * @param project the project containing the artifact.
     * @param artifactId the ID of the artifact
     * @param versionId the ID of the version
     * @return the version status
     */
    fun retrieveVersionStatus(
        auth: AuthenticatedUser,
        project: String,
        artifactId: Id<Artifact>,
        versionId: Id<ArtifactVersion>
    ): VersionStatus {
        val version = retrieveVersion(auth, project, artifactId, versionId)
        return version.status
    }

    private fun fetchParents(
        auth: AuthenticatedUser,
        project: String,
        artId: Id<Artifact>,
        verId: Id<ArtifactVersion>
    ): List<Id<ArtifactVersion>> {
        val art = retrieveVersion(auth, project, artId, verId)
        return art.parents
    }

    fun allAncestors(
        auth: AuthenticatedUser,
        project: String,
        artId: Id<Artifact>,
        verId: Id<ArtifactVersion>,
    ): Set<Id<ArtifactVersion>> {
        val queue = ArrayDeque<Id<ArtifactVersion>>()
        queue.add(verId)
        val allAncestors = HashSet<Id<ArtifactVersion>>()
        while (queue.isNotEmpty()) {
            val nxt = queue.removeFirst()
            if (nxt !in allAncestors) {
                allAncestors.add(nxt)
                queue.addAll(fetchParents(auth, project, artId, nxt))
            }
        }
        return allAncestors
    }

    fun versionIsAncestor(auth: AuthenticatedUser, project: String,
                          artifactId: Id<Artifact>,
                          maybeAncestor: Id<ArtifactVersion>,
                          maybeDescendant: Id<ArtifactVersion>
    ): Boolean {
        val queue = ArrayDeque<Id<ArtifactVersion>>()
        queue.add(maybeDescendant)
        val allAncestors = HashSet<Id<ArtifactVersion>>()
        while (queue.isNotEmpty()) {
            val nxt = queue.removeFirst()
            if (nxt == maybeAncestor) {
                return true
            }
            if (nxt !in allAncestors) {
                allAncestors.add(nxt)
                queue.addAll(fetchParents(auth, project, artifactId, nxt))
            }
        }
        return false
    }

    /**
     * Get the nearest common ancestor of two versions.
     *
     * This algorithm is based on frontier expansion. We start with the artifacts
     * and their immediate parents. If there's no common ancestor in the intersection of
     * those two sets, then we expand the frontier of the ancestry sets by adding some parents
     * of items in the histories. We keep expanding that frontier until eventually,
     * we find a common element.
     *
     * If there's more than one thing in the intersections, then the elements of
     * the intersection are equally distance from the two versions, so we can arbitrarily
     * choose any of them as a NCA.
     *
     * @param auth
     * @param project
     * @param artifactId
     * @param src
     * @param tgt
     * @returns
     */
    fun nearestCommonAncestor(
        auth: AuthenticatedUser,
        project: String,
        artifactId: Id<Artifact>,
        src: Id<ArtifactVersion>,
        tgt: Id<ArtifactVersion>
    ): Id<ArtifactVersion> {
        depot.users.validatePermissions(auth, Action.readProject(project))
        val sourceHistory: MutableSet<Id<ArtifactVersion>> = HashSet()
        val targetHistory: MutableSet<Id<ArtifactVersion>> = HashSet()
        val srcQueue: ArrayDeque<Id<ArtifactVersion>> = ArrayDeque()
        srcQueue.add(src)
        val tgtQueue: ArrayDeque<Id<ArtifactVersion>> = ArrayDeque()
        tgtQueue.add(tgt)
        while (
            sourceHistory.intersect(targetHistory).isEmpty() &&
            (srcQueue.size > 0 ||
                    tgtQueue.size > 0)
        ) {
            if (srcQueue.size > 0) {
                val nxt = srcQueue.removeFirst()
                if (nxt !in sourceHistory) {
                    sourceHistory.add(nxt)
                    val parents = fetchParents(auth, project, artifactId, nxt)
                    srcQueue.addAll(parents)
                }
            }
            if (
                sourceHistory.intersect(targetHistory).isEmpty() &&
                tgtQueue.size > 0
            ) {
                val nxt = tgtQueue.removeFirst()
                if (nxt !in targetHistory) {
                    targetHistory.add(nxt)
                    val parents = fetchParents(auth, project, artifactId, nxt)
                    tgtQueue.addAll(parents)
                }
            }
        }
        val ancestors = sourceHistory.intersect(targetHistory)
        if (ancestors.isEmpty()) {
            throw PtException(PtException.Kind.Internal,
                "Two versions of an artifact have no common ancestor. This should be impossible")
        }
        return ancestors.random()
    }

    override fun initStorage(config: Config) {
    }
}

