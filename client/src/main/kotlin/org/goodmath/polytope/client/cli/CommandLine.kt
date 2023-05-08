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

import com.beust.klaxon.Klaxon
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import org.goodmath.polytope.client.ClientCommandException
import org.goodmath.polytope.client.ClientConfig
import org.goodmath.polytope.client.ClientException
import org.goodmath.polytope.client.workspace.ClientWorkspace
import org.goodmath.polytope.common.PtException
import org.slf4j.LoggerFactory
import java.lang.Exception
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.system.exitProcess


val klaxon = Klaxon()




abstract class PolytopeCommandBase(name: String, help: String): CliktCommand(name=name, help=help) {
    val logger = LoggerFactory.getLogger(PolytopeCommandBase::class.java)
    val homedir = Path(System.getenv("HOME"))
    var workspace: ClientWorkspace? = null
    var cfg: ClientConfig? = null

    fun getOrPromptForPassword(): String {
        fun tryFromPath(p: Path): String? {
            return if (p.exists()) {
                p.bufferedReader().readLine()
            } else {
                null
            }
        }

        return tryFromPath(homedir / ".config" / "polytope" / ".ptpassword")
            ?: prompt(
                text = "Enter password: ",
                hideInput = true,
            ) ?: throw ClientException("login", "Could not read user password")
    }


    fun getClientConfig(): ClientConfig {
        return ClientConfig.load()
    }

    fun loadWorkspace(): ClientWorkspace? {
        if (workspace == null) {
            val cfg = ClientConfig.load()
            workspace = ClientWorkspace(cfg)
        }
        return workspace

    }

    fun handleCommandErrors(noun: String, verb: String, body: () -> Unit) {
        try {
            body()
            if (workspace != null) {
                workspace!!.close()
                workspace = null
            }
            exitProcess(0)
        } catch (e: Throwable) {
            printErrorAndExit(noun, verb, e)
        }
    }

    private fun printErrorAndExit(noun: String, verb: String, e: Throwable) {
        when(e) {
            is ClientCommandException -> {
                echo("Error performing $noun $verb: ${e.message}", err = true)
                exitProcess(e.exitCode)
            }
            is ClientException -> {
                echo(e.message)
            }
            is PtException ->{
                echo(e.toString())
            }
            else -> {
                echo("Unexpected internal error performing $noun $verb: $e", err = true)
                e.printStackTrace()
                exitProcess(1)
            }
        }
    }

}

class Polytope: PolytopeCommandBase("pt", help="The polytope command line client") {
    override fun run() {
    }

    companion object {
        fun getCommand(): CliktCommand =
            Polytope()
                .subcommands(
                    WorkspaceCommand.getCommand(),
                    FileCommand.getCommand(),
                    ProjectCommand.getCommand(),
                    HistoryCommand.getCommand(),
                    ChangeCommand.getCommand(),
                    UserCmd.getCommand(),
                )

    }
}

fun main(args: Array<String>) {
    Polytope.getCommand().main(args)
}
