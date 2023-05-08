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


/**
 * The record for an artifact in the depot
 */
@Serializable
data class Artifact(
    val id: Id<Artifact>,
    val artifactType: String,
    val timestamp: Long,
    val creator: String,
    val project: String,
    val metadata: Map<String, String>,
    val versions: List<Id<ArtifactVersion>>
)

@Serializable
enum class VersionStatus {
    Working, Committed, Aborted
}

@Serializable
data class ArtifactVersion(
    val id: Id<ArtifactVersion>,
    val artifactId: Id<Artifact>,
    val artifactType: String,
    val timestamp: Long,
    val creator: String,
    val content: String,
    val parents: List<Id<ArtifactVersion>>,
    val metadata: Map<String, String>,
    val status: VersionStatus
)
