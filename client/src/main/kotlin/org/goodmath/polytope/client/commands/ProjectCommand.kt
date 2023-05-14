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

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import kotlinx.coroutines.runBlocking
import org.goodmath.polytope.client.api.RestApiClient

class ProjectCommand : PolytopeCommandBase("project", "Manipulate projects in a polytope depot.") {
    override fun run() {
    }

    companion object {
        fun getCommand() =
            ProjectCommand()
                .subcommands(
                    ProjectListCommand(),
                    ProjectGetCommand(),
                    ProjectCreateCommand()
                )
    }

}

class ProjectCreateCommand : PolytopeCommandBase("create", "Create a new project.") {
    private val username: String? by option("-u", "--user", help = "the id of the user")
    private val serverUrl: String? by option("-s", "--server", help = "the URL of the polytope depot server")
    private val description: String by option(
        "-d",
        "--description",
        help = "a short description of the new project"
    ).required()
    private val name: String by argument(help = "the name of the new project")
    override fun run() {
        handleCommandErrors("project", "create") {
            val user = username ?: requireUserId()
            val password = getOrPromptForPassword()
            val server = serverUrl ?: requireServerUrl()
            val apiClient = RestApiClient(server, user, password)
            runBlocking {
                apiClient.projectCreate(name, description)
            }
        }
    }
}

class ProjectListCommand : PolytopeCommandBase("list", "list projects in the depot") {
    private val username: String? by option("-u", "--user", help = "the id of the user")
    private val serverUrl: String? by option("-s", "--server", help = "the URL of the polytope server")
    private val format: String by option("-f", "--format").choice("text", "json").default("text")

    override fun run() {
        handleCommandErrors("project", "list") {
            val user = username ?: requireUserId()
            val password = getOrPromptForPassword()
            val server = serverUrl ?: requireServerUrl()
            val apiClient = RestApiClient(server, user, password)
            val projects = runBlocking {
                apiClient.projectList()
            }
            if (format == "text") {
                for (p in projects.projects) {
                    echo(p.render())
                    echo("----")
                }
            } else {
                echo(prettyJson(projects))
            }
        }
    }
}

class ProjectGetCommand : PolytopeCommandBase("get", help = "get information about a project") {
    private val username: String? by option("-u", "--user", help = "the id of the user")
    private val serverUrl: String? by option("-s", "--server", help = "the URL of the polytope server")
    private val format: String by option("-f", "--format").choice("text", "json").default("text")
    private val project: String by argument(help = "the name of the project")
    override fun run() {
        handleCommandErrors("project", "get") {
            val user = username ?: requireUserId()
            val password = getOrPromptForPassword()
            val server = serverUrl ?: requireServerUrl()
            val apiClient = RestApiClient(server, user, password)
            val theProject = runBlocking {
                apiClient.projectGet(project)
            }

            if (format == "text") {
                echo(theProject.toString())
            } else {
                echo(prettyJson(theProject))
            }
        }
    }
}
