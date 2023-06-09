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
package org.goodmath.polytope.common.stashable

import kotlinx.serialization.Serializable
import org.goodmath.polytope.common.util.toLocalDateTime
import java.lang.StringBuilder

enum class ChangeStatus {
    Open,
    Closed,
    Aborted
}

@Serializable
data class Change(
    val id: Id<Change>,
    val project: String,
    val name: String,
    val history: String,
    val basis: ProjectVersionSpecifier,
    val description: String,
    val timestamp: Long,
    val baseline: Id<ArtifactVersion>,
    val savePoints: List<Id<SavePoint>>,
    val status: ChangeStatus
) {
    fun render(): String {
        val ts = toLocalDateTime(timestamp)
        val result = StringBuilder()

        result.append("Change: ${project}::${history}::${name} [Status: ${status}\n")
            .append("Description: ${description}\n")
            .append("Basis: ${basis}\n")
            .append("Created at: $ts\n")
            .append("Contains ${savePoints.size} savepoints\n")
        return result.toString()
    }
}

@Serializable
data class SavePoint(
    val id: Id<SavePoint>,
    val changeId: Id<Change>,
    val creator: String,
    val description: String,
    val basis: ProjectVersionSpecifier,
    val baselineVersion: Id<ArtifactVersion>,
    val modifiedArtifacts: List<Id<Artifact>>,
    val timestamp: Long
) {
    fun render(): String {
        val ts = toLocalDateTime(timestamp)
        val result = StringBuilder()
        result.append("SavePoint: ${id} < ${basis}\n")
            .append("Created by: ${creator} at $ts\n")
            .append("Changes: ${modifiedArtifacts.joinToString(", ")}\n")
        return result.toString()

    }
}