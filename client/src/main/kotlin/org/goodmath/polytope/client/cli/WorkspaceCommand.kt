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
package org.goodmath.polytope.client.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.file
import java.io.File

class WorkspaceCommand: PolytopeCommandBase("workspace", help="Work with workspaces") {
    override fun run() {

    }

    companion object {
        fun getCommand(): CliktCommand =
            WorkspaceCommand()
                .subcommands(
                    ListWorkspacesCommand(), GetWorkspaceCommand(), CreateWorkspaceCommand(),
                    OpenWorkspaceCommand(),
                    WorkspaceSaveCommand(), WorkspaceDeliverCommand(),
                    WorkspaceUpdateCommand(), WorkspaceIntegrateCommand(), WorkspaceDeleteCommand(),
                    WorkspaceAbandonCommand()
                )
    }

}

class ListWorkspacesCommand: PolytopeCommandBase("list", help="List workspaces in a project") {
    val project: String by option("--project", help="The project containing the workspace").required()

    override fun run() {

    }
}

class GetWorkspaceCommand: PolytopeCommandBase("get", help="Retrieve information about a specific workspace") {
    val project: String by option("--project", help="The project containing the workspace").required()
    val workspaceName: String by option("--workspace", help="The name of the workspace").required()
    override fun run() {

    }
}

class CreateWorkspaceCommand: PolytopeCommandBase("create", help="Create a new workspace") {
    val project: String by option("--project", help="The project containing the workspace").required()
    val workspaceName: String by option("--workspace", help="The name of the workspace").required()
    val location: File? by option("--location",
        help="The location where the client side of the new workspace should be located").file()

    override fun run() {

    }
}

class OpenWorkspaceCommand: PolytopeCommandBase("open", help="Open an existing workspace in this client") {
    val project: String by option("--project", help="The project containing the workspace").required()
    val workspaceName: String by option("--workspace", help="The name of the workspace").required()
    val location: File? by option("--location",
        help="The location where the client side of the workspace should be located").file()

    override fun run() {

    }
}

class WorkspaceSaveCommand: PolytopeCommandBase("save", "save changes in this workspace to the server") {
    val description: String by option("--description").required()
    val resolved: List<String> by option("--resolve", help="the name of a file whose conflicts were resolved").multiple()
    override fun run() {

    }
}

class WorkspaceDeliverCommand: PolytopeCommandBase("deliver", "deliver the current change to its parent history") {
    val description: String by option("--description").required()
    override fun run() {

    }
}


class WorkspaceUpdateCommand: PolytopeCommandBase("update", "update the workspace from its parent history") {
    override fun run() {

    }
}

class WorkspaceIntegrateCommand: PolytopeCommandBase("integrate", "Integrate a specific change into this workspace") {
    val history: String by option("--history", help="The history containing the change").required()
    val change: String by option("--change", help="The change to integrate").required()

    override fun run() {

    }

}

class WorkspaceDeleteCommand: PolytopeCommandBase("delete", help="Delete a workspace") {
    val project: String by option("--project", help="The project containing the workspace").required()
    val ws: String by option("--workspace", help="The name of the workspace to delete").required()
    override fun run() {

    }
}

class WorkspaceAbandonCommand: PolytopeCommandBase(help="Abandon the current change in the workspace, and reset to the last savepoint",
    name="abandon-change") {

    private val reason: String by option("--reason", help="A desrciption of why the change is being abandoned.").required()
    override fun run() {

    }
}
