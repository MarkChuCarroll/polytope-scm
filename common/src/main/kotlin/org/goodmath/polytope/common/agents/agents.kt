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
package org.goodmath.polytope.common.agents

import org.goodmath.polytope.common.stashable.Artifact
import org.goodmath.polytope.common.stashable.ArtifactVersion
import org.goodmath.polytope.common.stashable.Id

data class MergeConflict(
    val id: Id<MergeConflict>,
    val artifactId: Id<Artifact>,
    val artifactType: String,
    val sourceVersion: Id<ArtifactVersion>,
    val targetVersion: Id<ArtifactVersion>,
    val details: String
)

data class MergeResult(
    val artifactType: String,
    val artifactId: Id<Artifact>,
    val ancestorVersion: Id<ArtifactVersion>,
    val sourceVersion: Id<ArtifactVersion>,
    val targetVersion: Id<ArtifactVersion>,
    val proposedMerge: String,
    val conflicts: List<MergeConflict>
)

interface Agent<T> {
    val artifactType: String
    fun encodeToString(content: T): String
    fun decodeFromString(content: String): T

    fun merge(
        ancestor: ArtifactVersion,
        source: ArtifactVersion,
        target: ArtifactVersion
    ): MergeResult

}



