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
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import kotlinx.coroutines.runBlocking
import org.goodmath.polytope.client.api.RestApiClient
import org.goodmath.polytope.common.PtException
import org.goodmath.polytope.common.stashable.ProjectVersionSpecifier

class ChangeCommand : PolytopeCommandBase("change", "Manipulate changes in a polytope depot.") {
    override fun run() {
    }

    companion object {
        fun getCommand(): CliktCommand =
            ChangeCommand()
                .subcommands(
                    ChangeListCommand(),
                    ChangeGetCommand(),
                    ChangeSavesCommand(),
                    ChangeCreateCommand(),
                    ChangeOpenCommand(),
                    ChangeDeliverCommand(),
                    ChangeIntegrateCommand(),
                    ChangeIntegrateDiffCommand(),
                    ChangeAbortCommand()
                )
    }
}

class ChangeCreateCommand : PolytopeCommandBase("create", help = "Create a change in the current workspace.") {
    private val name: String by option("-n", "--name", help = "the name of the new change").required()
    private val description: String by option(
        "-d",
        "--description",
        help = "a short description of the change"
    ).required()
    private val parentHistory: String? by option(
        "-h", "--history", help = "the history that will contain the change. "
    )

    override fun run() {
        handleCommandErrors("change", "create") {
            val ws = requireWorkspace()
            val history = parentHistory ?: ws.history
            runBlocking {
                val updated = ws.apiClient.workspaceCreateChange(ws.project, ws.name, history, name, description)
                ws.populate(updated)
            }
            echo("Created new change $name ($description) in $history")
        }
    }

}

class ChangeOpenCommand : PolytopeCommandBase("open", help = "Open a change in the current workspace") {
    private val historyName: String? by option("--history", help = "the name of the history containing the change")
    private val changeName: String by option("--name", help = "the name of the existing change").required()

    override fun run() {
        handleCommandErrors("change", "open") {
            val ws = requireWorkspace()
            val hist = historyName ?: ws.history
            runBlocking {
                ws.openChange(hist, changeName)
            }
            echo("Opened change $changeName from $hist")
        }
    }

}


class ChangeListCommand : PolytopeCommandBase("list", "List the changes in a history.") {
    private val project: String? by option("--project", help = "the project containing the history")
    private val history: String? by option("--history", help = "the history containing the change")
    private val show: String by option("--show", help = "the minimum status to include in the list")
        .choice("all", "aborted", "closed", "open").default("open")
    private val usernameArg: String? by option("--user", help = "the userId of the user performing the command")
    private val serverUrlArg: String? by option("--server", help = "the URL of the polytope server")
    private val format: String by option("--format", help = "the output format")
        .choice("text", "json")
        .default("text")

    override fun run() {
        handleCommandErrors("change", "list") {
            val ws = loadWorkspace()
            val p = project ?: ws?.project
            val h = history ?: ws?.history
            if (p == null) {
                throw PtException(
                    PtException.Kind.UserError,
                    "Either a project must be specified, or this command must be run in a workspace"
                )

            }
            if (h == null) {
                throw PtException(
                    PtException.Kind.UserError,
                    "Either a history must be specified, or this command must be run in a workspace"
                )
            }
            val userId = usernameArg ?: requireUserId()
            val serverUrl = serverUrlArg ?: requireServerUrl()

            val apiClient = ws?.apiClient ?: RestApiClient(serverUrl, userId, getOrPromptForPassword())

            val changes = runBlocking {
                apiClient.changesList(p, h, show)
            }

            if (format == "text") {
                for (c in changes.changes) {
                    echo(c.render())
                    echo("----")
                }
            } else {
                echo(prettyJson(changes))
            }

        }
    }

}

class ChangeGetCommand : PolytopeCommandBase("get", "Retrieve and view a specific change from the depot.") {
    private val project: String? by option("-p", "--project", help = "the project containing the history")
    private val history: String? by option("-h", "--history", help = "the history containing the change")
    private val change: String by option("-c", "--change", help = "the name of the change").required()
    private val format: String by option("-f", "--format", help = "the output format")
        .choice("text", "json")
        .default("text")
    private val usernameArg: String? by option("-u", "--user", help = "the userId of the user performing the command")
    private val serverUrlArg: String? by option("-s", "--server", help = "the URL of the polytope server")

    override fun run() {
        handleCommandErrors("change", "get") {
            val ws = loadWorkspace()
            val pr = project
                ?: ws?.project
                ?: throw PtException(
                    PtException.Kind.UserError,
                    "Either a project must be supplied, or this command must be run inside the workspace."
                )
            val hi = history
                ?: ws?.history
                ?: throw PtException(
                    PtException.Kind.UserError,
                    "Either a history must be supplied, or this command must be run inside the workspace."
                )
            val apiClient = if (ws == null) {
                val user = usernameArg ?: requireUserId()
                val password = getOrPromptForPassword()
                val server = serverUrlArg ?: requireServerUrl()
                RestApiClient(server, user, password)
            } else {
                ws.apiClient
            }
            val ch = runBlocking {
                apiClient.changeGet(pr, hi, change)
            }
            if (format == "text") {
                echo(ch.render())
                echo("----")
            } else {
                echo(prettyJson(ch))
            }
        }
    }
}

class ChangeSavesCommand : PolytopeCommandBase("saves", help = "List the savepoints in a change.") {
    private val project: String? by option("-p", "--project", help = "the project containing the change")
    private val history: String? by option("-h", "--history", help = "the history containing the change")
    private val change: String? by option("-c", "--change", help = "the name of the change")
    private val username: String? by option("-u", "--user", help = "the userid of the user performing the command")
    private val serverUrl: String? by option("-s", "--server", help = "the URL of the polytope server")
    private val format: String by option("-f", "--format", help = "the output format")
        .choice("text", "json")
        .default("text")

    override fun run() {
        handleCommandErrors("change", "saves") {
            val ws = loadWorkspace()
            val pr = project
                ?: ws?.project
                ?: throw PtException(
                    PtException.Kind.UserError,
                    "Either a project must be supplied, or this command must be run inside the workspace."
                )
            val hi = history
                ?: ws?.history
                ?: throw PtException(
                    PtException.Kind.UserError,
                    "Either a history must be supplied, or this command must be run inside the workspace."
                )
            val ch = change ?: ws?.change ?: throw throw PtException(
                PtException.Kind.UserError,
                ("Either a history must be supplied, or this command must be " +
                        "run inside a workspace with an active change.")
            )
            val apiClient = if (ws == null) {
                val user = username ?: requireUserId()
                val password = getOrPromptForPassword()
                val server = serverUrl ?: requireServerUrl()
                RestApiClient(server, user, password)
            } else {
                ws.apiClient
            }
            val saves = runBlocking {
                apiClient.savesList(pr, hi, ch)
            }
            if (format == "text") {
                for (s in saves.saves) {
                    echo(s.toString())
                    echo("----")
                }
            } else {
                echo(prettyJson(saves))
            }
        }
    }
}

class ChangeDeliverCommand : PolytopeCommandBase("deliver", help = "Deliver a completed change to its history.") {
    private val description: String by option(
        "-d",
        "--description",
        help = "a description of the change being delivered"
    ).required()

    override fun run() {
        handleCommandErrors("change", "deliver") {
            val ws = requireWorkspace()
            ws.ensureUnchanged()
            runBlocking {
                val updated = ws.apiClient.workspaceDeliver(ws.project, ws.name, description)
                ws.populate(updated)
            }
            echo("Change successfully delivered to history ${ws.history}")
        }
    }
}

class ChangeIntegrateCommand : PolytopeCommandBase(
    "integrate",
    help = "integrate a change from a different history into an in-progress change."
) {
    private val history: String by option(
        "-h",
        "--history",
        help = "the history containing the change to integrate"
    ).required()
    private val change: String by option("-c", "--change", help = "the name of the change to integrate").required()
    override fun run() {
        handleCommandErrors("change", "integrate") {
            val ws = requireWorkspace()
            val updated = runBlocking {
                val updated = ws.apiClient.workspaceIntegrateChange(ws.project, ws.name, history, change)
                ws.populate(updated)
                updated
            }
            if (updated.conflicts.isNotEmpty()) {
                echo("Integrating change produced conflicts:")
                for (c in updated.conflicts) {
                    echo(c.render())
                    echo("----")
                }
            }
        }
    }
}

class ChangeIntegrateDiffCommand : PolytopeCommandBase(
    "integrate-diff",
    help = "integrate a diff from a different history into an in-progress change."
) {
    private val fromVersion: String by option(
        "-f",
        "--from",
        help = "The project version specifier of the beginning of the diff"
    ).required()
    private val toVersion: String by option(
        "-t",
        "--to",
        help = "The project version specifier of the end of the diff"
    ).required()

    override fun run() {
        handleCommandErrors("change", "integrate-diff") {
            val ws = requireWorkspace()
            val fromPvs = ProjectVersionSpecifier.fromString(fromVersion)
            val toPvs = ProjectVersionSpecifier.fromString(toVersion)
            val updated = runBlocking {
                val updated = ws.apiClient.workspaceIntegrateDiff(ws.project, ws.name, fromPvs, toPvs)
                ws.populate(updated)
                updated
            }
            if (updated.conflicts.isNotEmpty()) {
                echo("Integrating diff produced conflicts:")
                for (c in updated.conflicts) {
                    echo(c.render())
                    echo("----")
                }
            }
        }
    }
}


class ChangeAbortCommand : PolytopeCommandBase(
    name = "abort",
    help = "Abort an in-progress change."
) {
    private val project: String by option("-p", "--project", help = "the project containing the history").required()
    private val history: String by option("-h", "--history", help = "the history containing the change").required()
    private val change: String by option("-c", "--change", help = "the name of the change").required()
    private val reason: String by option("-d", "--reason", help = "the reason for abandoning the change").required()
    private val username: String? by option("-u", "--user", help = "the userId of the user performing the command")
    private val serverUrlOpt: String? by option("-s", "--server", help = "the URL of the polytope server")

    override fun run() {
        handleCommandErrors("change", "abandon") {
            val userId = username ?: requireUserId()
            val serverUrl = serverUrlOpt ?: requireServerUrl()
            val apiClient = RestApiClient(serverUrl, userId, getOrPromptForPassword())
            runBlocking {
                apiClient.abortChange(project, history, change, reason)
            }
        }
    }
}