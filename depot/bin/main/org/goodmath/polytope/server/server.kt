package org.goodmath.polytope.server

import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.Resources
import io.ktor.server.resources.get
import io.ktor.server.resources.patch
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.goodmath.polytope.depot.stashes.Artifact
import org.goodmath.polytope.depot.stashes.ArtifactVersion
import org.goodmath.polytope.depot.util.Id
import java.util.*

data class CreateWorkspaceRequestBody(
    val name: String,
    val description: String,
    val history: String?)

data class CreateChangeRequestBody(
    val description: String
)

data class WorkspaceFileBody(
    val id: Id<Artifact>,
    val version: Id<ArtifactVersion>,
    val artifactType: String,
    val contents: String,
)

data class RenamePathBody(
    val pathBefore: String,
    val pathAfter: String
)
@Resource("/projects/{project}")
data class ProjectRequest(val projectName: String) {
    // get - get list of projects
    // post - create a new project

    @Resource("workspaces")
    data class WorkspaceRequest(val prj: ProjectRequest) {
        // Get to /projects/prj/workspaces will retrieve a list of
        // workspace descriptors for the workspaces in the project.


        @Resource("{name}")
        data class NamedWorkspaceRequest(val projectRequest: ProjectRequest, val wsName: String) {
            // Post to /projects/{prj}/workspace/{ws} will create a workspace named ws
            // in the project prj, initialized to point at the current baseline for the
            // "main" history. The body of the request should be
            // a description to use for the newly created workspace.

            // Get to  /projects/{prj}/workspace/{ws} will retrieve the full workspace record
            // for a specific workspace.

            @Resource("ops")
            data class WorkspaceOpRequest(val workspaceRequest: WorkspaceRequest) {
                // The ops resource allows a range of operations to be performed on the
                // workspace using the PUT method. The operation is selectedby the "op"
                // parameter, and then additional details about the request will be supplied
                // via the body.

                @Resource("create_change/{changeName}")
                data class CreateChangeRequest(val wsOpRequest: WorkspaceOpRequest, val changeName: String)

                @Resource("select_change/{changeName}")
                data class SelectChangeRequest(val wsOpRequest: WorkspaceOpRequest, val changeName: String)

                @Resource("save")
                data class SaveWorkspaceRequest(val wsOpRequest: WorkspaceRequest)

                @Resource("deliver")
                data class DeliverRequest(val wsOpRequest: WorkspaceOpRequest)
                // Body is a description of the change.

                @Resource("update")
                data class UpdateWorkspaceRequest(val wsOpRequest: WorkspaceOpRequest) {
                }

                @Resource("integrate")
                data class IntegrateWorkspaceRequest(val wsOpRequest: WorkspaceOpRequest) {
                    // Body is a specifier of the change to integrate.
                }

                @Resource("rename")
                data class RenamePathRequest(val wsOpRequest: WorkspaceOpRequest) {
                    // Only POST.
                }

                @Resource("mkdir/{path...}")
                data class MakeDirRequest(
                    val wsOpRequest: WorkspaceOpRequest,
                    val path: String
                )
            }

            @Resource("files")
            data class WorkspaceFilesRequest(
                val wsRequest: WorkspaceRequest
            ) {
                @Resource("{path...}")
                data class WorkspaceFilePathRequest(
                    val wsFilesReqval: WorkspaceFilesRequest,
                    val path: String
                ) {
                    // GET returns the contenst of the path.
                    // PUT updates the contents of the path.
                    // POST creates a new file at the path.
                    // DELETE deletes the the path.
                }
            }

            @Resource("files/{path...}")
            data class FileRequest(val workspace: WorkspaceRequest, val path: String)
        }
    }
}



@Resource("users/{user}")
data class UserRequest(val user: String) {
    // GET gets a user, POST creates a user, PUT updates.
    // TODO: specify PUT body for user record updates.
}





