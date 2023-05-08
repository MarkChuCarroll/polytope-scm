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

import org.goodmath.polytope.common.agents.MergeConflict

data class WorkspaceDescriptor(
    val id: Id<Workspace>,
    val wsName: String,
    val project: String,
    val creator: String,
    val description: String,
    val createdAt: Long,
    val lastModified: Long
)

data class Workspace(
    val id: Id<Workspace>,
    val project: String,
    val name: String,
    val creator: String,
    val createdAt: Long,
    var lastModified: Long,
    val description: String,
    var basis: ProjectVersionSpecifier,
    val baselineId: Id<Artifact>,
    var baselineVersion: Id<ArtifactVersion>,
    var history: String,
    var change: String?,
    val workingVersions: MutableMap<Id<Artifact>, Id<ArtifactVersion>>,
    val modifiedArtifacts: MutableSet<Id<Artifact>> = HashSet(),
    val conflicts: MutableList<MergeConflict> = arrayListOf()
)
