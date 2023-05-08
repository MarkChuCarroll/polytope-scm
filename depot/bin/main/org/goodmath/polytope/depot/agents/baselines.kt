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
import org.goodmath.polytope.depot.util.*

data class Baseline(
    val rootDir: Id<Artifact>,
    val entries: MutableMap<Id<Artifact>, Id<ArtifactVersion>>
) {
    fun contains(artifactId: Id<Artifact>): Boolean =
        entries.containsKey(artifactId)

    fun get(artifactId: Id<Artifact>): Id<ArtifactVersion>? =
        entries[artifactId]

    fun add(artifactId: Id<Artifact>, versionId: Id<ArtifactVersion>) {
        if (contains(artifactId)) {
            throw PtException(PtException.Kind.Conflict,
                "Baseline already contains a mapping for $artifactId")
        } else {
            entries[artifactId] = versionId
        }
    }

     fun remove(artifactId: Id<Artifact>) {
        if (!entries.containsKey(artifactId)) {
            throw PtException(PtException.Kind.NotFound,
                "Baseline doesn't contain a mapping for $artifactId")
        } else {
            entries.remove(artifactId)
        }
    }
    fun change(artifactId: Id<Artifact>, versionId: Id<ArtifactVersion>) {
        remove(artifactId)
        add(artifactId, versionId)
    }
}


enum class BaselineConflictType {
    MOD_DEL, DEL_MOD, MOD_MOD
}

data class BaselineConflict(
    val type: BaselineConflictType,
    val artifactId: Id<Artifact>,
    val mergeSourceVersion: Id<ArtifactVersion>?,
    val mergeTargetVersion: Id<ArtifactVersion>?,
) {

    fun encodeToString():  String {
        return ParsingCommons.klaxon.toJsonString(this)
    }
    companion object {
        fun decodeFromString(deets: String): BaselineConflict {
            return ParsingCommons.klaxon.parse(deets)!!
        }

    }
}

object BaselineAgent: Agent<Baseline> {
    override val artifactType: String = "baseline"

    override fun decodeFromString(content: String): Baseline =
        ParsingCommons.klaxon.parse<Baseline>(content)!!

    override fun encodeToString(content: Baseline): String {
        return ParsingCommons.klaxon.toJsonString(content)
    }

    override fun merge(
        ancestor: ArtifactVersion,
        source: ArtifactVersion,
        target: ArtifactVersion
    ): MergeResult {
        val ancestorBaseline = decodeFromString(ancestor.content)
        val sourceBaseline = decodeFromString(source.content)
        val targetBaseline = decodeFromString(target.content)

        val targetVersionMap = targetBaseline.entries
        val targetArtifacts = targetVersionMap.keys
        val ancestorVersions = ancestorBaseline.entries
        val ancestorArtifacts = ancestorVersions.keys
        val sourceVersions = sourceBaseline.entries
        val sourceArtifacts = sourceVersions.keys


        val removedInTarget = ancestorArtifacts.minus(targetArtifacts)
        val removedInSource = ancestorArtifacts.minus(sourceArtifacts)

        val addedInTarget = targetArtifacts.minus(ancestorArtifacts)
        val addedInSource = sourceArtifacts.minus(ancestorArtifacts)

        val modifiedInTarget = targetArtifacts.filter { artId ->
            ancestorArtifacts.contains(artId) && ancestorVersions[artId] != targetVersionMap[artId]
        }
        val modifiedInSource = sourceArtifacts.filter { artId ->
            ancestorArtifacts.contains(artId) && ancestorVersions[artId] != sourceVersions[artId]
        }

        val allArtifacts = sourceArtifacts.union(targetArtifacts).union(ancestorArtifacts)

        val mergedVersionMappings = HashMap<Id<Artifact>, Id<ArtifactVersion>>()
        val conflicts = ArrayList<MergeConflict>()
        for (artId in allArtifacts) {
            val targetArtVersion = targetVersionMap[artId]
            val sourceArtVersion = sourceVersions[artId]
            val modSource = modifiedInSource.contains(artId)
            val modCurrent = modifiedInTarget.contains(artId)

            if (removedInTarget.contains(artId)) {
                if (modifiedInSource.contains(artId)) {
                    conflicts.add(
                        MergeConflict(
                            id = newId<MergeConflict>("baselineMerge"),
                            artifactId = ancestor.artifactId,
                            artifactType = BaselineAgent.artifactType,
                            sourceVersion = source.id,
                            targetVersion = target.id,
                            details = BaselineConflict(
                                BaselineConflictType.MOD_DEL,
                                artId,
                                sourceArtVersion!!, null
                            ).encodeToString()
                        )
                    )
                    mergedVersionMappings[artId] = sourceArtVersion
                }
            } else if (removedInSource.contains(artId)) {
                if (modifiedInTarget.contains(artId)) {
                    conflicts.add(MergeConflict(
                        id = newId<MergeConflict>("baselineMerge"),
                        artifactId = ancestor.artifactId,
                        artifactType = BaselineAgent.artifactType,
                        sourceVersion = source.id,
                        targetVersion = target.id,
                        details = BaselineConflict(BaselineConflictType.DEL_MOD,
                            artId,
                            null, targetArtVersion).encodeToString()))
                    mergedVersionMappings[artId] = targetArtVersion!!
                }
            } else if (addedInTarget.contains(artId)) {
                mergedVersionMappings[artId] = targetArtVersion!!
            } else if (addedInSource.contains(artId)) {
                mergedVersionMappings[artId] = sourceArtVersion!!
            }
            else if (modCurrent && !modSource) {
                mergedVersionMappings[artId] = targetArtVersion!!
            } else if (!modCurrent && modSource) {
                mergedVersionMappings[artId] = sourceArtVersion!!
            } else if (!modCurrent) {  //  modSource must be false here
                mergedVersionMappings[artId] = sourceArtVersion!!
            } else { // modified in both
                mergedVersionMappings[artId] = targetArtVersion!!
                conflicts.add(
                    MergeConflict(
                        id = newId<MergeConflict>("baselineMerge"),
                        artifactId = ancestor.artifactId,
                        artifactType = BaselineAgent.artifactType,
                        sourceVersion = source.id,
                        targetVersion = target.id,
                        details = ParsingCommons.klaxon.toJsonString(
                            BaselineConflict(BaselineConflictType.MOD_MOD,
                                artId,
                                sourceArtVersion, targetArtVersion))))

            }
        }
        return MergeResult(
            artifactType = this.artifactType,
            artifactId = ancestor.artifactId,
            ancestorVersion = ancestor.id,
            sourceVersion = source.id,
            targetVersion = target.id,
            proposedMerge = encodeToString(Baseline(targetBaseline.rootDir,
                mergedVersionMappings)),
            conflicts = conflicts)
    }
}
