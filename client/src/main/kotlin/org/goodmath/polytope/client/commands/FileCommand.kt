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
import org.goodmath.polytope.client.workspace.ClientWorkspace
import org.goodmath.polytope.client.workspace.WorkspaceConfig
import org.goodmath.polytope.common.PtException
import org.goodmath.polytope.common.WorkspaceFileContents
import org.goodmath.polytope.common.agents.Directory
import org.goodmath.polytope.common.agents.DirectoryAgent
import org.goodmath.polytope.common.stashable.Workspace
import java.io.File
import kotlin.io.path.*


class FileCommand : PolytopeCommandBase("file", help = "Manipulate files in a polytope workspace.") {
    override fun run() {
    }

    companion object {
        fun getCommand(): CliktCommand =
            FileCommand().subcommands(
                FileListCommand(),
                FileAddCommand(),
                FileMkdirCommand(),
                FileMoveCommand(),
                FileDeleteCommand(),
                FileIgnoreCommand()
            )
    }
}

class FileAddCommand : PolytopeCommandBase("add", help = "Add a new tracked file to the workspace.") {
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

class FileMoveCommand : PolytopeCommandBase("mv", "Move a tracked file inside of a workspace.") {
    private val fromPaths: List<String> by argument(help = "the file to move").multiple()
    private val toPath: String by argument(help = "the place to move it to")
    override fun run() {

        handleCommandErrors("file", "mv") {
            val ws = requireWorkspace()
            if (fromPaths.size > 1) {
                val dirPath = Path(toPath)
                if (!dirPath.isDirectory()) {
                    throw PtException(
                        PtException.Kind.InvalidParameter,
                        "When moving multiple files, the target path must be a directory"
                    )
                } else {
                    runBlocking {
                        for (from in fromPaths) {
                            val name = Path(from).name
                            val fullToPath = dirPath / name
                            ws.moveFile(from, fullToPath.toString())
                        }
                    }
                }
            } else {
                runBlocking {
                    ws.moveFile(fromPaths[0], toPath)
                }
            }
        }
    }
}

class FileDeleteCommand : PolytopeCommandBase("rm", "Delete files in the workspace.") {
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

class FileListCommand : PolytopeCommandBase(
			    "list",
			    help = "List the tracked, versioned artifacts in the workspace") {
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

class FileMkdirCommand : PolytopeCommandBase(
			     "mkdir",
			     help = "Create a tracked directory in a polytope workspace") {
    private val makeIntermediates by option(
        "-p",
        "--make-intermediates",
        help = "If true, then automatically make any intermediate directories"
    ).flag(default = false)
    private val dir by argument(name="directory", help="the name of the directory to create")

    suspend fun mkdir(ws: ClientWorkspace, allPaths: Set<String>, dirPath: String) {
        val parentPath = Path(dirPath).parent.pathString
        if (parentPath !in allPaths) {
            mkdir(ws, allPaths, parentPath)
        }
        File(dirPath).mkdir()
        // Make a directory object, and add it.
        val dirContent = Directory()
        ws.apiClient.workspaceAddFile(
	    ws.project,
	    ws.name,
	    dirPath,
	    WorkspaceFileContents(dirPath, DirectoryAgent.artifactType,
				  DirectoryAgent.encodeToString(dirContent)))
    }

    override fun run() {
        val ws = requireWorkspace()
        runBlocking {
            val allPaths = ws.apiClient.workspaceListPaths(ws.project, ws.name).paths.toSet()
            val parentPath = Path(dir).parent.pathString
            if (!makeIntermediates && parentPath  !in allPaths) {
                throw PtException(PtException.Kind.NotFound,
                    "Parent directory $parentPath does not exist")
            } else {
                mkdir(ws, allPaths, dir)
            }
        }
    }

}

class FileIgnoreCommand :
    PolytopeCommandBase("ignore", help = "Tell the polytope client to ignore files in the workspace") {
    private val pattern by argument("pattern", help = "a glob expression describing files to be ignored")
    override fun run() {
        handleCommandErrors("file", "ignore") {
            val ws = requireWorkspace()
            val wsCfg = WorkspaceConfig.load(ws.cfg.wsPath)
                ?: throw PtException(
                    PtException.Kind.Internal,
                    "Workspace configuration is invalid"
                )
            val updated = wsCfg.copy(ignorePatterns = wsCfg.ignorePatterns + pattern)
            updated.save(ws.cfg.wsPath)
        }
    }


}
