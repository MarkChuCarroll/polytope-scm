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

package org.goodmath.polytope

import org.goodmath.polytope.common.stashable.Action
import org.goodmath.polytope.common.stashable.ActionLevel
import org.goodmath.polytope.common.stashable.ActionScopeType
import org.goodmath.polytope.common.stashable.AuthenticatedUser
import org.goodmath.polytope.depot.Depot
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.io.File

/**
 * Lots of tests need to set up empty depots, so this stub
 * provides the basic functionality in one, reusable location.
 */
open class TestStub {
    lateinit var user: AuthenticatedUser

    private val cfg = Config(
        rootUser = "root",
        rootEmail = "root@root.root",
        password = "rootrootroot",
        dbPath = "testdbpath"
    )

    lateinit var depot: Depot

    @BeforeEach
    fun setupDepot() {
        val dbFile = File(cfg.dbPath)
        if (dbFile.exists()) {
            dbFile.deleteRecursively()
        }
        Depot.initializeStorage(cfg)
        depot = Depot(cfg)
        val root = depot.users.authenticate(cfg.rootUser, cfg.password)
        depot.users.create(
            root, "tester",
            "a tester droid",
            "tester@test.test",
            listOf(Action(ActionScopeType.Depot, "*", ActionLevel.Write)),
            "tester tests"
        )
        user = depot.users.authenticate("tester", "tester tests")
    }

    @AfterEach
    fun close() {
        depot.close()
    }
}