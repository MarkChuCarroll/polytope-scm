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
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required

class ChangeCommand: PolytopeCommandBase("change", "work with changes") {
    override fun run() {
    }

    companion object {
        fun getCommand(): CliktCommand =
            ChangeCommand()
                .subcommands(ListChangesCommand(), GetChangeCommand(), ListSavesCommand())
    }
}

class CreateChangeCommand: PolytopeCommandBase("create", help="create a change in this workspace") {
    val name: String by option("--name", help="The name of the new change").required()
    val description: String by option("-d", "--description", help="A short description of the change").required()
    val parentHistory: String? by option("-h", "--history", help="The history that will contain the change. " +
            "Default is the history currently populating the workspace.")

    override fun run() {
        echo("Create new change $name ($description) in $parentHistory")
    }

}
class OpenChangeCommand: PolytopeCommandBase("create", help="create a change in this workspace") {
    val name: String by option("--name", help="The name of the existing change").required()
    val history: String? by option("-h", "--history", help="The name of the history containing the change")

    override fun run() {
        echo("Open change $name from $history")
    }

}



class ListChangesCommand: PolytopeCommandBase("list", "list changes open in a history") {
    val project: String? by option("--project", help="The project containing the history")
    val history: String? by option("--history", help="The history containing the change")

    override fun run() {
        echo("list changes")
    }

}

class GetChangeCommand: PolytopeCommandBase("get", "retrieve a specific change record") {
    val project: String? by option("--project", help="The project containing the history")
    val history: String? by option("--history", help="The history containing the change")
    val change: String by option("--change", help="The name of the change").required()

    override fun run() {
    }
}

class ListSavesCommand: PolytopeCommandBase("saves", help="list savepoints in a change") {
    val project: String? by option("--project", help="The project containing the history")
    val history: String? by option("--history", help="The history containing the change")
    val change: String? by option("--change", help="The name of the change")
    override fun run() {

    }
}

