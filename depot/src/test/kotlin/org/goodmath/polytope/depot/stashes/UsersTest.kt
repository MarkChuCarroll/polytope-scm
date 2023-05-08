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

import org.goodmath.polytope.Config
import org.goodmath.polytope.common.PtException
import org.goodmath.polytope.common.stashable.Action
import org.goodmath.polytope.common.stashable.ActionLevel
import org.goodmath.polytope.common.stashable.ActionScopeType
import org.goodmath.polytope.depot.Depot
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import java.io.File

class UsersTest {
    private val cfg = Config(
        rootUser = "root",
        rootEmail = "root@root.root",
        password = "rootrootroot",
        dbPath = "testdbpath"
    )
    private lateinit var depot: Depot

    @BeforeEach
    fun setupDepot() {
        val dbFile = File(cfg.dbPath)
        if (dbFile.exists()) {
            dbFile.deleteRecursively()
        }
        Depot.initializeStorage(cfg)
        depot = Depot(cfg)
    }

    @AfterEach
    fun close() {
        depot.close()
    }

    @Test
    fun testRootUserIsRetrievable() {
        assertDoesNotThrow {depot.users.authenticate("root", "rootrootroot")}
    }

    @Test
    fun testRootUserHasAdminPermissions() {
        val root = depot.users.authenticate(cfg.rootUser, cfg.password)
        assertTrue(Action.readProject("foo").permittedFor(root))
        assertTrue(Action.writeProject("foo").permittedFor(root))
        assertTrue(Action.deleteProject("foo").permittedFor(root))
        assertTrue(Action.adminUsers.permittedFor(root))
    }

    @Test
    fun testRootUserCanCreateOtherUsers() {
        val root = depot.users.authenticate(cfg.rootUser, cfg.password)
        val newUser = depot.users.create(root, "tester",
            "a tester droid",
            "tester@test.test",
            listOf(Action(ActionScopeType.Project, "foo", ActionLevel.Write)),
            "tester tests")
        val retrieved = depot.users.retrieveUser(root, "tester")
        assertEquals(newUser, retrieved)
    }

    @Test
    fun newUserCanAuthenticate() {
        val root = depot.users.authenticate(cfg.rootUser, cfg.password)
        val newUser = depot.users.create(root, "tester",
            "a tester droid",
            "tester@test.test",
            listOf(Action(ActionScopeType.Project, "foo", ActionLevel.Write)),
            "tester tests")
        val tester = depot.users.authenticate("tester", "tester tests")
        assertEquals(newUser.permittedActions, tester.permittedActions)
    }

    @Test
    fun testRootUserCanDisable() {
        val root = depot.users.authenticate(cfg.rootUser, cfg.password)
        depot.users.create(root, "tester",
            "a tester droid",
            "tester@test.test",
            listOf(Action(ActionScopeType.Project, "foo", ActionLevel.Write)),
            "tester tests")
        depot.users.deactivateUser(root, "tester")
        assertThrows(PtException::class.java)
            { depot.users.authenticate("tester", "tester tests") }
    }

}