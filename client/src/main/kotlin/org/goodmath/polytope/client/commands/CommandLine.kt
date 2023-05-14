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

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import org.goodmath.polytope.client.workspace.ClientConfig
import org.goodmath.polytope.client.workspace.ClientWorkspace
import org.goodmath.polytope.common.PtException
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.system.exitProcess


val klaxon = Klaxon()

abstract class PolytopeCommandBase(name: String, help: String) : CliktCommand(name = name, help = help) {
    private val logger = LoggerFactory.getLogger(PolytopeCommandBase::class.java)
    private val homedir = Path(System.getenv("HOME"))
    private var workspace: ClientWorkspace? = null
    private var cfg: ClientConfig? = null

    /**
     * This is used by commands that require a workspace. If the command is not run in a
     * workspace, this will throw an exception.
     */
    fun requireWorkspace(): ClientWorkspace {
        return loadWorkspace() ?: throw PtException(
            PtException.Kind.UserError,
            "Command must be run inside of a workspace"
        )
    }

    fun requireServerUrl(): String {
        return getClientConfig()?.serverUrl ?: throw PtException(
            PtException.Kind.UserError,
            "You must either supply a serverURL, or have a serverURL stored in your user or workspace config"
        )

    }

    fun requireUserId(): String {
        return getClientConfig()?.userId ?: throw PtException(
            PtException.Kind.UserError,
            "You must either supply a userid, or have your userid stored in your user or workspace config"
        )
    }

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
            ) ?: throw PtException(
                PtException.Kind.Authentication,
                "Could not read user password"
            )
    }


    fun getClientConfig(): ClientConfig? {
        if (cfg == null) {
            cfg = ClientConfig.load()
        }
        return cfg
    }

    fun requireClientConfig(): ClientConfig {
        return getClientConfig() ?: throw PtException(
            PtException.Kind.NotFound,
            "configuration not found"
        )
    }

    fun prettyJson(t: Any): String {
        return klaxon.parse<JsonObject>(klaxon.toJsonString(t))!!.toJsonString(true)
    }

    fun loadWorkspace(): ClientWorkspace? {
        if (workspace == null) {
            logger.info("Loading workspace")
            val cfg = ClientConfig.load() ?: return null
            workspace = ClientWorkspace(cfg, cfg.wsName)
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
        when (e) {
            is PtException -> {
                echo(e.toString())
                exitProcess(e.kind.toExitCode())
            }

            else -> {
                echo("Unexpected internal error performing $noun $verb: $e", err = true)
                e.printStackTrace()
                exitProcess(1)
            }
        }
    }
}

class Polytope : PolytopeCommandBase("pt", help = "The Polytope SCM Command line") {
    private val logLevel: String by option("--logging", help = "The logging level to display")
        .choice("error", "warning", "info", "trace", "all", "off")
        .default("off")

    private fun updateLoggingLevel(level: String) {
        val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
        val logger: Logger = loggerContext.getLogger(PolytopeCommandBase::class.java)
        logger.level = Level.toLevel(level)


    }

    override fun run() {
        updateLoggingLevel(logLevel)
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
