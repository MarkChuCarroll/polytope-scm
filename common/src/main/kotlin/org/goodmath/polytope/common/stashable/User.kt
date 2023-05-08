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
package org.goodmath.polytope.common.stashable

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

import kotlinx.serialization.Serializable
@Serializable
data class AuthenticatedUser(
    val userId: String,
    val authToken: String,
    val permittedActions: List<Action>
)

@Serializable
data class User(
    val username: String,
    val fullName: String,
    val permittedActions: List<Action>,
    val email: String,
    val password: String,
    val timestamp: Long,
    val active: Boolean
)
