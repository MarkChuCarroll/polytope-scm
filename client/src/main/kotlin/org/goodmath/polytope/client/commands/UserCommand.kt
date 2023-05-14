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

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import kotlinx.coroutines.runBlocking
import org.goodmath.polytope.client.api.RestApiClient
import org.goodmath.polytope.common.PtException
import org.goodmath.polytope.common.stashable.Action


class UserCmd : PolytopeCommandBase(name = "user", help = "Manipulate user accounts in polytope depot.") {
    override fun run() {
    }

    companion object {
        fun getCommand(): CliktCommand =
            UserCmd()
                .subcommands(
                    UserGetCommand(),
                    UserListCommand(),
                    UserCreateCmd(),
                    UserPasswordCommand(),
                    UserGrantCommand(),
                    UserRevokeCommand(),
                    UserDeactivateCommand(),
                    UserReactivateCommand(),
                )
    }
}

class UserCreateCmd : PolytopeCommandBase(name = "create", help = "Create a new user.") {
    private val asUser: String? by option(
        "-a",
        "--acting-user",
        help = "the userID of the user to act-as when performing this operation"
    )
    private val username: String by option("-u", "--username", help = "the userid of the user to create").required()
    private val fullName: String by option(
        "-f",
        "--full-name",
        help = "the full name of the new user to create"
    ).required()
    private val email: String by option(
        "-e",
        "--email",
        help = "the email address of the new user to create"
    ).required()
    private val password: String by option("-p", "--password", help = "the password of the new user").required()
    private val permitted: List<String> by option(
        "-x",
        "--permitted",
        help = "an action the user is permitted to perform"
    ).multiple()
    private val serverUrl: String? by option("-s", "--server", help = "The URL of the polytope server")

    override fun run() {
        handleCommandErrors("user", "create") {
            val actingUser = asUser ?: requireUserId()
            val actingUserPassword = getOrPromptForPassword()
            val server = serverUrl ?: requireServerUrl()
            val client = RestApiClient(server, actingUser, actingUserPassword)
            val result = runBlocking {
                client.userCreate(username, fullName, email, password,
                    permitted.map { Action.parse(it) }
                )
            }
            echo("Successfully created user: ${result.username}")
        }
    }
}

class UserReactivateCommand : PolytopeCommandBase(name = "reactivate", help = "Re-activate an inactive user.") {
    private val asUser: String? by option(
        "-a",
        "--acting-user",
        help = "the userID of the user performing this operation"
    )
    private val username: String by option("-u", "--username", help = "the userid of the user to reactivate").required()
    private val serverUrl: String? by option("--server", help = "the URL of the polytope server")

    override fun run() {
        handleCommandErrors("user", "create") {
            val actingUser = asUser ?: requireUserId()
            val actingUserPassword = getOrPromptForPassword()
            val server = serverUrl ?: requireServerUrl()
            val client = RestApiClient(server, actingUser, actingUserPassword)
            runBlocking {
                client.userReactivate(username)
            }
            echo("Successfully reactivated user: $username")
        }
    }
}

class UserDeactivateCommand : PolytopeCommandBase(name = "deactivate", help = "Deactivate a user.") {
    private val asUser: String? by option(
        "-a",
        "--acting-user",
        help = "the userID of the user to act-as when performing this operation"
    )
    private val serverUrl: String? by option("-s", "--server", help = "the URL of the polytope server")
    private val username: String by option("-u", "--username", help = "the userid of the user to deactivate").required()
    override fun run() {
        handleCommandErrors("user", "create") {
            val actingUser = asUser ?: requireUserId()
            val actingUserPassword = getOrPromptForPassword()
            val server = serverUrl ?: requireServerUrl()
            val client = RestApiClient(server, actingUser, actingUserPassword)
            runBlocking {
                client.userDeactivate(username)
            }
            echo("Successfully deactivated user $username")
        }
    }
}

class UserGrantCommand : PolytopeCommandBase(name = "grant", help = "Grant a set of permitted actions to a user.") {
    private val asUser: String? by option(
        "--a",
        "--acting-user",
        help = "The userID of the user to act-as when performing this operation"
    )
    private val username: String by option("-u", "--username", help = "the userid of the user to create").required()
    private val actions: List<String> by option(
        "-x",
        "--permitted",
        help = "an action the user is permitted to perform"
    ).multiple()
    private val serverUrl: String? by option("-s", "--server", help = "The URL of the polytope server")

    override fun run() {
        handleCommandErrors("user", "create") {
            val actingUser = asUser ?: requireUserId()
            val actingUserPassword = getOrPromptForPassword()
            val server = serverUrl ?: requireServerUrl()
            val client = RestApiClient(server, actingUser, actingUserPassword)
            runBlocking {
                client.userGrant(username,
                    actions.map { Action.parse(it) }
                )
            }
            echo("Successfully granted permissions to $username")
        }
    }
}

class UserRevokeCommand :
    PolytopeCommandBase(name = "revoke", help = "Revoke a set of permitted actions from a user.") {
    private val asUser: String? by option(
        "-a",
        "--acting-user",
        help = "the userID of the user performing this operation"
    )
    private val username: String by option("-u", "--username", help = "the userid of the user to create").required()
    private val actions: List<String> by option(
        "-x",
        "--permitted",
        help = "an action the user is not permitted to perform"
    ).multiple()
    private val serverUrl: String? by option("-s", "--server", help = "the URL of the polytope server")

    override fun run() {
        handleCommandErrors("user", "create") {
            val actingUser = asUser ?: requireUserId()
            val actingUserPassword = getOrPromptForPassword()
            val server = serverUrl ?: requireServerUrl()
            val client = RestApiClient(server, actingUser, actingUserPassword)
            runBlocking {
                client.userRevoke(username,
                    actions.map { Action.parse(it) }
                )
            }
            echo("Successfully revoked permissions of user $username")
        }
    }
}

class UserPasswordCommand : PolytopeCommandBase(name = "set-password", help = "Change a user's password.") {
    private val asUser: String? by option(
        "-a",
        "--acting-user",
        help = "the userID of the user performing this operation"
    )
    private val serverUrl: String? by option("-s", "--server", help = "the URL of the polytope server")
    private val username: String? by option("-u", "--username", help = "the userid of the user to create")
    private val password: String? by option("-p", "--password", help = "the new password")
    override fun run() {
        handleCommandErrors("user", "create") {
            val actingUser = asUser ?: getClientConfig()?.userId ?: throw PtException(
                PtException.Kind.UserError,
                "You must either provide a userid, or run this command in a workspace"
            )
            val actingUserPassword = getOrPromptForPassword()
            val server = serverUrl ?: getClientConfig()?.serverUrl ?: throw PtException(
                PtException.Kind.UserError,
                "You must either provide a server URL, or run this command in a workspace"
            )
            val user = username ?: actingUser
            val newPassword = password ?: prompt(
                text = "Enter password: ",
                hideInput = true,
            ) ?: throw PtException(PtException.Kind.Authentication, "could not read user password")
            val client = RestApiClient(server, actingUser, actingUserPassword)
            runBlocking {
                client.userChangePassword(user, newPassword)
            }
            echo("Successfully updated user password for $user")
        }
    }
}

class UserListCommand : PolytopeCommandBase(name = "list", help = "List all users in the depot.") {
    private val asUser: String? by option(
        "-u",
        "--user",
        "--acting-user",
        help = "the userID of the user performing this operation."
    )
    private val serverUrl: String? by option("-s", "--server", help = "the URL of the polytope server")
    private val format: String by option("-f", "--format", help = "the desired output format").choice("text", "json")
        .default("text")

    override fun run() {
        handleCommandErrors("user", "create") {
            val actingUser = asUser ?: requireUserId()
            val actingUserPassword = getOrPromptForPassword()
            val server = serverUrl ?: requireServerUrl()
            val client = RestApiClient(server, actingUser, actingUserPassword)
            val users = runBlocking {
                client.userList()
            }
            if (format == "text") {
                echo(String.format("%-20s|%-30s|%30s|%6s|", "UserId", "Full Name", "Email", "Active"))
                for (u in users.users) {
                    echo(String.format("%-20s|%-30s|%30s|%6s|", u.username, u.fullName, u.email, u.active))
                }
            } else {
                echo(prettyJson(users))
            }
        }
    }
}

class UserGetCommand : PolytopeCommandBase(name = "get", help = "Retrieve and view information about a user account.") {
    private val asUser: String? by option(
        "-a",
        "--acting-user",
        help = "the userID of the user performing this operation"
    )
    private val serverUrl: String? by option("-s", "--server", help = "The URL of the polytope server")
    private val format: String by option("-f", "--format", help = "the desired output format").choice("text", "json")
        .default("text")
    private val username: String by option("-u", "--username", help = "the userid of the user to retrieve").required()
    override fun run() {
        handleCommandErrors("user", "create") {
            val actingUser = asUser ?: requireUserId()
            val actingUserPassword = getOrPromptForPassword()
            val server = serverUrl ?: requireServerUrl()
            val client = RestApiClient(server, actingUser, actingUserPassword)
            val result = runBlocking {
                client.userGet(username)
            }
            if (format == "text") {
                echo("User: ${result.username}")
                echo("Full name: ${result.fullName}")
                echo("Email: ${result.email}")
                echo("Permitted actions: ${result.permittedActions.joinToString(",") { it.render() }}")
                echo("IsActive: ${result.active}")
            }
        }
    }

}