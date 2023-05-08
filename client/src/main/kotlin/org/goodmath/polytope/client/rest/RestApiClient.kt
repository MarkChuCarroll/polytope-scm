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
package org.goodmath.polytope.client.rest

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import org.goodmath.polytope.client.cli.klaxon
import org.goodmath.polytope.common.*
import org.goodmath.polytope.common.stashable.*

typealias AuthToken = String

/**
 * A client API wrapping the Polytope server REST API.
 */
class RestApiClient(
    serverUrlStr: String,
    private val userId: String,
    private val password: String) {

    private val serverUrl = Url(serverUrlStr)

    companion object {
        private val authClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json()
            }
        }

        /**
         * A helper function for the auth functionality - this uses the
         * login request method of the server to get a JWT auth token
         * that can be used as a bearer authentication.
         */
        suspend fun getAuthToken(serverUrl: Url, userId: String, password: String): BearerTokens {
            val response = authClient.post(serverUrl) {
                url {
                    appendPathSegments("login")
                }
                headers {
                    contentType(ContentType.Application.Json)
                }
                setBody(LoginRequest(userId, password))
            }
            val responseBody: Token = response.body()
            return BearerTokens(responseBody.token, responseBody.token)
        }
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
        install(Auth) {
            bearer {
                loadTokens {
                    getAuthToken(serverUrl, userId, password)
                }
                refreshTokens {
                    getAuthToken(serverUrl, userId, password)
                }
                sendWithoutRequest { request ->
                    request.url.host == serverUrl.host &&
                            request.url.encodedPath.startsWith("polytope")
                }
            }
        }
    }

    /**
     * A convenience wrapper, which runs an HTTP request, and unpacks the response,
     * turning 2xxs into return values, and throwing an appropriate exception for
     * anything else.
     */
    private suspend inline fun <reified T> runWithErrorHandling(
        requester: () -> HttpResponse
    ): T {
        val response = requester()
        if (response.status.value in 200..299) {
            val body = response.bodyAsText()
            return klaxon.parse(body)!!
        } else {
            throw PtException(response.status, response.bodyAsText())
        }
    }

    /**
     * Get a list of users registered with the server.
     */
    suspend fun userList(): UserListResponse {
        return runWithErrorHandling {
            client.get(serverUrl) {
                url {
                    appendPathSegments("users")
                }
            }
        }
    }

    suspend fun userGet(username: String): User {
        return runWithErrorHandling {
            client.get(serverUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                }

                url {
                    appendPathSegments("users", username)
                }
            }
        }
    }
    suspend fun userGrant(userId: String, permitted: List<Action>): User {
        return runWithErrorHandling {
            client.put(serverUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                }

                url {
                    appendPathSegments("users", userId)
                }
                setBody(
                    UserUpdateRequest(
                        kind = UserUpdateRequest.Kind.Grant,
                        actions = permitted
                    )
                )
            }
        }
    }

    suspend fun userRevoke(userId: String, revoked: List<Action>): User {
        return runWithErrorHandling {
            client.put(serverUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                }

                url {
                    appendPathSegments("users", userId)
                }
                setBody(
                    UserUpdateRequest(
                        kind = UserUpdateRequest.Kind.Revoke,
                        actions = revoked
                    )
                )
            }
        }
    }
    suspend fun userDeactivate(userId: String) {
        return runWithErrorHandling {
            client.put(serverUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                }

                url {
                    appendPathSegments("users", userId)
                }
                setBody(
                    UserUpdateRequest(
                        kind = UserUpdateRequest.Kind.Deactivate
                    )
                )
            }
        }
    }
    suspend fun userReactivate( userId: String) {
        return runWithErrorHandling {
            client.put(serverUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                }

                url {
                    appendPathSegments("users", userId)
                }
                setBody(
                    UserUpdateRequest(
                        kind = UserUpdateRequest.Kind.Reactivate
                    )
                )

            }
        }
    }

    suspend fun userChangePassword(userId: String, newPassword: String): User {
        return runWithErrorHandling {
            client.put(serverUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                }
                url {
                    appendPathSegments("users", userId)
                }
                setBody(
                    UserUpdateRequest(
                        kind = UserUpdateRequest.Kind.Password,
                        password = newPassword
                    )
                )
            }
        }

    }

    suspend fun userCreate(userId: String, userFullName: String,
                            userEmail: String, userPassword: String,
                           permitted: List<Action>): User {
        return runWithErrorHandling {
            client.post(serverUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                }
                url {
                    appendPathSegments("users")
                }
                setBody(
                    UserCreateRequest(
                        userId, userFullName, userEmail, userPassword,
                        permitted
                    )
                )
            }
        }
    }

    suspend fun projectList(): ProjectListResponse {
        return runWithErrorHandling {
            client.get(serverUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                }
                url {
                    appendPathSegments("projects")
                }
            }
        }
    }

    suspend fun projectCreate(projectName: String, description: String): Project {
        return runWithErrorHandling {
            client.post(serverUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                }
                url {
                    appendPathSegments("projects")
                }
                setBody(ProjectCreateRequest(projectName, description))
            }
        }
    }

    suspend fun projectGet(name: String): Project {
        return runWithErrorHandling {
            client.get(serverUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                }
                url {
                    appendPathSegments("projects", name)
                }
            }
        }
    }

    suspend fun historyList(project: String): List<History> {
        return runWithErrorHandling {
            client.get(serverUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                }
                url {
                    appendPathSegments("projects", project, "histories")
                }
            }
        }
    }

    suspend fun historyCreate(project: String,
                              historyName: String, description: String,
                              parentHistory: String, parentHistoryIndex: Int? = null): History {
        return runWithErrorHandling {
            client.post(serverUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                }
                url {
                    appendPathSegments("projects", project, "histories")
                    setBody(
                        HistoryCreateRequest(
                            name = historyName,
                            description = description,
                            parentHistory = parentHistory,
                            step = parentHistoryIndex
                        )
                    )
                }
            }
        }
    }

    suspend fun historyGet(project: String, history: String): History {
        return runWithErrorHandling {
            client.get(serverUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                }
                url {
                    appendPathSegments("projects", project, "histories", history)
                }
            }
        }

    }

    suspend fun stepsList(project: String, history: String): List<HistoryStep> {
        return runWithErrorHandling {
            client.get(serverUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                }
                url {
                    appendPathSegments("projects", project, "histories", history, "steps")
                }
            }
        }
    }

    suspend fun changesList(project: String, history: String): List<Change> {
        return runWithErrorHandling {
            client.get(serverUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                }
                url {
                    appendPathSegments("projects", project, "histories", history, "changes")
                }
            }
        }
    }

    suspend fun changesList(project: String, history: String, change: String): Change {
        return runWithErrorHandling {
            client.get(serverUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                }
                url {
                    appendPathSegments("projects", project, "histories", history, "changes", change)
                }
            }
        }
    }

    suspend fun savesList(project: String, history: String, change: String): List<SavePoint> {
        return runWithErrorHandling {
            client.get(serverUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                }
                url {
                    appendPathSegments("projects", project, "histories", history, "changes", change, "saves")
                }
            }
        }
    }

    suspend fun workspacesList(project: String): List<WorkspaceDescriptor> {
        return runWithErrorHandling {
            client.get(serverUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                }
                url {
                    appendPathSegments("projects", project, "workspaces")
                }
            }
        }
    }

    suspend fun workspaceCreate(project: String, history: String,
                                wsName: String,
                                description: String
                                ): Workspace {
        return runWithErrorHandling {
            client.post(serverUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                }
                url {
                    appendPathSegments("projects", project, "workspaces")
                }
                setBody(
                    WorkspaceCreateRequest(
                        name = wsName,
                        history = history,
                        description = description
                    )
                )
            }
        }
    }

    suspend fun workspaceGet(project: String, name: String): Workspace {
        return runWithErrorHandling {
            client.get(serverUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                }
                url {
                    appendPathSegments("projects", project, "workspaces", name)
                }
            }
        }
    }

    suspend fun workspaceUtd(project: String, name: String): Boolean {
        return runWithErrorHandling {
            client.get(serverUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                }
                url {
                    appendPathSegments("projects", project, "workspaces", name, "utd")
                }
            }
        }
    }

    suspend fun workspaceCreateChange(project: String, name: String,
                                      description: String): Workspace {
        return runWithErrorHandling {
            client.post(serverUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                }
                url {
                    appendPathSegments("projects", project, "workspaces", name, "action", "createChange")
                    setBody(WorkspaceCreateChangeRequest(name, description))
                }
            }
        }
    }

    suspend fun workspaceListPaths(project: String, name: String): List<String> {
        return runWithErrorHandling {
            client.get(serverUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                }
                url {
                    appendPathSegments("projects", project, "workspaces", name, "paths")
                }
            }
        }
    }

    suspend fun workspaceAddFile(project: String, name: String,
                                 path: String, contents: WorkspaceFileContents
    ): Workspace {
        return runWithErrorHandling {
            client.post(serverUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                }
                url {
                    appendPathSegments("projects", project, "workspaces", name, "paths")
                    appendPathSegments(path.split("/"))
                    setBody(contents)
                }
            }
        }
    }

    suspend fun workspaceMoveFile(project: String, name: String,
                                  oldPath: String, newPath: String): Workspace {
        return runWithErrorHandling {
            client.post(serverUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                }
                url {
                    appendPathSegments("projects", project, "workspaces", name, "action", "move")
                    setBody(WorkspaceMoveFileRequest(oldPath, newPath))
                }
            }
        }
    }

    suspend fun workspaceDeleteFile(project: String, name: String,
                                 path: String): List<String> {
        return runWithErrorHandling {
            client.delete(serverUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                }
                url {
                    appendPathSegments("projects", project, "workspaces", name, "paths")
                    appendPathSegments(path.split("/"))
                }
            }
        }
    }

    suspend fun workspaceModifyFile(project: String, name: String,
                                    path: String, contents: String): Workspace {
        return runWithErrorHandling {
            client.put(serverUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                }
                url {
                    appendPathSegments("projects", project, "workspaces", name, "paths")
                    appendPathSegments(path.split("/"))
                }
                setBody(contents)
            }
        }
    }

    suspend fun workspaceGetFile(project: String, name: String,
                                 path: String): WorkspaceFileContents {
        return runWithErrorHandling {
            client.get(serverUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                }
                url {
                    appendPathSegments("projects", project, "workspaces", name, "paths")
                    appendPathSegments(path.split("/"))
                }
            }
        }
    }


    suspend fun workspaceGetFiles(project: String, name: String,
                                  paths: List<String>): List<WorkspaceFileContents> {
        return runWithErrorHandling {
            client.post(serverUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                }
                url {
                    appendPathSegments("projects", project, "workspaces", name, "multi")
                }
                setBody(WorkspaceGetMultiRequest(paths))
            }
        }
    }
    suspend fun workspaceSave(project: String, name: String, description: String,
                              resolved: List<String>): Workspace {
        return runWithErrorHandling {
            client.post(serverUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                }
                url {
                    appendPathSegments("projects", project, "workspaces", name, "action", "save")
                }
                setBody(WorkspaceSaveRequest(description, resolved))
            }
        }
    }
    suspend fun workspaceDeliver(project: String, name: String,
                                 description: String): Workspace {
        return runWithErrorHandling {
            client.post(serverUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                }
                url {
                    appendPathSegments("projects", project, "workspaces", name, "action", "deliver")
                }
                setBody(WorkspaceDeliverRequest(description))
            }
        }
    }
    suspend fun workspaceUpdate(project: String, name: String): Workspace {
        return runWithErrorHandling {
            client.post(serverUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                }
                url {
                    appendPathSegments("projects", project, "workspaces", name, "action", "update")
                }
            }
        }
    }

    suspend fun workspaceIntegrate(project: String, name: String,
                                   from: ProjectVersionSpecifier,
                                   to: ProjectVersionSpecifier
    ): Workspace {
        return runWithErrorHandling {
            client.post(serverUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                }
                url {
                    appendPathSegments("projects", project, "workspaces", name, "action", "integrate")
                    setBody(WorkspaceIntegrateRequest(from, to))
                }
            }
        }
    }

    suspend fun workspaceDelete(project: String, name: String): Workspace {
        return runWithErrorHandling {
            client.delete(serverUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                }
                url {
                    appendPathSegments("projects", project, "workspaces", name)
                }
            }
        }
    }

    suspend fun workspaceAbandon(project: String, name: String): Workspace {
        return runWithErrorHandling {
            client.post(serverUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                }
                url {
                    appendPathSegments("projects", project, "workspaces", name, "action", "abandon")
                }
            }
        }
    }
}