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
import org.goodmath.polytope.depot.Depot
import org.goodmath.polytope.depot.agents.BaselineAgent
import org.goodmath.polytope.depot.agents.DirectoryAgent
import org.goodmath.polytope.depot.agents.text.Text
import org.goodmath.polytope.depot.agents.text.TextAgent
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DepotTest: TestStub() {

    fun makeAndStoreBaseTextVersion(
        auth: AuthenticatedUser,
        testProject: String,
        depot: Depot
    ): ArtifactVersion {
        val textContent = Text(
            listOf(
                "hello\n",
                "there\n",
                "you\n",
                "bozo\n"
            )
        )
        val (_, tVer) = depot.artifacts.createArtifact(
            auth,
            testProject,
            TextAgent.artifactType,
            TextAgent.encodeToString(textContent),
            emptyMap()
        )

        return tVer
    }


    @Test
    fun `should be able to create a new project`() {
        val auth = depot.users.authenticate("tester", "tester tests")
        assertDoesNotThrow {
            depot.projects.createProject(
                auth,
                "testie",
                "a test",
            )
        }
    }

    @Test
    fun `it should be able to retrieve a project`() {
        val auth = depot.users.authenticate("tester", "tester tests")
        depot.projects.createProject(auth, "testie", "a test")
        val pr = depot.projects.retrieveProject(auth, "testie")
        assertEquals("testie", pr.name)
        assertDoesNotThrow {
            val hist = depot.histories.retrieveHistory(auth, pr.name, "main")
            val hver = depot.histories.retrieveHistoryStep(
                auth,
                pr.name,
                hist.name,
                hist.steps.size - 1
            )
            assertEquals(hver.historyName, hist.name)
            val baselineVer = depot.artifacts.retrieveVersion(
                auth,
                pr.name,
                hver.baselineId,
                hver.baselineVersionId
            )
            val baseline = BaselineAgent.decodeFromString(baselineVer.content)
            assertEquals(1, baseline.entries.size)
            assertNotNull(baseline.get(baseline.rootDir))
            val rootDirVer = depot.artifacts.retrieveVersion(
                auth,
                pr.name,
                baseline.rootDir,
                baseline.get(baseline.rootDir)!!
            )
            val dir = DirectoryAgent.decodeFromString(rootDirVer.content)
            assertEquals(dir.entries.size, 0)
        }
    }


    @Test
    fun `it should be able to create text objects in a directory in a project`() {
        val auth = depot.users.authenticate("tester", "tester tests")
        depot.projects.createProject(auth, "testie", "a test")
        val pr = depot.projects.retrieveProject(auth, "testie")
        val hist = depot.histories.retrieveHistory(auth, pr.name, "main")
        val hver = depot.histories.retrieveHistoryStep(
            auth,
            pr.name,
            hist.name
        )
        val baselineVer = depot.artifacts.retrieveVersion(
            auth,
            pr.name,
            hver.baselineId,
            hver.baselineVersionId
        )
        val baseline = BaselineAgent.decodeFromString(baselineVer.content)
        val rootDirVer = depot.artifacts.retrieveVersion(
            auth,
            pr.name,
            baseline.rootDir,
            baseline.get(baseline.rootDir)!!
        )
        val dir = DirectoryAgent.decodeFromString(rootDirVer.content)
        val someText = Text(listOf("aaa\n", "bbb\n", "ccc\n", "ddd\n"))
        val (textArt, textVer) = depot.artifacts.createArtifact(
            auth,
            TextAgent.artifactType,
            pr.name,
            TextAgent.encodeToString(someText),
            mapOf("order" to "lexical")
        )
        val newDir = dir.copy()
        newDir.addBinding("txt.txt", textVer.artifactId)
        baseline.add(textVer.artifactId, textVer.id)
        val newDirVersion = depot.artifacts.createVersion(
            auth,
            pr.name,
            rootDirVer.artifactId,
            DirectoryAgent.artifactType,
            DirectoryAgent.encodeToString(newDir),
            listOf(rootDirVer.id),
            emptyMap()
        )
        baseline.change(newDirVersion.artifactId, newDirVersion.id)
        val newBaselineVersion = depot.artifacts.createVersion(
            auth,
            pr.name,
            baselineVer.artifactId,
            BaselineAgent.artifactType,
            BaselineAgent.encodeToString(baseline),
            listOf(baselineVer.id),
            emptyMap()
        )

        val baselineVerFromStorage = depot.artifacts.retrieveVersion(
            auth,
            pr.name,
            hver.baselineId,
            newBaselineVersion.id
        )
        val baselineFromStorage = BaselineAgent.decodeFromString(
            baselineVerFromStorage.content
        )
        val dirVersionId = baselineFromStorage.get(baselineFromStorage.rootDir)
        assertNotNull(dirVersionId)
        val dirVersionFromStorage = depot.artifacts.retrieveVersion(
            auth,
            pr.name,
            baselineFromStorage.rootDir,
            dirVersionId
        )
        val dirContents = DirectoryAgent.decodeFromString(
            dirVersionFromStorage.content
        )
        val txtId = dirContents.getBinding("txt.txt")
        assertNotNull(txtId)
        assertEquals(textArt.id, txtId)
        assertEquals(textVer.id, baselineFromStorage.get(txtId))
        val txtFromStorage = depot.artifacts.retrieveVersion(
            auth,
            pr.name,
            txtId,
            baselineFromStorage.get(txtId)!!
        )
        assertEquals("lexical", txtFromStorage.metadata["order"])
    }

    @Test
    fun `it should be able to create a new history version`() {
        val auth = depot.users.authenticate("tester", "tester tests")
        depot.projects.createProject(auth, "testie", "a test")

        val pr = depot.projects.retrieveProject(auth, "testie")
        val hist = depot.histories.retrieveHistory(auth, pr.name, "main")
        val hver = depot.histories.retrieveHistoryStep(
            auth,
            pr.name,
            hist.name
        )
        val baselineVer = depot.artifacts.retrieveVersion(
            auth,
            pr.name,
            hver.baselineId,
            hver.baselineVersionId
        )
        val baseline = BaselineAgent.decodeFromString(baselineVer.content)
        val rootDirVer = depot.artifacts.retrieveVersion(
            auth,
            pr.name,
            baseline.rootDir,
            baseline.get(baseline.rootDir)!!
        )
        val dir = DirectoryAgent.decodeFromString(rootDirVer.content)

        val change = depot.changes.createChange(
            auth = auth,
            projectName = pr.name,
            history = "main",
            changeName = "test-change",
            basis = ProjectVersionSpecifier.history(pr.name, "main", 1),
            description = "a test"
        )

        val someText = Text(listOf("aaa\n", "bbb\n", "ccc\n", "ddd\n"))
        val (textArt, textVer) = depot.artifacts.createArtifact(
            auth,
            pr.name,
            TextAgent.artifactType,
            TextAgent.encodeToString(someText),
            mapOf("language" to "english")
        )

        val newDir = dir.copy()
        newDir.addBinding("txt.txt", textVer.artifactId)
        baseline.add(textVer.artifactId, textVer.id)
        val newDirVersion = depot.artifacts.createVersion(
            auth = auth,
            project = pr.name,
            artifactType = DirectoryAgent.artifactType,
            content = DirectoryAgent.encodeToString(newDir),
            parents = listOf(rootDirVer.id),
            artifactId = rootDirVer.artifactId,
            metadata = emptyMap()
        )
        baseline.change(newDirVersion.artifactId, newDirVersion.id)
        val newBaselineVersion = depot.artifacts.createVersion(
            auth = auth,
            project = pr.name,
            artifactId = baselineVer.artifactId,
            artifactType = BaselineAgent.artifactType,
            parents = listOf(baselineVer.id),
            content = BaselineAgent.encodeToString(baseline),
            metadata = emptyMap()
        )

        depot.changes.createSavePoint(
            auth = auth,
            project = pr.name,
            history = "main",
            changeName = change.name,
            changedArtifacts = listOf(textArt.id, rootDirVer.artifactId),
            description = "a test change",
            basis = ProjectVersionSpecifier.baseline(pr.name, "main", newBaselineVersion.id),
            baselineVersion = newBaselineVersion.id
        )

        depot.changes.updateChangeStatus(
            auth, pr.name, "main", "test-change",
            ChangeStatus.Closed
        )

        depot.histories.addHistoryStep(
            auth = auth,
            project = pr.name,
            history = "main",
            change = change.id,
            baselineVersion = newBaselineVersion,
            description = "desc"
        )


        depot.histories.retrieveHistoryStep(auth, pr.name, "main")

        val baselineVerFromStorage = depot.artifacts.retrieveVersion(
            auth,
            pr.name,
            newBaselineVersion.artifactId,
            newBaselineVersion.id
        )

        val rbaselineFromStorage = BaselineAgent.decodeFromString(
            baselineVerFromStorage.content
        )

        val dirVersionId = rbaselineFromStorage.get(rbaselineFromStorage.rootDir)
        val dirVersionFromStorage = depot.artifacts.retrieveVersion(
            auth,
            pr.name,
            rbaselineFromStorage.rootDir,
            dirVersionId!!
        )
        val dirContents = DirectoryAgent.decodeFromString(
            dirVersionFromStorage.content
        )
        val txtId = dirContents.getBinding("txt.txt")
        assertNotNull(txtId)

        assertEquals(textArt.id, txtId)
        assertEquals(textVer.id, rbaselineFromStorage.get(txtId))

        val txtFromStorage = depot.artifacts.retrieveVersion(
            auth,
            pr.name,
            txtId,
            rbaselineFromStorage.get(txtId)!!
        )
        assertEquals("english", txtFromStorage.metadata["language"])
        assertEquals("aaa\nbbb\nccc\nddd\n", txtFromStorage.content)
    }

    @Test
    fun `should be able to branch new histories`() {
        val auth = depot.users.authenticate("tester", "tester tests")
        depot.projects.createProject(auth, "testie", "a test")
        val pr = depot.projects.retrieveProject(auth, "testie")
        val hist = depot.histories.retrieveHistory(auth, pr.name, "main")
        val hver = depot.histories.retrieveHistoryStep(
            auth,
            pr.name,
            hist.name,
        )
        val baselineVer = depot.artifacts.retrieveVersion(
            auth,
            pr.name,
            hver.baselineId,
            hver.baselineVersionId
        )
        val baseline = BaselineAgent.decodeFromString(baselineVer.content)
        val rootDirVer = depot.artifacts.retrieveVersion(
            auth,
            pr.name,
            baseline.rootDir,
            baseline.get(baseline.rootDir)!!
        )
        val dir = DirectoryAgent.decodeFromString(rootDirVer.content)

        val change = depot.changes.createChange(
            auth,
            pr.name,
            "main",
            "test-change",
            ProjectVersionSpecifier.history(pr.name, "main", 1),
            "a test",
        )

        val someText = Text(listOf("aaa\n", "bbb\n", "ccc\n", "ddd\n"))
        val (textArt, textVer) = depot.artifacts.createArtifact(
            auth = auth,
            artifactType = TextAgent.artifactType,
            project = pr.name,
            metadata = mapOf("language" to "english", "order" to "lexical"),
            initialContent = TextAgent.encodeToString(someText),
        )
        val newDir = dir.copy()
        newDir.addBinding("txt.txt", textVer.artifactId)
        baseline.add(textVer.artifactId, textVer.id)
        val newDirVersion = depot.artifacts.createVersion(
            auth = auth,
            project = pr.name,
            artifactType = DirectoryAgent.artifactType,
            artifactId = rootDirVer.artifactId,
            parents = listOf(rootDirVer.id),
            content = DirectoryAgent.encodeToString(newDir),
            metadata = emptyMap()
        )
        baseline.change(newDirVersion.artifactId, newDirVersion.id)
        val newBaselineVersion = depot.artifacts.createVersion(
            auth = auth,
            project = pr.name,
            artifactId = baselineVer.artifactId,
            artifactType = BaselineAgent.artifactType,
            parents = listOf(baselineVer.id),
            content = BaselineAgent.encodeToString(baseline),
            metadata = emptyMap()
        )

        depot.changes.createSavePoint(
            auth = auth,
            project = pr.name,
            changeName = change.name,
            changedArtifacts = listOf(newDirVersion.id, textArt.id),
            baselineVersion = newBaselineVersion.id,
            description = "a test change",
            history = "main",
            basis = ProjectVersionSpecifier.baseline(pr.name, "main", newBaselineVersion.id)
        )
        depot.changes.updateChangeStatus(
            auth = auth,
            project = pr.name,
            changeName = change.name,
            history = "main",
            status = ChangeStatus.Closed)
        depot.histories.addHistoryStep(
            auth = auth,
            project = pr.name,
            history = "main",
            change = change.id,
            baselineVersion = newBaselineVersion,
            description = "desc"
        )

        depot.histories.createHistory(
            auth = auth,
            project = pr.name,
            name = "alternate",
            description = "testing a branched history",
            fromHistory = "main",
            atStep = 1
        )


         depot.histories.retrieveHistoryStep(
            auth = auth,
            project = pr.name,
            historyName = "alternate"
        )

        val baselineVerFromStorage = depot.artifacts.retrieveVersion(
            auth,
            pr.name,
            newBaselineVersion.artifactId,
            newBaselineVersion.id
        )
        val baselineFromStorage = BaselineAgent.decodeFromString(
            baselineVerFromStorage.content
        )

        val dirVersionId = baselineFromStorage.get(baselineFromStorage.rootDir)
        assertNotNull(dirVersionId)
        val dirVersionFromStorage = depot.artifacts.retrieveVersion(
            auth,
            pr.name,
            baselineFromStorage.rootDir,
            dirVersionId
        )
        assertNotNull(dirVersionFromStorage)
        val dirContents = DirectoryAgent.decodeFromString(
            dirVersionFromStorage.content
        )
        val txtId = dirContents.getBinding("txt.txt")
        assertNotNull(txtId)
        assertEquals(textArt.id, txtId)
        assertEquals(textVer.id, baselineFromStorage.get(txtId))

        val txtFromStorage = depot.artifacts.retrieveVersion(
            auth,
            pr.name,
            txtId,
            baselineFromStorage.get(txtId)!!
        )
        assertEquals("lexical", txtFromStorage.metadata["order"])
        assertEquals("aaa\nbbb\nccc\nddd\n", txtFromStorage.content)
    }

}