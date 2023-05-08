package org.goodmath.polytope.client
import com.beust.klaxon.Klaxon
import org.goodmath.polytope.common.PtException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.exists


/*
 * Configuring the client.
 *
 * The client config is made up of several pieces:
 * - A password file - either ".ptpassword" in the root directory of a
 *   workspace, or .ptpassword in the user's "~/.config/polytope".
 * - a workspace config file, ".pt/config.json", which is a render
 *   of a WorkspaceConfig object.
 * - a user config file, "~/.config/polytope/config.json"
 *
 * If a config key exists in both the user and workspace config file,
 * then:
 * - if the value is a list, then the values are merged, with the
 *   values from the workspace coming first.
 * - if the value is atomic, then the workspace config takes precedence.
 */
data class ClientConfig(
    val userId: String,
    val userFullName: String,
    val email: String,
    val password: String,
    val ignorePatterns: List<String>,
    val serverUrl: String,
    val wsPath: Path
) {

    companion object {
        val klaxon = Klaxon()

        fun load(): ClientConfig {
            val wsConfig = WorkspaceConfig.load(Path("."))
            val userConfig = UserConfig.load()
            val password = findAndLoadPassword(wsConfig.wsPath)
            return ClientConfig(
                userId = wsConfig.userId ?: userConfig.userId
                   ?: throw PtException(PtException.Kind.UserError, "Configuration must define a userId"),
                email = wsConfig.email ?: userConfig.email
                ?: throw PtException(PtException.Kind.UserError, "Configuration must define an email address"),
                userFullName = wsConfig.userFullName ?: userConfig.userFullName
                ?: throw PtException(PtException.Kind.UserError, "Configuration must define a user full name"),
                ignorePatterns = wsConfig.ignorePatterns + userConfig.ignorePatterns,
                serverUrl = wsConfig.serverUrl,
                wsPath = Path(wsConfig.wsPath),
                password = password)

        }

        private fun findAndLoadPassword(wsPath: String): String {
            val passInWsPath = Path(wsPath) / ".pt" / ".ptpassword"
            if (passInWsPath.exists()) {
                return passInWsPath.toFile().readLines(Charsets.UTF_8)[0]
            }
            val passInUserConfig = Path("~") / ".config" / "polytope" / ".ptpassword"
            if (passInUserConfig.exists()) {
                return passInUserConfig.toFile().readLines(Charsets.UTF_8)[0]
            }
            throw PtException(PtException.Kind.UserError, "Password required")
        }
    }
}

data class UserConfig(
    val userId: String? = null,
    val userFullName: String? = null,
    val email: String? = null,
    val ignorePatterns: List<String> = emptyList()
) {
    companion object {
        fun load(): UserConfig {
            val cfgPath = Path("~/.config/polytope/config.json")
            if (cfgPath.exists()) {
                return ClientConfig.klaxon.parse(cfgPath.toFile())?:UserConfig()
            }
            return UserConfig()
        }
    }
}
data class WorkspaceConfig(
    val serverUrl: String,
    val wsPath: String,
    val userId: String? = null,
    val userFullName: String? = null,
    val email: String? = null,
    val ignorePatterns: List<String> = emptyList()
) {
    companion object {
        private fun findWorkspace(p: Path): Path? {
            return if ((p / ".pt" / "config.json").exists()) {
                p
            } else {
                if (p.parent != null) {
                    findWorkspace(p.parent)
                } else {
                    null
                }
            }
        }

        fun load(path: Path = Path(".")): WorkspaceConfig {
            val wsPath = findWorkspace(path)
                ?: throw PtException(PtException.Kind.UserError,
                    "Command must be run in a workspace")
            val cfgPath = wsPath / ".pt" / "config.json"
            if (cfgPath.exists()) {
                return ClientConfig.klaxon.parse(cfgPath.toFile()) ?:
                        throw PtException(PtException.Kind.UserError,
                            "Invalid configuration file")
            } else {
                throw PtException(PtException.Kind.UserError,
                    "Workspace directory must contain a configuration")
            }
        }
    }

}
