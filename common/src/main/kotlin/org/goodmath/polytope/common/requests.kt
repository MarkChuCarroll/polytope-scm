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
package org.goodmath.polytope.common

import kotlinx.serialization.Serializable
import org.goodmath.polytope.common.agents.MergeConflict
import org.goodmath.polytope.common.stashable.*

@Serializable
data class LoginRequest(
    val userId: String,
    val password: String
)

@Serializable
data class Token(
    val userId: String,
    val token: String
)

@Serializable
data class ProjectCreateRequest(
    val name: String,
    val description: String
)

@Serializable
data class ProjectListResponse(
    val projects: List<Project>
)

@Serializable
data class UserCreateRequest(
    val userId: String,
    val fullName: String,
    val email: String,
    val password: String,
    val permittedActions: List<Action>)

@Serializable
data class UserUpdateRequest(
    val kind: Kind,
    val actions: List<Action>? = null,
    val password: String? = null
) {
    enum class Kind {
        Reactivate, Deactivate, Grant, Revoke, Password
    }
}

@Serializable
data class ChangeListResponse(
    val changes: List<Change>
)


@Serializable
data class UserListResponse(val users: List<User>)

@Serializable
data class SavesListResponse(
    val saves: List<SavePoint>
)

@Serializable
data class HistoryCreateRequest(
    val name: String,
    val description: String,
    val parentHistory: String,
    val step: Int?
)

@Serializable
data class WorkspaceCreateRequest(
    val name: String,
    val history: String,
    val description: String
)

data class WorkspaceCreateChangeRequest(
    val history: String,
    val changeName: String,
    val description: String)

data class WorkspaceAddFileRequest(
    val path: String,
    val artifactType: String,
    val content: String)

data class HistoryListResponse(
    val histories: List<History>
)

data class HistoryStepsResponse(
    val steps: List<HistoryStep>
)

data class WorkspaceResetRequest(
    val reason: String,
    val stepIndex: Int?
)

data class WorkspaceListResponse(
    val workspaces: List<WorkspaceDescriptor>
)

data class PathListResponse(
    val paths: List<String>
)

data class WorkspaceMoveFileRequest(
    val pathBefore: String,
    val pathAfter: String
)

data class WorkspaceFileContents(
    val path: String,
    val artifactType: String,
    val content: String
)

data class WorkspaceGetMultiRequest(
    val paths: List<String>
)

data class WorkspaceSaveRequest(
    val description: String,
    val resolvedConflicts: List<Id<MergeConflict>>
)

data class WorkspaceDeliverRequest(
    val description: String,
)

data class WorkspaceIntegrateChangeRequest(
    val sourceHistory: String,
    val changeName: String
)

data class WorkspaceIntegrateDiffRequest(
    val fromVersion: ProjectVersionSpecifier,
    val toVersion: ProjectVersionSpecifier
)
