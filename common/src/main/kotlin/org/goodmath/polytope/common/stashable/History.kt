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

@Serializable
data class History(
    val id: Id<History>,
    val project: String,
    val name: String,
    val description: String,
    val timestamp: Long,
    val basis: ProjectVersionSpecifier,
    val steps: MutableList<Id<HistoryStep>>
)

@Serializable
data class HistoryStep(
    val id: Id<HistoryStep>,
    val project: String,
    val change: Id<Change>?,
    val historyName: String,
    val number: Int,
    val baselineId: Id<Artifact>,
    val baselineVersionId: Id<ArtifactVersion>,
    val description: String
)
