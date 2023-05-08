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

import org.goodmath.polytope.TestStub
import org.goodmath.polytope.depot.agents.BaselineAgent
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ProjectsTest: TestStub() {

    @Test
    fun testCanCreateProject() {
        var prj: Project? = null
        assertDoesNotThrow {
            prj = depot.projects.createProject(user, "test-project", "a test project")
        }
        assertNotNull(prj)
        assertEquals("test-project", prj?.name)
        assertEquals("a test project", prj?.description)
        assertEquals("tester", prj?.creator)
        assertTrue(prj?.baseline?.startsWith("art(baseline)")?:false)
    }

    @Test
    fun testCanRetrieveProject() {
        depot.projects.createProject(user, "test-project", "a test project")
        val retrieved = depot.projects.retrieveProject(user,"test-project")
        val history = depot.histories.retrieveHistory(user, "test-project", "main")
        assertEquals(1, history.steps.size)
        val histStep = depot.histories.retrieveHistoryStep(user, "test-project", "main", 0)
        assertEquals(histStep.baselineId, retrieved.baseline)
        val baselineArt = depot.artifacts.retrieveVersion(user, "test-project", histStep.baselineId, histStep.baselineVersionId)
        val baseline = BaselineAgent.decodeFromString(baselineArt.content)
        assertEquals(baseline.rootDir, retrieved.rootDir)
        assertEquals(1, baseline.entries.size)
    }

}