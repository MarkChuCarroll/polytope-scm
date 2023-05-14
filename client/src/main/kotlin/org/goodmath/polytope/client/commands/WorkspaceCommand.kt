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
package org.goodmath.polytope.client.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.coroutines.runBlocking
import org.goodmath.polytope.client.api.RestApiClient
import org.goodmath.polytope.client.workspace.ClientWorkspace
import org.goodmath.polytope.common.PtException
import kotlin.io.path.Path
import kotlin.io.path.div

class WorkspaceCommand : PolytopeCommandBase("ws", help = "Manipulate polytope workspaces.") {
    override fun run() {

    }

    companion object {
        fun getCommand(): CliktCommand =
            WorkspaceCommand()
                .subcommands(
                    WorkspaceListCommand(),
                    WorkspaceGetCommand(),
                    WorkspaceCreateCommand(),
                    WorkspaceOpenCommand(),
                    WorkspaceSaveCommand(),
                    WorkspaceUpdateCommand(),
                    WorkspaceDeleteCommand(),
                    WorkspaceResetCommand()
                )
    }

}

class WorkspaceListCommand : PolytopeCommandBase("list", help = "List workspaces in a project.") {
    private val projectArg: String? by option("-p", "--project", help = "the project containing the workspace")
    private val usernameArg: String? by option("-u", "--user", help = "the userId of the user performing the command")
    private val serverUrlArg: String? by option("-s", "--server", help = "the URL of the polytope server")
    private val format: String by option("-f", "--format", help = "the output format")
        .choice("text", "json")
        .default("text")

    override fun run() {
        handleCommandErrors("ws", "list") {
            val ws = loadWorkspace()
            val userId = usernameArg ?: requireUserId()
            val serverUrl = serverUrlArg ?: requireServerUrl()
            val project = projectArg ?: ws?.project ?: throw PtException(
                PtException.Kind.UserError,
                "You must either supply a project name, or run this command in a workspace"
            )
            val apiClient = ws?.apiClient ?: RestApiClient(serverUrl, userId, getOrPromptForPassword())
            val workspaces = runBlocking {
                apiClient.workspacesList(project)
            }
            if (format == "text") {
                for (w in workspaces.workspaces) {
                    echo(w.render())
                    echo("----")
                }
            } else {
                echo(prettyJson(workspaces))
            }
        }
    }
}

class WorkspaceGetCommand : PolytopeCommandBase("get", help = "Retrieve and view information about a workspace.") {
    private val projectArg: String? by option("-p", "--project", help = "the project containing the workspace")
    private val workspaceName: String? by option("-w", "--ws", help = "the name of the workspace")
    private val usernameArg: String? by option("-u", "--user", help = "the userId of the user performing the command")
    private val serverUrlArg: String? by option("-s", "--server", help = "the URL of the polytope server")
    private val format: String by option("-f", "--format", help = "the output format")
        .choice("text", "json")
        .default("text")

    override fun run() {
        val ws = loadWorkspace()
        val userId = usernameArg ?: requireUserId()
        val serverUrl = serverUrlArg ?: requireServerUrl()
        val project = projectArg ?: ws?.project ?: throw PtException(
            PtException.Kind.UserError,
            "You must either supply a project name, or run this command in a workspace"
        )
        val wsName = workspaceName ?: ws?.name ?: throw PtException(
            PtException.Kind.UserError,
            "You must either supply a workspace name, or run this command in a workspace"
        )
        val apiClient = ws?.apiClient ?: RestApiClient(serverUrl, userId, getOrPromptForPassword())
        val workspace = runBlocking {
            apiClient.workspaceGet(project, wsName)
        }
        if (format == "text") {
            echo(workspace.render())
        } else {
            echo(prettyJson(workspace))
        }
    }
}

class WorkspaceCreateCommand : PolytopeCommandBase("create", help = "Create a new workspace for a project.") {
    private val projectOpt: String by option(
        "-p",
        "--project",
        help = "The project containing the workspace"
    ).required()
    private val workspaceName: String by option("--ws", help = "The name of the workspace").required()
    private val description: String by option(
        "-d",
        "--description",
        help = "A description of the new workspace"
    ).required()
    private val location: String? by option("-l", "--location", help = "The location of the new workspace")
    private val usernameArg: String? by option("-u", "--user", help = "The userId of the user performing the command")
    private val serverUrlArg: String? by option("-s", "--server", help = "The URL of the polytope server")
    override fun run() {
        val cfg = requireClientConfig()
        val userId = usernameArg ?: requireUserId()
        val serverUrl = serverUrlArg ?: requireServerUrl()
        val apiClient = RestApiClient(serverUrl, userId, getOrPromptForPassword())
        val wsDir = location?.let { Path(it) } ?: (Path(".") / workspaceName)
        val clientConfig = cfg.copy(
            userId = userId, serverUrl = serverUrl, wsName = workspaceName,
            wsPath = wsDir
        )

        runBlocking {
            val ws = apiClient.workspaceCreate(projectOpt, "main", workspaceName, description)
            ClientWorkspace.createWorkspace(clientConfig, ws)
        }
        echo("Workspace created")
    }
}

class WorkspaceOpenCommand : PolytopeCommandBase("open", help = "Open an existing workspace in this client.") {
    private val projectOpt: String by option(
        "-p",
        "--project",
        help = "the project containing the workspace"
    ).required()
    private val workspaceName: String by option("--ws", help = "the name of the workspace").required()
    private val location: String? by option("-l", "--location", help = "the location of the new workspace")
    private val usernameArg: String? by option("-u", "--user", help = "the userId of the user performing the command")
    private val serverUrlArg: String? by option("-s", "--server", help = "the URL of the polytope server")
    override fun run() {
        val cfg = requireClientConfig()
        val userId = usernameArg ?: requireUserId()
        val serverUrl = serverUrlArg ?: requireServerUrl()
        val apiClient = RestApiClient(serverUrl, userId, getOrPromptForPassword())
        val wsDir = location?.let { Path(it) } ?: (Path(".") / workspaceName)
        val clientConfig = cfg.copy(
            userId = userId, serverUrl = serverUrl, wsName = workspaceName,
            wsPath = wsDir
        )

        runBlocking {
            val ws = apiClient.workspaceGet(projectOpt, workspaceName)
            ClientWorkspace.createWorkspace(clientConfig, ws)
        }
        echo("Workspace opened and local space created")
    }
}

class WorkspaceSaveCommand : PolytopeCommandBase("save", "Save changes in this workspace to the server.") {
    private val description: String by option(
        "-d",
        "--description",
        help = "a brief description of the changes being saved"
    ).required()
    private val resolved: List<String> by option(
        "-r",
        "--resolve",
        help = "the ID of a conflict which was resolved"
    ).multiple()

    override fun run() {
        handleCommandErrors("workspace", "save") {
            val ws = requireWorkspace()
            runBlocking {
                ws.save(description, resolved)
            }
            echo("Workspace changes saved.")
        }
    }
}

class WorkspaceUpdateCommand : PolytopeCommandBase("update", "update the workspace from its parent history") {
    override fun run() {
        handleCommandErrors("workspace", "update") {
            val ws = requireWorkspace()
            val updated = runBlocking {
                val updated = ws.apiClient.workspaceUpdate(ws.project, ws.name)
                ws.populate(updated)
                updated
            }
            if (updated.conflicts.isNotEmpty()) {
                echo("Update resulted in conflicts:", err = true)
                for (c in updated.conflicts) {
                    echo(c.render(0), err = true)
                    echo("----", err = true)
                }
            }
        }
    }
}

class WorkspaceResetCommand : PolytopeCommandBase(
    help = "Reset the workspace to a prior savepoint, abandoning unsaved changes.",
    name = "reset"
) {
    private val reason: String by option(
        "-r",
        "--reason",
        help = "the reason the workspace changes are being abandoned"
    ).required()
    private val stepIndex: Int? by option("-v", "--step", help = "the step index within the change to reset to").int()

    override fun run() {
        handleCommandErrors("workspace", "reset") {
            val ws = requireWorkspace()
            runBlocking {
                ws.reset(reason, stepIndex)
            }
            echo("Workspace reset")
        }

    }
}

class WorkspaceDeleteCommand : PolytopeCommandBase(
    help = "Delete a workspace.",
    name = "delete"
) {
    private val project: String by option(
        "-p",
        "--project",
        help = "the name of the project containing the workspace"
    ).required()
    private val ws: String by option("-w", "--ws", help = "the name of the workspace to delete").required()
    private val asUser: String? by option(
        "-a",
        "--acting-user",
        help = "the userID of the user performing this operation"
    )
    private val serverUrl: String? by option("-s", "--server", help = "the URL of the polytope server")

    override fun run() {
        handleCommandErrors("workspace", "delete") {
            val apiClient = RestApiClient(
                serverUrl ?: requireServerUrl(),
                asUser ?: requireUserId(),
                getOrPromptForPassword()
            )
            runBlocking {
                apiClient.workspaceDelete(project, ws)
            }
            echo("Workspace $project::$ws deleted")
        }
    }
}