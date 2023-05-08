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

package org.goodmath.polytope.depot.agents

import org.goodmath.polytope.PtException
import org.goodmath.polytope.depot.stashes.Artifact
import org.goodmath.polytope.depot.stashes.ArtifactVersion
import org.goodmath.polytope.depot.util.ID_CONFLICT
import org.goodmath.polytope.depot.util.Id
import org.goodmath.polytope.depot.util.ParsingCommons
import org.goodmath.polytope.depot.util.newId


/**
 * A directory is just a list of entries, each of which is a name/id pair.
 */
data class Directory(
    val entries: MutableList<DirectoryEntry> = ArrayList()
) {
    data class DirectoryEntry(
        val name: String,
        val artifact: Id<Artifact>
    )

    fun removeBinding(name: String) {
        entries.removeIf { it.name == name }
    }

    fun addBinding(name: String, artifactId: Id<Artifact>) {
        if (entries.any { it.name == name }) {
            throw PtException(
                PtException.Kind.Conflict,
                "Binding already exists for $name"
            )
        } else {
            entries.add(DirectoryEntry(name, artifactId))
        }
    }

    fun containsBinding(name: String): Boolean =
        entries.any { it.name == name }

    fun getBinding(name: String): Id<Artifact>? {
        return entries.firstOrNull { it.name == name }?.artifact
    }

    fun getNameFor(id: Id<Artifact>): String? {
        return entries.firstOrNull { it.artifact == id }?.name
    }
}

/**
 * For computing merges, a structure that describes the changes
 * between a base version and a modified version of a directory.
 */
data class DirectoryChange(
    val type: Kind,
    val artifactId: Id<Artifact>,
    val nameBefore: String?,
    val nameAfter: String?
) {
    enum class Kind {
        Add, Rename, Remove
    }

    /**
     * Apply a change, produced from one directory comparison,
     * to a different directory. This merges a change from one
     * modified version to another.
     */
    fun applyTo(dir: Directory) {
        return when (type) {
            Kind.Rename -> {
                dir.removeBinding(nameBefore!!)
                dir.addBinding(nameAfter!!, artifactId)
            }

            Kind.Add -> {
                if (nameAfter != null && !dir.containsBinding(nameAfter)) {
                    dir.addBinding(nameAfter, artifactId)
                } else {
                    throw IllegalArgumentException("Add must have a non-null new binding name")
                }
            }

            Kind.Remove ->
                dir.removeBinding(nameBefore!!)

        }
    }
}

/**
 * Information about a marge conflict that's specific to directories.
 * This will be the contents of the 'details' field of the conflict record.
 */
data class DirectoryMergeConflict(
    val kind: ConflictKind,
    val nameBefore: String?,
    val nameInMergeSource: String?,
    val nameInMergeTarget: String?,
) {
    enum class ConflictKind {
        ADD_ADD_NAME, ADD_ADD_ID, MOD_DEL, DEL_MOD, MOD_MOD
    }

    fun encodeToString(): String {
        return ParsingCommons.klaxon.toJsonString(this)
    }

    companion object {
        fun decodeFromString(cf: String): DirectoryMergeConflict {
            return ParsingCommons.klaxon.parse(cf)!!
        }
    }
}

/**
 * The agent for working with directory artifacts.
 */
object DirectoryAgent : Agent<Directory> {
    override val artifactType: String = "directory"
    override fun decodeFromString(content: String): Directory {
        return ParsingCommons.klaxon.parse<Directory>(content)!!
    }

    override fun encodeToString(content: Directory): String {
        return ParsingCommons.klaxon.toJsonString(content)
    }


    override fun merge(ancestor: ArtifactVersion, source: ArtifactVersion, target: ArtifactVersion): MergeResult {
        val ancDir = decodeFromString(ancestor.content)
        val srcDir = decodeFromString(source.content)
        val tgtDir = decodeFromString(target.content)
        val ancBindings = DualMapping.fromDirectory(ancDir)
        val srcBindings = DualMapping.fromDirectory(srcDir)
        val tgtBindings = DualMapping.fromDirectory(tgtDir)

        fun createConflict(
            detail: DirectoryMergeConflict
        ): MergeConflict {
            return MergeConflict(
                id = newId<MergeConflict>(ID_CONFLICT),
                artifactId = ancestor.artifactId,
                artifactType = artifactType,
                sourceVersion = source.id,
                targetVersion = target.id,
                details = detail.encodeToString()
            )
        }


        // Step 1: compare each of the merge source and merge target to
        // the common ancestor, gathering the changes.
        val changesInMergeSource = computeDirectoryChanges(ancBindings, srcBindings)
        val changesInMergeTarget = computeDirectoryChanges(ancBindings, tgtBindings)

        // At this point, we've got a collection of the changes to the directory
        // in both the merge source and merge target. What we need to do now
        // is walk through those.
        //
        // For each directory change in the merge source, we need to check to see if there's
        // a conflicting change in the merge target, and vice versa. If we find
        // a conflict, then we need to put a best-guess into the merge result, and
        // add a conflict record.

        val conflicts = ArrayList<MergeConflict>()
        val proposedMergeResult = tgtDir.copy()


        for (changeInSource in changesInMergeSource) {
            var conflicted = false
            // First, look to see if there are two adds with the same name.
            if (changeInSource.type == DirectoryChange.Kind.Add) {
                val sameNameChangeInTarget = changesInMergeTarget.firstOrNull {
                    it.nameAfter == changeInSource.nameAfter &&
                            it.artifactId != changeInSource.artifactId
                }
                if (sameNameChangeInTarget != null) {
                    conflicted = true
                    val conflict =
                        createConflict(
                            DirectoryMergeConflict(
                                DirectoryMergeConflict.ConflictKind.ADD_ADD_NAME,
                                nameBefore = null,
                                nameInMergeSource = changeInSource.nameAfter,
                                nameInMergeTarget = sameNameChangeInTarget.nameAfter
                            )
                        )
                    conflicts.add(conflict)
                    proposedMergeResult.addBinding(
                        "${changeInSource.nameAfter}_${conflict.id}",
                        changeInSource.artifactId
                    )
                }
            }
            // check if there are two changes that affect the same artifact ID
            val changeInTarget = changesInMergeTarget.firstOrNull {
                it.artifactId == changeInSource.artifactId
            }
            if (changeInTarget != null) {
                System.err.println("Src: $changeInSource, tgt = $changeInTarget")
                when (Pair(changeInSource.type, changeInTarget.type)) {
                    Pair(DirectoryChange.Kind.Add, DirectoryChange.Kind.Add) -> {
                        conflicted = true
                        conflicts.add(
                            createConflict(
                                DirectoryMergeConflict(
                                    DirectoryMergeConflict.ConflictKind.ADD_ADD_ID,
                                    nameBefore = null,
                                    nameInMergeSource = changeInSource.nameAfter,
                                    nameInMergeTarget = changeInTarget.nameAfter
                                )
                            )
                        )
                        // Don't add anything to the proposed result: there's already an
                        // add for this artifact.
                    }

                    Pair(DirectoryChange.Kind.Add, DirectoryChange.Kind.Remove) -> {
                        // should be impossible - you can't both add a rename the same thing.
                        throw PtException(
                            PtException.Kind.Internal,
                            "Impossible case in baseline merge: Add-Remove artifact"
                        )
                    }

                    Pair(DirectoryChange.Kind.Add, DirectoryChange.Kind.Rename) -> {
                        // should be impossible - you can't both add a rename the same thing.
                        throw PtException(
                            PtException.Kind.Internal,
                            "Impossible case in baseline merge: Add-Rename artifact"
                        )
                    }

                    Pair(DirectoryChange.Kind.Rename, DirectoryChange.Kind.Add) -> {
                        // can't happen.
                        throw PtException(
                            PtException.Kind.Internal,
                            "Impossible case in baseline merge: Rename-Add artifact"
                        )
                    }

                    Pair(DirectoryChange.Kind.Rename, DirectoryChange.Kind.Remove) -> {
                        conflicted = true
                        conflicts.add(
                            createConflict(
                                DirectoryMergeConflict(
                                    DirectoryMergeConflict.ConflictKind.MOD_DEL,
                                    nameBefore = changeInSource.nameBefore,
                                    nameInMergeSource = changeInSource.nameAfter,
                                    nameInMergeTarget = changeInTarget.nameAfter
                                )
                            )
                        )
                        proposedMergeResult.addBinding(changeInSource.nameAfter!!, changeInSource.artifactId)
                    }

                    Pair(DirectoryChange.Kind.Rename, DirectoryChange.Kind.Rename) -> {
                        if (changeInSource.nameAfter != changeInTarget.nameAfter) {
                            conflicted = true
                            conflicts.add(
                                createConflict(
                                    DirectoryMergeConflict(
                                        DirectoryMergeConflict.ConflictKind.MOD_MOD,
                                        nameBefore = changeInSource.nameBefore,
                                        nameInMergeSource = changeInSource.nameAfter,
                                        nameInMergeTarget = changeInTarget.nameAfter
                                    )
                                )
                            )
                            // Don't add any bindings: the artifact is already present.
                        }
                    }

                    Pair(DirectoryChange.Kind.Remove, DirectoryChange.Kind.Add) -> {
                        // can't happen.
                        throw PtException(
                            PtException.Kind.Internal,
                            "Impossible case in baseline merge: Remove-Add artifact"
                        )
                    }

                    Pair(DirectoryChange.Kind.Remove, DirectoryChange.Kind.Rename) -> {
                        conflicted = true
                        conflicts.add(
                            createConflict(
                                DirectoryMergeConflict(
                                    DirectoryMergeConflict.ConflictKind.DEL_MOD,
                                    nameBefore = changeInSource.nameBefore,
                                    nameInMergeSource = changeInSource.nameAfter,
                                    nameInMergeTarget = changeInTarget.nameAfter
                                )
                            )
                        )
                        // No need to add a binding: one is already present.
                    }

                    Pair(DirectoryChange.Kind.Remove, DirectoryChange.Kind.Remove) -> {
                        Unit // the source and target already match, so the remove is in
                        // the proposed.
                    }
                }
            }
            if (!conflicted) {
                changeInSource.applyTo(proposedMergeResult)
            }
        }
        // assemble into a merge result.
        return MergeResult(
            artifactType = this.artifactType,
            artifactId = ancestor.artifactId,
            ancestorVersion = ancestor.id,
            sourceVersion = source.id,
            targetVersion = target.id,
            proposedMerge = encodeToString(proposedMergeResult),
            conflicts = conflicts
        )
    }


    // Helper functions for computing diff/merge.

    /**
     * For directory merges, we need both the primary mapping from
     * name to artifact, and also the secondary mapping from artifact to name.
     */
    data class DualMapping(
        val byName: Map<String, Id<Artifact>>,
        val byArtifact: Map<Id<Artifact>, String>
    ) {
        companion object {
            fun fromDirectory(dir: Directory): DualMapping {
                return DualMapping(
                    dir.entries.associate { Pair(it.name, it.artifact) },
                    dir.entries.associate { Pair(it.artifact, it.name) }
                )
            }
        }
    }

    private fun unmodified(id: String, baseVersion: DualMapping, modifiedVersion: DualMapping): Boolean =
        baseVersion.byArtifact.containsKey(id) && modifiedVersion.byArtifact.containsKey(id) &&
                baseVersion.byArtifact[id] == modifiedVersion.byArtifact[id]

    private fun added(id: String, baseVersion: DualMapping, modifiedVersion: DualMapping): Boolean =
        modifiedVersion.byArtifact.containsKey(id) &&
                !baseVersion.byArtifact.containsKey(id)

    private fun renamed(id: String, baseVersion: DualMapping, modifiedVersion: DualMapping): Boolean =
        baseVersion.byArtifact.containsKey(id) && modifiedVersion.byArtifact.containsKey(id) &&
                baseVersion.byArtifact[id] != modifiedVersion.byArtifact[id]

    private fun removed(id: String, baseVersion: DualMapping, modifiedVersion: DualMapping): Boolean =
        baseVersion.byArtifact.containsKey(id) && !modifiedVersion.byArtifact.containsKey(id)


    private fun computeDirectoryChanges(baseVersion: DualMapping, modifiedVersion: DualMapping): List<DirectoryChange> {
        val allIds = HashSet(baseVersion.byArtifact.keys + modifiedVersion.byArtifact.keys)
        return allIds.mapNotNull { id ->
            System.err.println("Considering $id:\n\tbase: $baseVersion\n\tmod: $modifiedVersion")
            if (!unmodified(id, baseVersion, modifiedVersion)) {
                when {
                    added(id, baseVersion, modifiedVersion) -> {
                        System.err.println("ADD")
                        DirectoryChange(
                            DirectoryChange.Kind.Add, id, null, modifiedVersion.byArtifact[id]!!
                        )
                    }

                    removed(id, baseVersion, modifiedVersion) -> {
                        System.err.println("DEL")
                        DirectoryChange(
                            DirectoryChange.Kind.Remove, id, baseVersion.byArtifact[id]!!, null
                        )
                    }

                    renamed(id, baseVersion, modifiedVersion) -> {
                        System.err.println("RENAME")
                        DirectoryChange(
                            DirectoryChange.Kind.Rename,
                            id,
                            baseVersion.byArtifact[id]!!,
                            modifiedVersion.byArtifact[id]!!,
                        )
                    }

                    else -> {
                        null // do nothing.
                    }
                }
            } else {
                null
            }
        }
    }

}
