package org.goodmath.polytope.client.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option


class FileCommand: PolytopeCommandBase("file", help="commands for working with files in a workspace") {
    override fun run() {
    }

    companion object {
        fun getCommand(): CliktCommand =
            FileCommand().subcommands(
                AddFileCommand(), MoveFileCommand(), DeleteFileCommand(),
                ListFiles()
            )
    }
}

class AddFileCommand: PolytopeCommandBase("add", help="Add a new file to the project") {
    val paths: List<String> by argument(help="Files to add to the project").multiple()
    override fun run() {
        echo("Add files to the workspace: ${paths.joinToString(",")}")
    }
}

class MoveFileCommand: PolytopeCommandBase("mv", "move a file inside of a workspace") {
    private val fromPaths: List<String> by argument(help="The files to move").multiple()
    private val toPath: String by argument(help="The place to move them to")
    override fun run() {
        echo("Move files from (${fromPaths.joinToString(",")} to $toPath")
    }
}

class DeleteFileCommand: PolytopeCommandBase("rm", "delete files in the workspace") {
    val recursive: Boolean by option("-r", "--recursive").flag(default=false)
    val untrack: Boolean by option("-u", "--untrack-only",
        help=("If true, the file will be removed from the set of versioned artifacts tracked\n" +
                "by the workspace, but the file will not be deleted")).flag(default = false)
    val paths: List<String>  by argument(help="The files to delete").multiple()
    override fun run() {
        echo("Delete files: ${paths.joinToString(",")}")
        echo("\tRecursive=$recursive, untrack=$untrack")
    }
}

class ListFiles: PolytopeCommandBase("list", help="List the tracked, versioned artifacts in the workspace") {
    override fun run() {
        echo("List files")
    }
}
