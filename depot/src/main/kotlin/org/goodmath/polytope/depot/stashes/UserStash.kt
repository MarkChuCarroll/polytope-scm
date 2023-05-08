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
import org.goodmath.polytope.Config
import org.goodmath.polytope.common.*
import org.goodmath.polytope.common.stashable.*
import org.goodmath.polytope.depot.Depot
import org.goodmath.polytope.depot.util.*
import org.rocksdb.ColumnFamilyHandle
import java.security.MessageDigest
import java.time.Instant
import java.util.*
import kotlin.text.Charsets.UTF_8


class UserStash(
    private val db: RocksDB,
    private val usersColumn: ColumnFamilyHandle,
    private val tokensColumn: ColumnFamilyHandle,
    private val depot: Depot):
    Stash {


    private val ONE_DAY: Long = 24 * 60 * 60 * 1000
    private val ONE_WEEK: Long = ONE_DAY * 7

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
        val salt = (username.chars().reduce { x, y ->
            (x * 37 + y) / 211
        }).toString()
        val bytes = MessageDigest.getInstance("MD5").digest((salt + pw).toByteArray(UTF_8))
        return bytes.joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
    }

    data class AuthEntry(
        val user: AuthenticatedUser,
        val token: String,
        val expiration: Long
    )

    fun generateAuthToken(auth: AuthenticatedUser): Pair<String, Long> {
        val existingAuth: AuthEntry? = db.getTyped(tokensColumn, auth.userId)
        if (existingAuth != null) {
            if (existingAuth.expiration > (Instant.now().toEpochMilli()
                        + ONE_DAY)
            ) {
                return Pair(existingAuth.token, existingAuth.expiration)
            } else {
                db.delete(tokensColumn, auth.userId.toByteArray())
            }
        }
        val uuid = UUID.randomUUID().toString()
        val token = "${auth.userId}/$uuid"
        val exp = Instant.now().toEpochMilli() + ONE_WEEK
        val entry = AuthEntry(auth, token, exp)
        db.putTyped(tokensColumn, auth.userId, entry)
        return Pair(token, exp)
    }

    fun validateAuthToken(userId: String, token: String): AuthenticatedUser? {
        val entry: AuthEntry? = db.getTyped(tokensColumn, userId)
        return entry?.let {
            if (it.token == token && it.expiration > Instant.now().toEpochMilli()) {
                entry.user
            } else {
                null
            }
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
        return AuthenticatedUser(username, hashedFromUser, user.permittedActions)
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
        permittedActions: List<Action>,
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
            permittedActions,
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
                       userId: String): User {
        validatePermissions(auth, Action.adminUsers)
        val user = getUser(userId) ?: throw PtException(PtException.Kind.NotFound,
            "User $userId not found")
        val updated = user.copy(active = false)
        db.updateTyped(usersColumn, userId, updated)
        return user.copy(password = "<redacted>")
    }

    fun reactivateUser(auth: AuthenticatedUser,
                       userId: String): User {
        validatePermissions(auth, Action.adminUsers)
        val user = getUser(userId) ?: throw PtException(PtException.Kind.NotFound,
            "User $userId not found")
        val updated = user.copy(active = true)
        db.updateTyped(usersColumn, userId, updated)
        return user.copy(password = "<redacted>")
    }

    fun grantPermissions(
        auth: AuthenticatedUser,
        username: String, perms: List<Action>
    ): User {
        validatePermissions(auth, Action.adminUsers)
        val u = retrieveUser(auth, username)
        val newPermissions = (u.permittedActions + perms).distinct()
        db.updateTyped(usersColumn, username, u.copy(permittedActions = newPermissions))
        return u.copy(password = "<redacted>")
    }

    fun revokePermission(
        auth: AuthenticatedUser,
        username: String, perms: List<Action>
    ): User {
        validatePermissions(auth, Action.adminUsers)
        val u = retrieveUser(auth, username)
        val newPermissions = u.permittedActions - perms.toSet()
        db.updateTyped(usersColumn, username, u.copy(permittedActions = newPermissions))
        return u.copy(password = "<redacted>")
    }

    fun list(auth: AuthenticatedUser): List<User> {
        validatePermissions(auth, Action.readUsers)
        return db.listAllInColumn<User>(usersColumn)
    }

    override fun initStorage(config: Config) {
        val root = User(
            username = config.rootUser,
            fullName = "The Dreaded Administrator",
            permittedActions = listOf(Action(ActionScopeType.Global, "*", ActionLevel.Admin)),
            email = config.rootEmail,
            password = saltedHash(config.rootUser, config.password),
            timestamp = Instant.now().toEpochMilli(),
            active = true
        )
        db.putTyped(usersColumn, root.username, root)
    }

    fun updatePassword(auth: AuthenticatedUser, userId: String, password: String): User {
        if (auth.userId != userId) {
            validatePermissions(auth, Action.adminUsers)
        }
        val user = db.getTyped<User>(usersColumn, userId)
            ?: throw PtException(PtException.Kind.NotFound,
                "User $userId not found")
        val salted = saltedHash(userId, password)
        val updated = user.copy(password = salted)
        db.updateTyped(usersColumn, userId, updated)
        return user.copy(password = "<redacted>")
    }
}

