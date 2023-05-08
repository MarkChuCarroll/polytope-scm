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

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ActionTest {
    @Test
    fun testValidateSingleReadPermission() {
        val perm = Action(ActionScopeType.Project, "foo", ActionLevel.Read)
        val auth = AuthenticatedUser("markcc", "token", listOf(perm))
        assertTrue(Action.readProject("foo").permittedFor(auth))
        assertFalse(Action.writeProject("foo").permittedFor(auth))
        assertFalse(Action.adminUsers.permittedFor(auth))
    }

    @Test
    fun testValidateSingleWritePermission() {
        val perm = Action(ActionScopeType.Project, "foo", ActionLevel.Write)

        val auth = AuthenticatedUser("markcc", "token", listOf(perm))
        assertTrue(
            Action.readProject("foo").permittedFor(auth))

        assertTrue(Action.writeProject("foo").permittedFor(auth))
        assertFalse(Action.adminUsers.permittedFor(auth))
    }

    @Test
    fun testValidateSingleAdminPermission() {
        val perm = Action(ActionScopeType.Global, "*", ActionLevel.Admin)
        val auth = AuthenticatedUser("root", "token", listOf(perm))
        assertTrue(Action.readProject("foo").permittedFor(auth))
        assertTrue(Action.writeProject("foo").permittedFor(auth))
        assertTrue(Action.adminUsers.permittedFor(auth))
    }

    @Test
    fun testValidateMultiplePermissions() {
        val perms = listOf(
            Action(ActionScopeType.Project, "foo", ActionLevel.Admin),
            Action(ActionScopeType.Project, "bar", ActionLevel.Read),
            Action(ActionScopeType.Project, "twip", ActionLevel.Write))

        val auth = AuthenticatedUser("markcc", "token", perms)
        assertTrue(Action.readProject("foo").permittedFor(auth))
        assertTrue(Action.writeProject("foo").permittedFor(auth))
        assertTrue(Action.readProject("bar").permittedFor(auth))
        assertFalse(Action.writeProject("bar").permittedFor(auth))
        assertTrue(Action.readProject("twip").permittedFor(auth))
        assertTrue(Action.writeProject("twip").permittedFor(auth))
        assertTrue(Action.readProject("twip").permittedFor(auth))
        assertTrue(Action.adminProject("foo").permittedFor(auth))
        assertFalse(Action.adminProject("bar").permittedFor(auth))
        assertFalse(Action.adminProject("twip").permittedFor(auth))
        assertFalse(Action.adminUsers.permittedFor(auth))
    }

    @Test
    fun testValidateAdminUniversePermission() {
        val perms = listOf(
            Action(ActionScopeType.Project, "foo", ActionLevel.Read),
            Action(ActionScopeType.Global, "*", ActionLevel.Admin))
        val auth = AuthenticatedUser("markcc", "token", perms)
        assertTrue(
            Action.readProject("foo").permittedFor(auth))
        assertTrue(
            Action.writeProject("foo").permittedFor(auth))
        assertTrue(
            Action.deleteProject("bar").permittedFor(auth))
        assertTrue(Action.adminUsers.permittedFor(auth))
        assertTrue(
            Action.adminProject("foo").permittedFor(auth))
    }
}
