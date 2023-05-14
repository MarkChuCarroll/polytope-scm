package org.goodmath.polytope.client.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import kotlinx.coroutines.runBlocking
import org.goodmath.polytope.common.PtException
import java.io.File


class FileCommand : PolytopeCommandBase("file", help = "Manipulate files in a polytope workspace.") {
    override fun run() {
    }

    companion object {
        fun getCommand(): CliktCommand =
            FileCommand().subcommands(
                ListFiles(),
                AddFileCommand(),
                MoveFileCommand(),
                DeleteFileCommand()
            )
    }
}

class AddFileCommand : PolytopeCommandBase("add", help = "Add a new tracked file to the workspace.") {
    private val recursive: Boolean by option(
        "-r",
        "--recursive",
        help = "recursively add files in subdirectories"
    ).flag(default = false)
    private val paths: List<String> by argument(help = "the files to add to the project").multiple()

    override fun run() {
        handleCommandErrors("file", "add") {
            val ws = requireWorkspace()
            runBlocking {
                ws.addFiles(paths, recursive)
            }
        }
    }
}

class MoveFileCommand : PolytopeCommandBase("mv", "Move a tracked file inside of a workspace.") {
    private val fromPath: String by argument(help = "the file to move")
    private val toPath: String by argument(help = "the place to move it to")
    override fun run() {
        // TODO: this should be able to handle multiple things to move (like mv),
        // and the target should be able to be either a complete path, or a directory name.
        handleCommandErrors("file", "mv") {
            val ws = requireWorkspace()
            runBlocking {
                ws.moveFile(fromPath, toPath)
            }
        }
    }
}

class DeleteFileCommand : PolytopeCommandBase("rm", "Delete files in the workspace.") {
    private val recursive: Boolean by option("-r", "--recursive").flag(default = false)
    private val untrack: Boolean by option(
        "-x", "--untrack-only",
        help = ("if true, the file will be removed from the set of versioned artifacts tracked\n" +
                "by the workspace, but the file will not be deleted")
    ).flag(default = false)
    private val paths: List<String> by argument(help = "The files to delete").multiple()
    override fun run() {
        handleCommandErrors("file", "delete") {
            val ws = requireWorkspace()
            runBlocking {
                for (p in paths) {
                    val pathFile = File(p)
                    if (pathFile.isDirectory) {
                        if (recursive) {
                            ws.deleteFile(p, !untrack)
                        } else {
                            throw PtException(
                                PtException.Kind.UserError,
                                "$p is a directory; use --recursive to delete"
                            )
                        }
                    } else {
                        ws.deleteFile(p, !untrack)
                    }
                }
            }
        }
    }
}

class ListFiles : PolytopeCommandBase("list", help = "List the tracked, versioned artifacts in the workspace") {
    private val format: String by option("-f", "--format", help = "the output format")
        .choice("text", "json")
        .default("text")

    override fun run() {
        handleCommandErrors("file", "list") {
            val ws = requireWorkspace()
            val files = runBlocking {
                ws.listFilePaths()
            }
            if (format == "text") {
                for (f in files) {
                    echo(f)
                }
            } else {
                echo(prettyJson(files))
            }
        }
    }
}
