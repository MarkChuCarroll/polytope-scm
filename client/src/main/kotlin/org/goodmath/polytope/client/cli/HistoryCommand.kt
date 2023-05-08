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
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int

class HistoryCommand: PolytopeCommandBase("history", "work with histories") {
    override fun run() {
    }

    companion object {
        fun getCommand(): CliktCommand =
            HistoryCommand()
                .subcommands(CreateHistoryCommand(), ListHistoriesCommand(), ListHistoryVersionsCommand())
    }
}

class CreateHistoryCommand: PolytopeCommandBase("create", "create a new history") {
    val project: String by option("--project", help="the project that should contain the history").required()
    val sourceHistory: String by option("--from-history", help="the history that the new history will branch from").required()
    val index: Int? by option("--from-history-version", help="The history version to branch from.").int()
    val name: String by argument(help = "The name of the new history")
    override fun run() {
    }
}

class ListHistoriesCommand: PolytopeCommandBase("list", "list histories") {
    val project by option("--project", help="the project containing the histories").required()
    override fun run() {

    }
}

class ListHistoryVersionsCommand: PolytopeCommandBase("versions", "list versions of a history") {
    val project by option("--project", help="the project containing the history").required()
    val history by option("--history", help="the name of the history").required()
    override fun run() {

    }
}

