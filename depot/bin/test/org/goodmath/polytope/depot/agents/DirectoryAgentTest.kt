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
package org.goodmath.polytope.depot.agents

import org.goodmath.polytope.depot.stashes.Artifact
import org.goodmath.polytope.depot.stashes.ArtifactVersion
import org.goodmath.polytope.depot.stashes.VersionStatus
import org.goodmath.polytope.depot.util.Id
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.Instant
import kotlin.test.assertEquals

class DirectoryAgentTest {

    fun setupTestDirectories(
        ancestorBindings: Map<Id<Artifact>, Id<ArtifactVersion>>,
        sourceBindings:  Map<Id<Artifact>, Id<ArtifactVersion>>,
        targetBindings:  Map<Id<Artifact>, Id<ArtifactVersion>>
        ): Triple<ArtifactVersion, ArtifactVersion, ArtifactVersion> {
        val artifactID = "initial"
        val ancestorVer = ArtifactVersion(
            id = "ancestorID",
            artifactId = artifactID,
            artifactType = DirectoryAgent.artifactType,
            timestamp = Instant.now().toEpochMilli(),
            creator = "me",
            content = DirectoryAgent.encodeToString(Directory(ancestorBindings.entries.map { (name, id) ->
                Directory.DirectoryEntry(
                    name,
                    id
                )
            }.toMutableList())),
            parents = emptyList(),
            metadata = emptyMap(),
            status = VersionStatus.Committed
        )
        val sourceVer = ArtifactVersion(
            id = "sourceId",
            artifactId = artifactID,
            artifactType = DirectoryAgent.artifactType,
            timestamp = Instant.now().toEpochMilli(),
            creator = "me",
            content = DirectoryAgent.encodeToString(Directory(sourceBindings.entries.map { (name, id) ->
                Directory.DirectoryEntry(
                    name,
                    id
                )
            }.toMutableList())),
            parents = listOf(ancestorVer.id),
            metadata = emptyMap(),
            status = VersionStatus.Committed
        )
        val targetVer = ArtifactVersion(
            id = "targetId",
            artifactId = artifactID,
            artifactType = DirectoryAgent.artifactType,
            timestamp = Instant.now().toEpochMilli(),
            creator = "me",
            content = DirectoryAgent.encodeToString(Directory(targetBindings.entries.map { (name, id) ->
                Directory.DirectoryEntry(
                    name,
                    id
                )
            }.toMutableList())),
            parents = listOf(ancestorVer.id),
            metadata = emptyMap(),
            status = VersionStatus.Committed
        )
        return Triple(ancestorVer, sourceVer, targetVer)
    }


    @Test
    fun `it should be able to merge non-conflicting changes`() {
        val anc = mapOf(
            "a" to "artifactA",
            "b" to "artifactB",
            "c" to "artifactC",
            "d" to "artifactD"
        )

        val src = mapOf(
            "a" to "artifactA",
            "b" to "artifactB",
            "c" to "artifactC",
            "dd" to "artifactD"
        )

        val tgt = mapOf(
            "a" to "artifactA",
            "bb" to "artifactB",
            "c" to "artifactC",
            "d" to "artifactD"
        )


        val (ancestor, source, target) = setupTestDirectories(
            anc,
            src,
            tgt
        )

        val result = DirectoryAgent.merge(ancestor, source, target)

        assertEquals(0, result.conflicts.size)
        val proposed = DirectoryAgent.decodeFromString(result.proposedMerge)
        assertEquals("a", proposed.getNameFor("artifactA"))
        assertEquals("bb", proposed.getNameFor("artifactB"))
        assertEquals("c", proposed.getNameFor("artifactC"))
        assertEquals("dd", proposed.getNameFor("artifactD"))
    }

    @Test
    fun `it should be able to merge non-conflicting adds`() {
            val ancestorBindings = mapOf(
                "a" to "artifactA",
                "b" to "artifactB",
                "c" to "artifactC",
                "d" to "artifactD")

            val sourceBindings = mapOf(
                "a" to "artifactA",
                "b" to "artifactB",
                "c" to "artifactC",
                "d" to "artifactD",
                "e" to "artifactE")

            val targetBindings = mapOf(
                "a" to "artifactA",
                "b" to "artifactB",
                "c" to "artifactC",
                "d" to "artifactD",
                "f" to "artifactF")

            val (ancestor, source, target) = setupTestDirectories(
                ancestorBindings,
                sourceBindings,
                targetBindings
            )

            val result = DirectoryAgent.merge(ancestor, source, target)

            assertEquals(0, result.conflicts.size)
            val proposed = DirectoryAgent.decodeFromString(result.proposedMerge)
            assertEquals("a", proposed.getNameFor("artifactA"))
            assertEquals("b", proposed.getNameFor("artifactB"))
            assertEquals("c", proposed.getNameFor("artifactC"))
            assertEquals("d", proposed.getNameFor("artifactD"))
            assertEquals("e", proposed.getNameFor("artifactE"))
            assertEquals("f", proposed.getNameFor("artifactF"))
    }

    @Test
    fun `it should be able to merge non-conflicting deletes`() {
            val ancestorBindings = mapOf(
                "a" to "artifactA",
                "b" to "artifactB",
                "c" to "artifactC",
                "d" to "artifactD")


            val sourceBindings = mapOf(
                "a" to "artifactA",
                "b" to "artifactB",
                "d" to "artifactD")


            val targetBindings = mapOf(
                "a" to "artifactA",
                "b" to "artifactB",
                "c" to "artifactC")


            val (ancestorVer, sourceVer, targetVer) = setupTestDirectories(
                ancestorBindings,
                sourceBindings,
                targetBindings
            )
            val result = DirectoryAgent.merge(ancestorVer, sourceVer, targetVer)

            assertEquals(0, result.conflicts.size)
            val proposed = DirectoryAgent.decodeFromString(result.proposedMerge)
            assertEquals("a", proposed.getNameFor("artifactA"))
            assertEquals("b", proposed.getNameFor("artifactB"))
    }

    @Test
    fun `it should be able to merge rename-rename with conflicts`() {
        val ancestorBindings = mapOf(
            "a" to "artifactA",
            "b" to "artifactB",
            "c" to "artifactC",
            "d" to "artifactD"
        )


        val sourceBindings = mapOf(
            "a" to "artifactA",
            "b" to "artifactB",
            "c" to "artifactC",
            "dd" to "artifactD"
        )

        val targetBindings = mapOf(
            "a" to "artifactA",
            "b" to "artifactB",
            "c" to "artifactC",
            "e" to "artifactD"
        )


        val (ancestorVer, sourceVer, targetVer) = setupTestDirectories(
            ancestorBindings,
            sourceBindings,
            targetBindings
        )

        val result = DirectoryAgent.merge(ancestorVer, sourceVer, targetVer)

        assertEquals(1, result.conflicts.size)
        assertEquals("sourceId", result.conflicts[0].sourceVersion)
        assertEquals("targetId", result.conflicts[0].targetVersion)
        val detail = DirectoryMergeConflict.decodeFromString(result.conflicts[0].details)
        assertEquals(DirectoryMergeConflict.ConflictKind.MOD_MOD, detail.kind)
        val proposed = DirectoryAgent.decodeFromString(result.proposedMerge)
        assertEquals("a", proposed.getNameFor("artifactA"))
        assertEquals("b", proposed.getNameFor("artifactB"))
    }


    @Test
    fun `it should be able to merge delete-mod with conflicts`() {
        val ancestorBindings = mapOf(
            "a" to "artifactA",
            "b" to "artifactB",
            "c" to "artifactC",
            "d" to "artifactD"
        )


        val sourceBindings = mapOf(
            "a" to "artifactA",
            "b" to "artifactB",
            "c" to "artifactC"
        )

        val targetBindings = mapOf(
            "a" to "artifactA",
            "b" to "artifactB",
            "c" to "artifactC",
            "e" to "artifactD"
        )


        val (ancestorVer, sourceVer, targetVer) = setupTestDirectories(
            ancestorBindings,
            sourceBindings,
            targetBindings
        )

        val result = assertDoesNotThrow { DirectoryAgent.merge(ancestorVer, sourceVer, targetVer) }

        assertEquals(1, result.conflicts.size)
        assertEquals("sourceId", result.conflicts[0].sourceVersion)
        assertEquals("targetId", result.conflicts[0].targetVersion)
        val detail = DirectoryMergeConflict.decodeFromString(result.conflicts[0].details)
        assertEquals(DirectoryMergeConflict.ConflictKind.DEL_MOD, detail.kind)
        val proposed = DirectoryAgent.decodeFromString(result.proposedMerge)
        assertEquals(4, proposed.entries.size)
        assertEquals("a", proposed.getNameFor("artifactA"))
        assertEquals("b", proposed.getNameFor("artifactB"))
        assertEquals("c", proposed.getNameFor("artifactC"))
        assertEquals("e", proposed.getNameFor("artifactD"))
    }


    @Test
    fun `it should be able to merge mod-delete with conflicts`() {
        val ancestorBindings = mapOf(
            "a" to "artifactA",
            "b" to "artifactB",
            "c" to "artifactC",
            "d" to "artifactD"
        )
        val sourceBindings = mapOf(
            "a" to "artifactA",
            "b" to "artifactB",
            "c" to "artifactC",
            "e" to "artifactD"
        )


        val targetBindings = mapOf(
            "a" to "artifactA",
            "b" to "artifactB",
            "c" to "artifactC"
        )


        val (ancestorVer, sourceVer, targetVer) = setupTestDirectories(
            ancestorBindings,
            sourceBindings,
            targetBindings
        )
        val result = DirectoryAgent.merge(ancestorVer, sourceVer, targetVer)

        assertEquals(1, result.conflicts.size)
        assertEquals("sourceId", result.conflicts[0].sourceVersion)
        assertEquals("targetId", result.conflicts[0].targetVersion)
        val detail = DirectoryMergeConflict.decodeFromString(result.conflicts[0].details)
        assertEquals(DirectoryMergeConflict.ConflictKind.MOD_DEL, detail.kind)
        val proposed = DirectoryAgent.decodeFromString(result.proposedMerge)
        assertEquals(4, proposed.entries.size)
        assertEquals("a", proposed.getNameFor("artifactA"))
        assertEquals("b", proposed.getNameFor("artifactB"))
        assertEquals("c", proposed.getNameFor("artifactC"))
        assertEquals("e", proposed.getNameFor("artifactD"))
    }
}