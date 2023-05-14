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
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.coroutines.runBlocking
import org.goodmath.polytope.client.api.RestApiClient
import org.goodmath.polytope.common.PtException

class HistoryCommand : PolytopeCommandBase("history", "Manipulate histories in the polytope depot.") {
    override fun run() {
    }

    companion object {
        fun getCommand(): CliktCommand =
            HistoryCommand()
                .subcommands(
                    HistoryListCommand(),
                    HistoryGetCommand(),
                    HistoryStepsCommand(),
                    HistoryCreateCommand(),
                    HistoryOpenCommand(),
                )
    }
}

class HistoryGetCommand : PolytopeCommandBase("get", "Retrieve and view a history for a project.") {
    private val project: String? by option("-p", "--project", help = "the project containing the histories")
    private val historyName: String by option("-h", "--history", help = "the name of the history to get").required()
    private val format: String by option("-f", "--format", help = "the output format").choice("text", "json")
        .default("text")
    private val username: String? by option("-u", "--user", help = "the userId of the user performing the command")
    private val serverUrl: String? by option("-s", "--server", help = "the URL of the polytope server")

    override fun run() {
        handleCommandErrors("history", "get") {
            val ws = loadWorkspace()
            val userId = username ?: getClientConfig()?.userId ?: throw PtException(
                PtException.Kind.UserError,
                "You must either provide a userid, or run this command in a workspace"
            )
            val project = project ?: ws?.project ?: throw PtException(
                PtException.Kind.UserError,
                "you must either supply a project parameter, or run this command in a workspace"
            )
            val apiClient = if (username != null) {
                val password = getOrPromptForPassword()
                val server = serverUrl ?: getClientConfig()?.serverUrl ?: throw PtException(
                    PtException.Kind.UserError,
                    "You must either provide a server URL, or run this command in a workspace"
                )
                RestApiClient(server, userId, password)
            } else {
                ws!!.apiClient
            }
            val h = runBlocking {
                apiClient.historyGet(project, historyName)
            }
            if (format == "text") {
                echo(h.render())
            } else {
                echo(prettyJson(h))
            }
        }
    }
}

class HistoryCreateCommand : PolytopeCommandBase("create", "Create a new history in a project.") {
    private val project: String? by option("-p", "--project", help = "the project that should contain the history")
    private val sourceHistory: String? by option(
        "-f",
        "--from-history",
        help = "the history that the new history will branch from"
    )
    private val index: Int? by option("-v", "--from-history-version", help = "the history version to branch from").int()
    private val description: String by option(
        "-d",
        "--description",
        help = "a description for the new branch"
    ).required()
    private val username: String? by option("-u", "--user", help = "The userId of the user performing the command")
    private val serverUrl: String? by option("-s", "--server", help = "The URL of the polytope server")
    private val name: String by argument(help = "The name of the new history")


    override fun run() {
        handleCommandErrors("history", "create") {
            val ws = loadWorkspace()
            val hi = sourceHistory ?: ws?.history ?: throw PtException(
                PtException.Kind.UserError,
                "you must either provide a source history or run this command in a workspace"
            )
            val pr = project ?: ws?.project ?: throw PtException(
                PtException.Kind.UserError,
                "you must either provide a project or run this command in a workspace"
            )
            val apiClient = if (ws == null) {
                val user = username ?: getClientConfig()?.userId ?: throw PtException(
                    PtException.Kind.UserError,
                    "You must either provide a parent history name, or run this command in a workspace"
                )
                val password = getOrPromptForPassword()
                val server = serverUrl ?: getClientConfig()?.serverUrl ?: throw PtException(
                    PtException.Kind.UserError,
                    "You must either provide a server URL, or run this command in a workspace"
                )
                RestApiClient(server, user, password)
            } else {
                ws.apiClient
            }
            runBlocking {
                apiClient.historyCreate(pr, name, description, hi, index)
            }
            if (ws != null) {
                runBlocking {
                    val updated = apiClient.historyOpen(ws.project, ws.name, name)
                    ws.populate(updated)
                }
            }
        }
    }
}

class HistoryListCommand : PolytopeCommandBase("list", "List the histories in a project.") {
    private val project: String? by option("-p", "--project", help = "the project containing the histories")
    private val username: String? by option("-u", "--user", help = "the userId of the user performing the command")
    private val serverUrl: String? by option("-s", "--server", help = "the URL of the polytope server")
    private val format: String by option("-f", "--format", help = "the output format").choice("text", "json")
        .default("text")

    override fun run() {
        handleCommandErrors("history", "list") {
            val ws = loadWorkspace()
            val userId = username ?: requireUserId()
            val project = project ?: ws?.project ?: throw PtException(
                PtException.Kind.UserError,
                "you must either supply a project parameter, or run this command in a workspace"
            )
            val apiClient = if (username != null) {
                val password = getOrPromptForPassword()
                val server = serverUrl ?: requireServerUrl()
                RestApiClient(server, userId, password)
            } else {
                ws!!.apiClient
            }
            val histories = runBlocking {
                apiClient.historyList(project)
            }
            if (format == "text") {
                for (h in histories.histories) {
                    echo(h.render())
                    echo("----")
                }
            } else {
                echo(prettyJson(histories))
            }
        }
    }
}

class HistoryStepsCommand : PolytopeCommandBase("steps", "List the steps is a history.") {
    private val project by option("-p", "--project", help = "the project containing the history")
    private val history by option("-h", "--history", help = "the name of the history")
    private val format: String by option("-f", "--format", help = "The output format").choice("text", "json")
        .default("text")
    private val username: String? by option("-u", "--user", help = "The userId of the user performing the command")
    private val serverUrl: String? by option("-s", "--server", help = "The URL of the polytope server")

    override fun run() {
        handleCommandErrors("history", "steps") {
            val ws = loadWorkspace()
            val userId = username ?: requireUserId()
            val pr = project ?: ws?.project ?: throw PtException(
                PtException.Kind.UserError,
                "you must either supply a project parameter, or run this command in a workspace"
            )
            val hi = history ?: ws?.history ?: throw PtException(
                PtException.Kind.UserError,
                "you must either supply a history parameter, or run this command in a workspace"
            )
            val apiClient = if (username != null) {
                val password = getOrPromptForPassword()
                val server = serverUrl ?: requireServerUrl()
                RestApiClient(server, userId, password)
            } else {
                ws!!.apiClient
            }
            val steps = runBlocking {
                apiClient.stepsList(pr, hi)
            }
            if (format == "text") {
                for (s in steps.steps) {
                    echo(s.render())
                    echo("----")
                }
            } else {
                echo(prettyJson(steps))
            }


        }
    }
}

class HistoryOpenCommand : PolytopeCommandBase("open", "Open a history for work in the workspace.") {
    private val history by argument(help = "the history")
    override fun run() {
        handleCommandErrors("history", "open") {
            val ws = requireWorkspace()
            runBlocking {
                ws.openHistory(history)
            }
            echo("Workspace is now open on history $history")
        }
    }
}

