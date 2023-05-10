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
package org.goodmath.polytope.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.goodmath.polytope.common.*
import org.goodmath.polytope.depot.Depot
import org.goodmath.polytope.common.util.ParsingCommons
import org.goodmath.polytope.common.stashable.*
import java.util.*


suspend inline fun <reified T : Any> callWithAuthAndResultHandling(
    call: ApplicationCall,
    depot: Depot,
    body: (user: AuthenticatedUser, depot: Depot, call: ApplicationCall) -> T
) {
    val principal = call.principal<JWTPrincipal>()
    val user = principal?.payload?.getClaim("userId")?.asString()
    val token = principal?.payload?.getClaim("token")?.asString()
    if (user == null || token == null) {
        call.respond(HttpStatusCode.Forbidden, "Valid userid credential not supplied")
        return
    }

    val auth = depot.users.validateAuthToken(user, token)
    if (auth == null) {
        call.respond(HttpStatusCode.Forbidden, "Valid userid credential not supplied")
    } else {
        try {
            call.respond(body(auth, depot, call))
        } catch (e: PtException) {
            val code = e.toStatusCode()
            call.respond(code, e.toString())
        } catch (e: Throwable) {
            System.err.println("Uncaught exception: $e")
            throw e
        }
    }
}



fun Application.configureRouting() {

    val depot = Depot.getDepot(this@configureRouting.environment.config)

    routing {
        route("/polytope/v0") {
            // login(userId, password): token
            post("login") {
                try {
                    val loginRequest = call.receive<LoginRequest>()
                    val auth = depot.users.authenticate(
                        loginRequest.userId,
                        loginRequest.password
                    )
                    val (token, exp) = depot.users.generateAuthToken(auth)
                    val jwt = JWT.create()
                        .withAudience(this@configureRouting.environment.config.property("jwt.audience").getString())
                        .withIssuer(this@configureRouting.environment.config.property("jwt.issuer").getString())
                        .withClaim("userId", loginRequest.userId)
                        .withClaim("token", token)
                        .withExpiresAt(Date(exp))
                        .sign(
                            Algorithm.HMAC256(
                                this@configureRouting.environment.config.property("jwt.secret").getString()
                            )
                        )
                    call.respond(Token(loginRequest.userId, jwt))
                } catch (e: PtException) {
                    System.err.println("Request failed with managed exception: $e")
                    call.respond(HttpStatusCode.Unauthorized, "Login attempt failed")
                } catch (e: Throwable) {
                    val l = LoginRequest("root", "rootabega")
                    System.err.println("Request failed with uncaught exception: $e")
                    call.respond(HttpStatusCode.Unauthorized, ParsingCommons.klaxon.toJsonString(l))
                }
            }

            authenticate("auth-jwt") {


                get("users") {
                    callWithAuthAndResultHandling(call, depot) { auth, depot, _ ->
                        UserListResponse(depot.users.list(auth))
                    }
                }

                post("users") {
                    callWithAuthAndResultHandling(call, depot) { auth, depot, call ->
                        val req = call.receive<UserCreateRequest>()
                        depot.users.create(
                            auth, req.userId,
                            req.fullName,
                            req.email,
                            req.permittedActions,
                            req.password
                        )
                    }
                }

                get("users/{user}") {
                    callWithAuthAndResultHandling(call, depot) { auth, depot, call ->
                        depot.users.retrieveUser(auth, call.parameters["user"]!!)
                    }
                }

                put("users/{user}") {
                    callWithAuthAndResultHandling(call, depot) { auth, depot, call ->
                        val userId = call.parameters["user"]!!
                        val req = call.receive<UserUpdateRequest>()
                        when (req.kind) {
                            UserUpdateRequest.Kind.Grant ->
                                if (req.actions == null) {
                                    throw PtException(
                                        PtException.Kind.InvalidParameter,
                                        "A grant request must include a permitted action list"
                                    )
                                } else {
                                    depot.users.grantPermissions(auth, userId, req.actions!!)

                                }

                            UserUpdateRequest.Kind.Revoke ->
                                if (req.actions == null) {
                                    throw PtException(
                                        PtException.Kind.InvalidParameter,
                                        "A grant request must include a permitted action list"
                                    )
                                } else {
                                    depot.users.revokePermission(auth, userId, req.actions!!)
                                }

                            UserUpdateRequest.Kind.Reactivate ->
                                depot.users.reactivateUser(auth, userId)

                            UserUpdateRequest.Kind.Deactivate ->
                                depot.users.deactivateUser(auth, userId)

                            UserUpdateRequest.Kind.Password ->
                                if (req.password == null) {
                                    throw PtException(
                                        PtException.Kind.InvalidParameter,
                                        "A change password request must include a new password"
                                    )
                                } else {
                                    depot.users.updatePassword(auth, userId, req.password!!)
                                }
                        }
                    }
                }

                get("projects") {
                    callWithAuthAndResultHandling(call, depot) { auth, depot, _ ->
                        val pr = depot.projects.listProjects(auth)
                        ProjectListResponse(pr)
                    }
                }

                post("projects") {
                    callWithAuthAndResultHandling(call, depot) { auth, depot, call ->
                        val req = call.receive<ProjectCreateRequest>()
                        depot.projects.createProject(auth, req.name, req.description)
                    }
                }

                get("projects/{name}") {
                    callWithAuthAndResultHandling(call, depot) { auth, depot, call ->
                        val projectName = call.parameters["name"]!!
                        depot.projects.retrieveProject(auth, projectName)
                    }
                }

                get("projects/{project}/histories") {
                    callWithAuthAndResultHandling(call, depot) { auth, depot, call ->
                        val projectName = call.parameters["name"]!!
                        depot.histories.listHistories(auth, projectName)
                    }
                }

                post("projects/{project}/histories") {
                    callWithAuthAndResultHandling(call, depot) { auth, depot, call ->
                        val projectName = call.parameters["project"]!!
                        val req = call.receive<HistoryCreateRequest>()
                        depot.histories.createHistory(
                            auth, projectName, req.name, req.description,
                            req.parentHistory, req.step
                        )
                    }
                }

                get("projects/{project}/histories/{history}") {
                    callWithAuthAndResultHandling(call, depot) { auth, depot, call ->
                        val projectName = call.parameters["project"]!!
                        val historyName = call.parameters["history"]!!
                        depot.histories.retrieveHistory(auth, projectName, historyName)
                    }
                }

                get("projects/{project}/histories/{history}/steps") {
                    callWithAuthAndResultHandling(call, depot) { auth, depot, call ->
                        val projectName = call.parameters["project"]!!
                        val historyName = call.parameters["history"]!!
                        depot.histories.listHistorySteps(
                            auth, projectName,
                            historyName
                        )
                    }
                }

                get("projects/{project}/histories/{history}/changes") {
                    callWithAuthAndResultHandling(call, depot) { auth, depot, call ->
                        val project = call.parameters["project"]!!
                        val history = call.parameters["history"]!!
                        depot.changes.listChanges(auth, project, history)
                    }
                }

                get("projects/{project}/histories/{history}/changes/{change}") {
                    callWithAuthAndResultHandling(call, depot) { auth, depot, call ->
                        val project = call.parameters["project"]!!
                        val history = call.parameters["history"]!!
                        val change = call.parameters["change"]!!
                        depot.changes.retrieveChangeByName(auth, project, history, change)
                    }
                }


                get("projects/{project}/histories/{history}/changes/{change}/saves") {
                    callWithAuthAndResultHandling(call, depot) { auth, depot, call ->
                        val project = call.parameters["project"]!!
                        val history = call.parameters["history"]!!
                        val change = call.parameters["change"]!!
                        depot.changes.listSavePoints(auth, project, history, change)
                    }
                }

                get("artifacts/{artifactId}/versions/{versionId}") {
                    TODO()
                }


                get("projects/{project}/workspaces") {
                    callWithAuthAndResultHandling(call, depot) { auth, depot, call ->
                        val project = call.parameters["project"]!!
                        depot.workspaces.listWorkspaces(auth, project)
                    }
                }

                post("projects/{project}/workspaces") {
                    callWithAuthAndResultHandling(call, depot) { auth, depot, call ->
                        val project = call.parameters["project"]!!
                        val req = call.receive<WorkspaceCreateRequest>()
                        depot.workspaces.createWorkspace(auth, project, req.history, req.name, req.description)
                    }
                }

                get("projects/{project}/workspaces/{workspace}") {
                    callWithAuthAndResultHandling(call, depot) { auth, depot, call ->
                        val project = call.parameters["project"]!!
                        val workspace = call.parameters["workspace"]!!
                        depot.workspaces.retrieveWorkspace(auth, project, workspace)
                    }
                }

                get("projects/{project}/workspaces/{workspace}/utd") {
                    callWithAuthAndResultHandling(call, depot) { auth, depot, call ->
                        val project = call.parameters["project"]!!
                        val workspace = call.parameters["workspace"]!!
                        val ws = depot.workspaces.retrieveWorkspace(auth, project, workspace)
                        depot.workspaces.upToDate(auth, ws)
                    }
                }

                post("projects/{project}/workspaces/{workspace}/action/createChange") {
                    callWithAuthAndResultHandling(call, depot) { auth, depot, call ->
                        val project = call.parameters["project"]!!
                        val workspace = call.parameters["workspace"]!!
                        val req = call.receive<WorkspaceCreateChangeRequest>()
                        val ws = depot.workspaces.retrieveWorkspace(auth, project, workspace)
                        depot.workspaces.createChange(
                            auth, ws, req.name,
                            req.description
                        )
                    }
                }

                post("projects/{project}/workspaces/{workspace}/action/openChange/{change}") {
                    callWithAuthAndResultHandling(call, depot) { auth, depot, call ->
                        val project = call.parameters["project"]!!
                        val workspace = call.parameters["workspace"]!!
                        val change = call.parameters["change"]!!
                        val ws = depot.workspaces.retrieveWorkspace(auth, project, workspace)
                        depot.workspaces.openChange(auth, ws, ws.history, change)
                        ws
                    }
                }

                post("projects/{project}/workspaces/{workspace}/action/openHistory/{history}") {
                    callWithAuthAndResultHandling(call, depot) { auth, depot, call ->
                        val project = call.parameters["project"]!!
                        val workspace = call.parameters["workspace"]!!
                        val history = call.parameters["history"]!!
                        val ws = depot.workspaces.retrieveWorkspace(auth, project, workspace)
                        depot.workspaces.openHistory(auth, ws, history)
                        ws
                    }
                }

                get("projects/{project}/workspaces/{workspace}/paths") {
                    callWithAuthAndResultHandling(call, depot) { auth, depot, call ->
                        val project = call.parameters["project"]!!
                        val workspace = call.parameters["workspace"]!!
                        val ws = depot.workspaces.retrieveWorkspace(auth, project, workspace)
                        depot.workspaces.listPaths(auth, ws)
                    }
                }

                post("projects/{project}/workspaces/{workspace}/paths/{path...}") {
                    callWithAuthAndResultHandling(call, depot) { auth, depot, call ->
                        val project = call.parameters["project"]!!
                        val workspace = call.parameters["workspace"]!!
                        val path = call.parameters["path"]!!
                        val req = call.receive<WorkspaceAddFileRequest>()
                        val ws = depot.workspaces.retrieveWorkspace(auth, project, workspace)
                        depot.workspaces.addFile(
                            auth, ws, path, req.artifactType,
                            req.content
                        )
                        ws
                    }
                }

                post("projects/{project}/workspaces/{workspace}/action/move") {
                    callWithAuthAndResultHandling(call, depot) { auth, depot, call ->
                        val project = call.parameters["project"]!!
                        val workspace = call.parameters["workspace"]!!
                        val req = call.receive<WorkspaceMoveFileRequest>()
                        val ws = depot.workspaces.retrieveWorkspace(auth, project, workspace)
                        depot.workspaces.moveFile(
                            auth, ws,
                            req.pathBefore, req.pathAfter
                        )
                        ws
                    }
                }

                delete("projects/{project}/workspaces/{workspace}/paths/{path...}") {
                    callWithAuthAndResultHandling(call, depot) { auth, depot, call ->
                        val project = call.parameters["project"]!!
                        val workspace = call.parameters["workspace"]!!
                        val path = call.parameters["path"]!!
                        val ws = depot.workspaces.retrieveWorkspace(auth, project, workspace)
                        depot.workspaces.deleteFile(auth, ws, path).toList()
                    }
                }

                put("projects/{project}/workspaces/{workspace}/paths/{path...}") {
                    callWithAuthAndResultHandling(call, depot) { auth, depot, call ->
                        val project = call.parameters["project"]!!
                        val workspace = call.parameters["workspace"]!!
                        val path = call.parameters["path"]!!
                        val contents = call.receiveText()
                        val ws = depot.workspaces.retrieveWorkspace(auth, project, workspace)
                        depot.workspaces.modifyFile(auth, ws, path, contents)
                        ws
                    }
                }

                get("projects/{project}/workspaces/{workspace}/paths/{path...}") {
                    callWithAuthAndResultHandling(call, depot) { auth, depot, call ->
                        val project = call.parameters["project"]!!
                        val workspace = call.parameters["workspace"]!!
                        val path = call.parameters["path"]!!
                        val ws = depot.workspaces.retrieveWorkspace(auth, project, workspace)
                        depot.workspaces.getFileContents(auth, ws, path)
                    }
                }

                get("projects/{project}/workspaces/{workspace}/multi") {
                    callWithAuthAndResultHandling(call, depot) { auth, depot, call ->
                        val project = call.parameters["project"]!!
                        val workspace = call.parameters["workspace"]!!
                        val ws = depot.workspaces.retrieveWorkspace(auth, project, workspace)
                        val req = call.receive<WorkspaceGetMultiRequest>()
                        req.paths.map { path -> depot.workspaces.getFileContents(auth, ws, path) }
                    }
                }

                post("projects/{project}/workspaces/{workspace}/action/save") {
                    callWithAuthAndResultHandling(call, depot) { auth, depot, call ->
                        val project = call.parameters["project"]!!
                        val workspace = call.parameters["workspace"]!!
                        val ws = depot.workspaces.retrieveWorkspace(auth, project, workspace)
                        val req = call.receive<WorkspaceSaveRequest>()
                        depot.workspaces.save(auth, ws, req.description, req.resolvedConflicts)
                    }
                }

                post("projects/{project}/workspaces/{workspace}/action/deliver") {
                    callWithAuthAndResultHandling(call, depot) { auth, depot, call ->
                        val project = call.parameters["project"]!!
                        val workspace = call.parameters["workspace"]!!
                        val ws = depot.workspaces.retrieveWorkspace(auth, project, workspace)
                        val req = call.receive<WorkspaceDeliverRequest>()
                        depot.workspaces.deliver(auth, ws, req.description)
                        ws
                    }
                }

                post("projects/{project}/workspaces/{workspace}/action/update") {
                    callWithAuthAndResultHandling(call, depot) { auth, depot, call ->
                        val project = call.parameters["project"]!!
                        val workspace = call.parameters["workspace"]!!
                        val ws = depot.workspaces.retrieveWorkspace(auth, project, workspace)
                        depot.workspaces.update(auth, ws)
                        ws
                    }
                }

                post("projects/{project}/workspaces/{workspace}/action/integrate") {
                    callWithAuthAndResultHandling(call, depot) { auth, depot, call ->
                        val project = call.parameters["project"]!!
                        val workspace = call.parameters["workspace"]!!
                        val ws = depot.workspaces.retrieveWorkspace(auth, project, workspace)
                        val req = call.receive<WorkspaceIntegrateRequest>()
                        depot.workspaces.integrate(auth, ws, req.fromVersion, req.toVersion)
                        ws
                    }
                }

                delete("projects/{project}/workspaces/{workspace}") {
                    callWithAuthAndResultHandling(call, depot) { auth, depot, call ->
                        val project = call.parameters["project"]!!
                        val workspace = call.parameters["workspace"]!!
                        val ws = depot.workspaces.retrieveWorkspace(auth, project, workspace)
                        depot.workspaces.deleteWorkspace(auth, ws)
                        ws
                    }
                }

                post("projects/{project}/workspaces/{workspace}/action/abandon") {
                    callWithAuthAndResultHandling(call, depot) { auth, depot, call ->
                        val project = call.parameters["project"]!!
                        val workspace = call.parameters["workspace"]!!
                        val reason = call.receiveText()
                        val ws = depot.workspaces.retrieveWorkspace(auth, project, workspace)
                        depot.workspaces.abandonChanges(auth, ws, reason)
                        ws
                    }
                }
            }
        }
    }
}