package org.goodmath.polytope.client.workspace
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
    val password: String?,
    val ignorePatterns: List<String>,
    val serverUrl: String,
    val wsName: String,
    val wsPath: Path
) {

    companion object {
        val klaxon = Klaxon()

        fun load(): ClientConfig? {
            val wsConfig = WorkspaceConfig.load(Path("."))
            val userConfig = UserConfig.load()
            val password = if (wsConfig != null) {
                findAndLoadPassword(wsConfig.wsPath)
            } else {
                null
            }
            val userId = wsConfig?.userId ?: userConfig.userId ?: return null
            val email = wsConfig?.email ?: userConfig.email ?: return null
            val fullName = wsConfig?.userFullName ?: userConfig.userId ?: return null
            return ClientConfig(
                userId,
                email,
                fullName,
                ignorePatterns = (wsConfig?.ignorePatterns ?: emptyList()) + userConfig.ignorePatterns,
                serverUrl = wsConfig?.serverUrl ?: return null,
                wsPath = Path(wsConfig?.wsPath ?: return null),
                wsName = wsConfig.wsName,
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
                return ClientConfig.klaxon.parse(cfgPath.toFile())?: UserConfig()
            }
            return UserConfig()
        }
    }
}
data class WorkspaceConfig(
    val wsName: String,
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

        fun load(path: Path = Path(".")): WorkspaceConfig? {
            val wsPath = findWorkspace(path)
                ?: return null
            val cfgPath = wsPath / ".pt" / "config.json"
            return if (cfgPath.exists()) {
                ClientConfig.klaxon.parse(cfgPath.toFile()) ?: throw PtException(PtException.Kind.UserError,
                    "Invalid configuration file")
            } else {
                null
            }
        }
    }

}
