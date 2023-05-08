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

package org.goodmath.polytope.depot.stashes

import maryk.rocksdb.RocksDB
import org.goodmath.polytope.PtException
import org.goodmath.polytope.depot.Stash
import org.goodmath.polytope.depot.Config
import org.goodmath.polytope.depot.Depot
import org.goodmath.polytope.depot.util.*
import org.rocksdb.ColumnFamilyHandle
import java.security.MessageDigest
import java.time.Instant
import kotlin.text.Charsets.UTF_8


/**
 * An authenticated user. This is passed around the server code to represent
 * an authenticated user, and determine if they have the correct permissions
 * to perform operations.
 *
 * From a security perspective, this needs work. The current auth token is
 * the salted password retrieved from the depot. A bad actor, such as
 * a rogue agent, could retrieve that, and have full access to anything they wanted.
 * There should be some mechanism here that isn't accessible from outside the users
 * stash for creating a token that we can quickly validate to ensure that
 * the auth token is genuine.
 */
data class AuthenticatedUser(
    val userId: String,
    val authToken: String,
    val permissions: List<Action>
)


data class User(
    val username: String,
    val fullName: String,
    val permissions: List<Action>,
    val email: String,
    val password: String,
    val timestamp: Long,
    val active: Boolean
)

class UserStash(
    private val db: RocksDB,
    private val usersColumn: ColumnFamilyHandle,
    private val depot: Depot):
    Stash {

    /**
     * Test if a user has permission to perform an action.
     * @param auth the authenticated user.
     * @param action the requested action.
     * @throws PtException an authorization exception if the operation isn't allowed.
     * TODO: this should check that the auth token is valid.
     */
    fun validatePermissions(auth: AuthenticatedUser, action: Action) {
        if (!action.permittedFor(auth)) {
            throw PtException(
                PtException.Kind.Permission,
                "User permission denied"
            )
        }
    }

    /**
     * Compute the salted hash of a user password for authentication.
     */
    private fun saltedHash(username: String, pw: String): String {
        // This is trash, but it's a placeholder fol now.
        val salt = (username.chars().reduce { x, y ->
            (x * 37 + y) / 211
        }).toString()
        val bytes = MessageDigest.getInstance("MD5").digest((salt + pw).toByteArray(UTF_8))
        return bytes.joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
    }

    /**
     * Authenticate a user.
     */
    fun authenticate(
        username: String,
        password: String
    ): AuthenticatedUser {
        val user = db.getTyped<User>(usersColumn, username)
            ?: throw PtException(
                PtException.Kind.NotFound,
                "User $username not found"
            )
        if (!user.active) {
            throw PtException(PtException.Kind.Authentication,
                "Authentication failed for user $username")
        }
        val hashedFromUser = saltedHash(username, password)
        if (hashedFromUser != user.password) {
            throw PtException(
                PtException.Kind.Authentication,
                "Authentication failed"
            )
        }

        return AuthenticatedUser(username, hashedFromUser, user.permissions)
    }

    fun retrieveUser(
        auth: AuthenticatedUser,
        username: String
    ): User {
        if (auth.userId != username) {
            validatePermissions(
                auth, Action.adminUsers)
        }

        val result = db.getTyped<User>(usersColumn, username)
            ?: throw PtException(
                PtException.Kind.NotFound,
                "User $username not found"
            )
        return result.copy(password = "<redacted>")
    }

    fun create(
        auth: AuthenticatedUser,
        userName: String,
        fullName: String,
        email: String,
        permissions: List<Action>,
        password: String
    ): User {
        validatePermissions(auth, Action.adminUsers)

        if (userExists(auth, userName)) {
            throw PtException(
                PtException.Kind.InvalidParameter,
                "User with username '${userName} already exists"
            )
        }

        val encodedPassword = saltedHash(
            userName,
            password
        )

        val user = User(
            userName,
            fullName,
            permissions,
            email,
            encodedPassword,
            Instant.now().toEpochMilli(),
            true
        )

        db.putTyped(usersColumn, user.username, user)
        return user.copy(password = "<redacted>")
    }

    private fun userExists(
        auth: AuthenticatedUser,
        username: String
    ): Boolean {
        validatePermissions(auth, Action.readUsers)
        val u = db.getTyped<User>(username)
        return u != null
    }

    /**
     * An internal get user - for update operations, we need
     * to retrieve a user record without redacting the password
     */
    private fun getUser(userId: String): User? {
        return db.getTyped<User>(usersColumn, userId)
    }

    fun deactivateUser(auth: AuthenticatedUser,
                       userId: String) {
        validatePermissions(auth, Action.adminUsers)
        val user = getUser(userId) ?: throw PtException(PtException.Kind.NotFound,
            "User $userId not found")
        val updated = user.copy(active = false)
        db.updateTyped(usersColumn, userId, updated)
    }

    fun reactivateUser(auth: AuthenticatedUser,
                       userId: String) {
        validatePermissions(auth, Action.adminUsers)
        val user = getUser(userId) ?: throw PtException(PtException.Kind.NotFound,
            "User $userId not found")
        val updated = user.copy(active = true)
        db.updateTyped(usersColumn, userId, updated)
    }

    fun grantPermissions(
        auth: AuthenticatedUser,
        username: String, perms: List<Action>
    ) {
        validatePermissions(auth, Action.adminUsers)
        val u = retrieveUser(auth, username)
        val newPermissions = (u.permissions + perms).distinct()
        db.updateTyped(usersColumn, username, u.copy(permissions = newPermissions))
    }

    fun revokePermission(
        auth: AuthenticatedUser,
        username: String, perms: List<Action>
    ) {
        validatePermissions(auth, Action.adminUsers)
        val u = retrieveUser(auth, username)
        val newPermissions = u.permissions - perms.toSet()
        db.updateTyped(usersColumn, username, u.copy(permissions = newPermissions))
    }

    fun list(auth: AuthenticatedUser): List<User> {
        validatePermissions(auth, Action.readUsers)
        return db.listAllInColumn<User>(usersColumn)
    }

    override fun initStorage(config: Config) {
        val root = User(
            username = config.rootUser,
            fullName = "The Dreaded Administrator",
            permissions = listOf(Action(ActionScopeType.Global, "*", ActionLevel.Admin)),
            email = config.rootEmail,
            password = saltedHash(config.rootUser, config.password),
            timestamp = Instant.now().toEpochMilli(),
            active = true
        )
        db.putTyped(usersColumn, root.username, root)
    }
}

