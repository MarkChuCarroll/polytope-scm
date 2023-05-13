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

package org.goodmath.polytope.workspace

import org.goodmath.polytope.TestStub
import org.goodmath.polytope.common.agents.Directory
import org.goodmath.polytope.common.agents.DirectoryAgent
import org.goodmath.polytope.common.agents.text.Text
import org.goodmath.polytope.common.agents.text.TextAgent
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals


class WorkspaceBasicsTest: TestStub() {
    @Test
    fun `it should be able to create a new workspace`() {
        depot.projects.createProject(user, "test", "a test project")
        assertDoesNotThrow {
            val ws = depot.workspaces.createWorkspace(
                user, "test",  "main", "mytest",
                "a test workspace"
            )
            assertEquals(ws.name, "mytest")
        }
    }

    @Test
    fun `it should be able to create and save files in a workspace`() {
        val project = depot.projects.createProject(user, "test", "a test project")
        val ws = depot.workspaces.createWorkspace(
            user, "test", "main", "mytest",
            "a test workspace"
        )
        val ch = depot.workspaces.createChange(user, ws, "main", "test-change", "just a test")
        val newFileId = depot.workspaces.addFile(user, ws, "foo", TextAgent.artifactType, TextAgent.encodeToString(Text(listOf("11\n", "22\n", "33\n"))))
        val sp = depot.workspaces.save(user, ws,"test", emptyList())

        val paths = depot.workspaces.listPaths(user, ws)
        assertEquals(setOf("", "foo"), paths.toSet())
        val rsp = depot.changes.retrieveSavePoint(user, project.name,"main", "test-change",
            sp.id)
        assertContains(rsp.modifiedArtifacts, newFileId)
        assertEquals(rsp.changeId, ch.id)
        val baseline = depot.workspaces.currentBaseline(user, ws)
            assertContentEquals(listOf(newFileId, baseline.rootDir).sorted(), rsp.modifiedArtifacts.sorted())
        val rch = depot.changes.retrieveChangeByName(user, project.name, "main", "test-change")
        assertContains(rch.savePoints, rsp.id)
    }

    @Test
    fun `it should be able to handle a directory hierarchy`() {
        depot.projects.createProject(user, "test", "a test project")
        val ws = depot.workspaces.createWorkspace(
            user, "test", "main", "mytest",
            "a test workspace"
        )
        depot.workspaces.createChange(user, ws,"main", "test-change", "just a test")
        val dirDir = depot.workspaces.addFile(user, ws,"dir", DirectoryAgent.artifactType, DirectoryAgent.encodeToString(
            Directory()
        ))
        val dirRid = depot.workspaces.addFile(user, ws,"rid", DirectoryAgent.artifactType, DirectoryAgent.encodeToString(Directory()))
        depot.workspaces.addFile(user, ws,"dir/boo", DirectoryAgent.artifactType, DirectoryAgent.encodeToString(Directory()))
        depot.workspaces.addFile(user, ws,"dir/boo/text.txt", TextAgent.artifactType, TextAgent.encodeToString(Text(listOf("just some text\t",
            "boring\n",
            "not interesting\n"))))
        val dull = depot.workspaces.addFile(user, ws,"rid/blah.txt", TextAgent.artifactType, TextAgent.encodeToString(
            Text(listOf(
                "I didn't realize\n",
                "but that last one\n",
                "was more interesting than this one\n"))))


        val sp = depot.workspaces.save(user, ws,"test", emptyList())
        val paths = depot.workspaces.listPaths(user, ws)
        assertEquals(setOf("", "dir", "dir/boo", "dir/boo/text.txt", "rid", "rid/blah.txt"), paths.toSet())
        depot.workspaces.moveFile(user, ws,"dir/boo", "rid/boo")

        val postMovePaths = depot.workspaces.listPaths(user, ws)
        assertEquals(setOf("", "dir", "rid/boo", "rid/boo/text.txt", "rid", "rid/blah.txt"), postMovePaths.toSet())

        depot.workspaces.moveFile(user, ws,"rid/blah.txt", "rid/bleh.txt")
        assertEquals(setOf("", "dir", "rid/boo", "rid/boo/text.txt", "rid", "rid/bleh.txt"), depot.workspaces.listPaths(user, ws).toSet())

        depot.workspaces.deleteFile(user, ws,"rid/bleh.txt")
        assertEquals(setOf("", "dir", "rid/boo", "rid/boo/text.txt", "rid"), depot.workspaces.listPaths(user, ws).toSet())

        val sp2 = depot.workspaces.save(user, ws,"another save point.", emptyList())
        assertEquals(setOf(dull, dirDir, dirRid), sp2.modifiedArtifacts.toSet())
        assertEquals(sp.id, sp2.basis.savePointId)
    }

}