
import json
import mimetypes
import falcon
from falcon_auth import JWTAuthBackend, FalconAuthMiddleware

from polytope.common.error import PtException
from polytope.common.stashable.user import AuthenticatedUser
from polytope.depot.config import Config
from polytope.depot.depot import Depot
from polytope.depot.server.login import Authenticator
from polytope.depot.stashes.user_stash import UserStash


class Server:
    def __init__(self, config: Config) -> None:
        self.depot = Depot(config)
        self.auth_backend = JWTAuthBackend(Authenticator(self.depot.user_stash))
        self.auth_middleware = FalconAuthMiddleware(self.auth_backend,
                                                    exempt_routes=['/login'])

        self.app = falcon.App(middleware=[self.auth_middleware])
        self.app.req_options.default_media_type = "application/json"


class LoginResource:
    def __init__(self, user_stash: UserStash) -> None:
        self.user_stash = user_stash

    def on_post(self, req: falcon.Request, resp: falcon.Response):
        doc = json.load(req.bounded_stream)
        user = doc["user_id"]
        code = doc["code"]
        nonce = doc["nonce"]
        try:
            auth = self.user_stash.authenticate(user, code, nonce)
            resp.text = json.dumps(auth.to_dict())
        except PtException as e:
            resp.text = str(e)
            resp.status_code = e.kind.to_status_code().value


# Resources:
# users (get, post)
# users/{user} (get, put)

# projects (get, post)
# projects/{name} (get)
# projects/{project}/histories (get, post)
# projects/{project}/histories/{history} (get)
# projects/{project}/histories/{history}/steps (get)
# projects/{project}/histories/{history}/changes (get)
# projects/{project}/histories/{history}/changes/{change} (get, delete)
# projects/{project}/histories/{history}/changes/{change}/saves (get)
# projects/{project}/workspaces (get, post)
# projects/{project}/workspaces/{workspace} (get, delete)
# projects/{project}/workspaces/{workspace}/utd (get)
# projects/{project}/workspaces/{workspace}/action/createChange (post)
# projects/{project}/workspaces/{workspace}/action/openChange/{change} (post)
# projects/{project}/workspaces/{workspace}/action/openHistory/{history} (post)
# projects/{project}/workspaces/{workspace}/paths (get)
# projects/{project}/workspaces/{workspace}/paths/{path...} (get, post, post, delete)
# projects/{project}/workspaces/{workspace}/action/move" (post)
# projects/{project}/workspaces/{workspace}/multi (get)
# projects/{project}/workspaces/{workspace}/action/save (post)
# projects/{project}/workspaces/{workspace}/action/deliver (post)
# projects/{project}/workspaces/{workspace}/action/update (post)
# projects/{project}/workspaces/{workspace}/action/integrateChange (post)
# projects/{project}/workspaces/{workspace}/action/integrateDiff" (post)
# projects/{project}/workspaces/{workspace}/action/reset (post)
# artifacts/{artifactId} (get)
# artifacts/{artifactId}/versions (get)
# artifacts/{artifactid}/versions/{versionId} (get)

    #     route("/polytope/v0") {
    #         // login(userId, password): token
    #         post("login") {
    #             try {
    #                 val loginRequest = call.receive < LoginRequest > ()
    #                 val auth = depot.users.authenticate(
    #                     loginRequest.userId,
    #                     loginRequest.password
    #                 )
    #                 val(token, exp) = depot.users.generateAuthToken(auth)
    #                 val jwt = JWT.create()
    #                     .withAudience(this@configureRouting.environment.config.property("jwt.audience").getString())
    #                     .withIssuer(this@configureRouting.environment.config.property("jwt.issuer").getString())
    #                     .withClaim("userId", loginRequest.userId)
    #                     .withClaim("token", token)
    #                     .withExpiresAt(Date(exp))
    #                     .sign(
    #                         Algorithm.HMAC256(
    #                             this@configureRouting.environment.config.property(
    #                                 "jwt.secret").getString()
    #                         )
    #                     )
    #                 call.respond(Token(loginRequest.userId, jwt))
    #             } catch(e: PtException) {
    #                 System.err.println("Request failed with managed exception: $e")
    #                 call.respond(HttpStatusCode.Unauthorized, "Login attempt failed")
    #             } catch(e: Throwable) {
    #                 val l = LoginRequest("root", "rootabega")
    #                 System.err.println("Request failed with uncaught exception: $e")
    #                 call.respond(HttpStatusCode.Unauthorized, ParsingCommons.klaxon.toJsonString(l))
    #             }
    #         }

    #         authenticate("auth-jwt") {

    #             get("users") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, _ ->
    #                     UserListResponse(depot.users.list(auth))
    #                 }
    #             }

    #             post("users") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, call ->
    #                     val req = call.receive < UserCreateRequest > ()
    #                     depot.users.create(
    #                         auth, req.userId,
    #                         req.fullName,
    #                         req.email,
    #                         req.permittedActions,
    #                         req.password
    #                     )
    #                 }
    #             }

    #             get("users/{user}") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, call ->
    #                     depot.users.retrieveUser(auth, call.parameters["user"]!!)
    #                 }
    #             }

    #             put("users/{user}") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, call ->
    #                     val userId = call.parameters["user"]!!
    #                     val req = call.receive < UserUpdateRequest > ()
    #                     when(req.kind) {
    #                         UserUpdateRequest.Kind.Grant ->
    #                             if (req.actions == null) {
    #                                 throw PtException(
    #                                     PtException.Kind.InvalidParameter,
    #                                     "A grant request must include a permitted action list"
    #                                 )
    #                             } else {
    #                                 depot.users.grantPermissions(auth, userId, req.actions!!)

    #                             }

    #                         UserUpdateRequest.Kind.Revoke ->
    #                             if (req.actions == null) {
    #                                 throw PtException(
    #                                     PtException.Kind.InvalidParameter,
    #                                     "A grant request must include a permitted action list"
    #                                 )
    #                             } else {
    #                                 depot.users.revokePermission(auth, userId, req.actions!!)
    #                             }

    #                         UserUpdateRequest.Kind.Reactivate ->
    #                             depot.users.reactivateUser(auth, userId)

    #                         UserUpdateRequest.Kind.Deactivate ->
    #                             depot.users.deactivateUser(auth, userId)

    #                         UserUpdateRequest.Kind.Password ->
    #                             if (req.password == null) {
    #                                 throw PtException(
    #                                     PtException.Kind.InvalidParameter,
    #                                     "A change password request must include a new password"
    #                                 )
    #                             } else {
    #                                 depot.users.updatePassword(auth, userId, req.password!!)
    #                             }
    #                     }
    #                 }
    #             }

    #             get("projects") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, _ ->
    #                     val pr = depot.projects.listProjects(auth)
    #                     ProjectListResponse(pr)
    #                 }
    #             }

    #             post("projects") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, call ->
    #                     val req = call.receive < ProjectCreateRequest > ()
    #                     depot.projects.createProject(auth, req.name, req.description)
    #                 }
    #             }

    #             get("projects/{name}") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, call ->
    #                     val projectName = call.parameters["name"]!!
    #                     depot.projects.retrieveProject(auth, projectName)
    #                 }
    #             }

    #             get("projects/{project}/histories") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, call ->
    #                     val projectName = call.parameters["name"]!!
    #                     HistoryListResponse(depot.histories.listHistories(auth, projectName))
    #                 }
    #             }

    #             post("projects/{project}/histories") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, call ->
    #                     val projectName = call.parameters["project"]!!
    #                     val req = call.receive < HistoryCreateRequest > ()
    #                     depot.histories.createHistory(
    #                         auth, projectName, req.name, req.description,
    #                         req.parentHistory, req.step
    #                     )
    #                 }
    #             }

    #             get("projects/{project}/histories/{history}") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, call ->
    #                     val projectName = call.parameters["project"]!!
    #                     val historyName = call.parameters["history"]!!
    #                     depot.histories.retrieveHistory(auth, projectName, historyName)
    #                 }
    #             }

    #             get("projects/{project}/histories/{history}/steps") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, call ->
    #                     val projectName = call.parameters["project"]!!
    #                     val historyName = call.parameters["history"]!!
    #                     HistoryStepsResponse(depot.histories.listHistorySteps(
    #                         auth, projectName,
    #                         historyName
    #                     ))
    #                 }
    #             }

    #             get("projects/{project}/histories/{history}/changes") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, call ->
    #                     val showStr = call.request.queryParameters["show"]?: "open"
    #                     val show = when(showStr) {
    #                         "aborted", "all" -> ChangeStatus.Aborted
    #                         "closed" -> ChangeStatus.Closed
    #                             "open" -> ChangeStatus.Open
    #                         else ->
    #                             throw PtException(PtException.Kind.InvalidParameter,
    #                                 "status for a change list must be one of ('all', 'aborted', 'closed', 'open')")
    #                     }
    #                     val project = call.parameters["project"]!!
    #                     val history = call.parameters["history"]!!
    #                     ChangeListResponse(depot.changes.listChanges(auth, project, history, show))
    #                 }
    #             }

    #             get("projects/{project}/histories/{history}/changes/{change}") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, call ->
    #                     val project = call.parameters["project"]!!
    #                     val history = call.parameters["history"]!!
    #                     val change = call.parameters["change"]!!
    #                     depot.changes.retrieveChangeByName(auth, project, history, change)
    #                 }
    #             }

    #             delete("projects/{project}/histories/{history}/changes/{change}") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, call ->
    #                     val project = call.parameters["project"]!!
    #                     val history = call.parameters["history"]!!
    #                     val change = call.parameters["change"]!!
    #                     val theChange = depot.changes.retrieveChangeByName(auth, project, history, change).copy(status=ChangeStatus.Aborted)
    #                     depot.changes.updateChangeStatus(
    #                         auth, project, history, change, ChangeStatus.Aborted)
    #                     theChange
    #                 }
    #             }

    #             get("projects/{project}/histories/{history}/changes/{change}/saves") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, call ->
    #                     val project = call.parameters["project"]!!
    #                     val history = call.parameters["history"]!!
    #                     val change = call.parameters["change"]!!
    #                     SavesListResponse(depot.changes.listSavePoints(
    #                         auth, project, history, change))
    #                 }
    #             }

    #             get("artifacts/{artifactId}/versions/{versionId}") {
    #                 TODO()
    #             }

    #             get("projects/{project}/workspaces") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, call ->
    #                     val project = call.parameters["project"]!!
    #                     WorkspaceListResponse(depot.workspaces.listWorkspaces(auth, project))
    #                 }
    #             }

    #             post("projects/{project}/workspaces") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, call ->
    #                     val project = call.parameters["project"]!!
    #                     val req = call.receive < WorkspaceCreateRequest > ()
    #                     depot.workspaces.createWorkspace(
    #                         auth, project, req.history, req.name, req.description)
    #                 }
    #             }

    #             get("projects/{project}/workspaces/{workspace}") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, call ->
    #                     val project = call.parameters["project"]!!
    #                     val workspace = call.parameters["workspace"]!!
    #                     depot.workspaces.retrieveWorkspace(auth, project, workspace)
    #                 }
    #             }

    #             get("projects/{project}/workspaces/{workspace}/utd") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, call ->
    #                     val project = call.parameters["project"]!!
    #                     val workspace = call.parameters["workspace"]!!
    #                     val ws = depot.workspaces.retrieveWorkspace(auth, project, workspace)
    #                     depot.workspaces.upToDate(auth, ws)
    #                 }
    #             }

    #             post("projects/{project}/workspaces/{workspace}/action/createChange") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, call ->
    #                     val project = call.parameters["project"]!!
    #                     val workspace = call.parameters["workspace"]!!
    #                     val req = call.receive < WorkspaceCreateChangeRequest > ()
    #                     val ws = depot.workspaces.retrieveWorkspace(auth, project, workspace)
    #                     depot.workspaces.createChange(
    #                         auth, ws, req.history, req.changeName,
    #                         req.description
    #                     )
    #                 }
    #             }

    #             post("projects/{project}/workspaces/{workspace}/action/openChange/{change}") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, call ->
    #                     val project = call.parameters["project"]!!
    #                     val workspace = call.parameters["workspace"]!!
    #                     val change = call.parameters["change"]!!
    #                     val ws = depot.workspaces.retrieveWorkspace(auth, project, workspace)
    #                     val history = call.request.queryParameters["history"]?: ws.history
    #                     depot.workspaces.openChange(auth, ws, history, change)
    #                     ws
    #                 }
    #             }

    #             post("projects/{project}/workspaces/{workspace}/action/openHistory/{history}") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, call ->
    #                     val project = call.parameters["project"]!!
    #                     val workspace = call.parameters["workspace"]!!
    #                     val history = call.parameters["history"]!!
    #                     val ws = depot.workspaces.retrieveWorkspace(auth, project, workspace)
    #                     depot.workspaces.openHistory(auth, ws, history)
    #                     ws
    #                 }
    #             }

    #             get("projects/{project}/workspaces/{workspace}/paths") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, call ->
    #                     val project = call.parameters["project"]!!
    #                     val workspace = call.parameters["workspace"]!!
    #                     val ws = depot.workspaces.retrieveWorkspace(auth, project, workspace)
    #                     PathListResponse(depot.workspaces.listPaths(auth, ws))
    #                 }
    #             }

    #             post("projects/{project}/workspaces/{workspace}/paths/{path...}") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, call ->
    #                     val project = call.parameters["project"]!!
    #                     val workspace = call.parameters["workspace"]!!
    #                     val path = call.parameters["path"]!!
    #                     val req = call.receive < WorkspaceAddFileRequest > ()
    #                     val ws = depot.workspaces.retrieveWorkspace(auth, project, workspace)
    #                     depot.workspaces.addFile(
    #                         auth, ws, path, req.artifactType,
    #                         req.content
    #                     )
    #                     ws
    #                 }
    #             }

    #             post("projects/{project}/workspaces/{workspace}/action/move") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, call ->
    #                     val project = call.parameters["project"]!!
    #                     val workspace = call.parameters["workspace"]!!
    #                     val req = call.receive < WorkspaceMoveFileRequest > ()
    #                     val ws = depot.workspaces.retrieveWorkspace(auth, project, workspace)
    #                     depot.workspaces.moveFile(
    #                         auth, ws,
    #                         req.pathBefore, req.pathAfter
    #                     )
    #                     ws
    #                 }
    #             }

    #             delete("projects/{project}/workspaces/{workspace}/paths/{path...}") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, call ->
    #                     val project = call.parameters["project"]!!
    #                     val workspace = call.parameters["workspace"]!!
    #                     val path = call.parameters["path"]!!
    #                     val ws = depot.workspaces.retrieveWorkspace(auth, project, workspace)
    #                     depot.workspaces.deleteFile(auth, ws, path).toList()
    #                 }
    #             }

    #             put("projects/{project}/workspaces/{workspace}/paths/{path...}") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, call ->
    #                     val project = call.parameters["project"]!!
    #                     val workspace = call.parameters["workspace"]!!
    #                     val path = call.parameters["path"]!!
    #                     val contents = call.receiveText()
    #                     val ws = depot.workspaces.retrieveWorkspace(auth, project, workspace)
    #                     depot.workspaces.modifyFile(auth, ws, path, contents)
    #                     ws
    #                 }
    #             }

    #             get("projects/{project}/workspaces/{workspace}/paths/{path...}") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, call ->
    #                     val project = call.parameters["project"]!!
    #                     val workspace = call.parameters["workspace"]!!
    #                     val path = call.parameters["path"]!!
    #                     val ws = depot.workspaces.retrieveWorkspace(auth, project, workspace)
    #                     depot.workspaces.getFileContents(auth, ws, path)
    #                 }
    #             }

    #             get("projects/{project}/workspaces/{workspace}/multi") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, call ->
    #                     val project = call.parameters["project"]!!
    #                     val workspace = call.parameters["workspace"]!!
    #                     val ws = depot.workspaces.retrieveWorkspace(auth, project, workspace)
    #                     val req = call.receive < WorkspaceGetMultiRequest > ()
    #                     req.paths.map {path -> depot.workspaces.getFileContents(auth, ws, path)}
    #                 }
    #             }

    #             post("projects/{project}/workspaces/{workspace}/action/save") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, call ->
    #                     val project = call.parameters["project"]!!
    #                     val workspace = call.parameters["workspace"]!!
    #                     val ws = depot.workspaces.retrieveWorkspace(auth, project, workspace)
    #                     val req = call.receive < WorkspaceSaveRequest > ()
    #                     depot.workspaces.save(auth, ws, req.description, req.resolvedConflicts)
    #                 }
    #             }

    #             post("projects/{project}/workspaces/{workspace}/action/deliver") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, call ->
    #                     val project = call.parameters["project"]!!
    #                     val workspace = call.parameters["workspace"]!!
    #                     val ws = depot.workspaces.retrieveWorkspace(auth, project, workspace)
    #                     val req = call.receive < WorkspaceDeliverRequest > ()
    #                     depot.workspaces.deliver(auth, ws, req.description)
    #                     ws
    #                 }
    #             }

    #             post("projects/{project}/workspaces/{workspace}/action/update") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, call ->
    #                     val project = call.parameters["project"]!!
    #                     val workspace = call.parameters["workspace"]!!
    #                     val ws = depot.workspaces.retrieveWorkspace(auth, project, workspace)
    #                     depot.workspaces.update(auth, ws)
    #                     ws
    #                 }
    #             }

    #             post("projects/{project}/workspaces/{workspace}/action/integrateChange") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, call ->
    #                     val project = call.parameters["project"]!!
    #                     val workspace = call.parameters["workspace"]!!
    #                     val ws = depot.workspaces.retrieveWorkspace(auth, project, workspace)
    #                     val req = call.receive < WorkspaceIntegrateChangeRequest > ()
    #                     depot.workspaces.integrateChange(
    #                         auth, ws, req.sourceHistory, req.changeName)
    #                     ws
    #                 }
    #             }

    #             post("projects/{project}/workspaces/{workspace}/action/integrateDiff") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, call ->
    #                     val project = call.parameters["project"]!!
    #                     val workspace = call.parameters["workspace"]!!
    #                     val ws = depot.workspaces.retrieveWorkspace(auth, project, workspace)
    #                     val req = call.receive < WorkspaceIntegrateDiffRequest > ()
    #                     depot.workspaces.integrateDiff(auth, ws, req.fromVersion, req.toVersion)
    #                     ws
    #                 }
    #             }

    #             delete("projects/{project}/workspaces/{workspace}") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, call ->
    #                     val project = call.parameters["project"]!!
    #                     val workspace = call.parameters["workspace"]!!
    #                     val ws = depot.workspaces.retrieveWorkspace(auth, project, workspace)
    #                     depot.workspaces.deleteWorkspace(auth, ws)
    #                     ws
    #                 }
    #             }

    #             post("projects/{project}/workspaces/{workspace}/action/reset") {
    #                 callWithAuthAndResultHandling(call, depot) {auth, depot, call ->
    #                     val project = call.parameters["project"]!!
    #                     val workspace = call.parameters["workspace"]!!
    #                     val req = call.receive < WorkspaceResetRequest > ()
    #                     val ws = depot.workspaces.retrieveWorkspace(auth, project, workspace)
    #                     depot.workspaces.abandonChanges(auth, ws, req.reason, req.stepIndex)
    #                     ws
    #                 }
    #             }
    #         }
    #     }
    # }
