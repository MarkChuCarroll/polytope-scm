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
package org.goodmath.polytope.common.agents

import org.goodmath.polytope.common.stashable.Artifact
import org.goodmath.polytope.common.stashable.ArtifactVersion
import org.goodmath.polytope.common.stashable.Id
import org.goodmath.polytope.common.stashable.VersionStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BaselineAgentTest {

    data class BaselineFamily(
        val ancestor: ArtifactVersion,
        val source: ArtifactVersion,
        val target: ArtifactVersion
    )

    private fun setupBaselines(
        ancestorMapping: List<Pair<Id<Artifact>, Id<ArtifactVersion>>>,
        sourceMapping: List<Pair<Id<Artifact>, Id<ArtifactVersion>>>,
        targetMapping: List<Pair<Id<Artifact>, Id<ArtifactVersion>>>
    ): BaselineFamily {
        val artifact = Artifact(
            id = "test_artifact",
            artifactType = DirectoryAgent.artifactType,
            timestamp = 12345678L,
            creator = "markcc",
            project = "testing",
            metadata = emptyMap(),
            versions = ArrayList()
        )

        val ancestorBaseline = Baseline(
            rootDir = "a",
            entries = ancestorMapping.associate { (a, v) -> a to v }.toMutableMap()
        )

        val ancestorVer = ArtifactVersion(
            id = "ancestorID",
            artifactId = artifact.id,
            artifactType = DirectoryAgent.artifactType,
            timestamp = 123456678L,
            creator = "markcc",
            parents = emptyList(),
            metadata = emptyMap(),
            status = VersionStatus.Committed,
            content = BaselineAgent.encodeToString(ancestorBaseline)
        )

        val sourceBaseline = Baseline(
            rootDir = "a",
            entries = sourceMapping.associate { (a, v) -> a to v }.toMutableMap()
        )

        val sourceVer = ArtifactVersion(
            id = "ancestorID",
            artifactId = artifact.id,
            artifactType = DirectoryAgent.artifactType,
            timestamp = 123456678L,
            creator = "markcc",
            parents = listOf(ancestorVer.id),
            metadata = emptyMap(),
            status = VersionStatus.Committed,
            content = BaselineAgent.encodeToString(sourceBaseline)
        )

        val targetBaseline = Baseline(
            rootDir = "a",
            entries = targetMapping.associate { (a, v) -> a to v }.toMutableMap()
        )

        val targetVer = ArtifactVersion(
            id = "ancestorID",
            artifactId = artifact.id,
            artifactType = DirectoryAgent.artifactType,
            timestamp = 123456678L,
            creator = "markcc",
            parents = listOf(ancestorVer.id),
            metadata = emptyMap(),
            status = VersionStatus.Committed,
            content = BaselineAgent.encodeToString(targetBaseline)
        )

        return BaselineFamily(ancestorVer, sourceVer, targetVer)
    }

    @Test
    fun `it should be able to do a non-conflicting merge`() {
        val ancMap = listOf(
            Pair("a", "a1"),
            Pair("b", "b1"),
            Pair("c", "c1"),
            Pair("d", "d1")
        )
        val srcMap = listOf(
            Pair("a", "a2"),
            Pair("b", "b1"),
            Pair("c", "c1"),
            Pair("d", "d2")
        )
        val tgtMap = listOf(
            Pair("a", "a1"),
            Pair("b", "b2"),
            Pair("c", "c2"),
            Pair("d", "d1")
        )
        val (ancestorVer, sourceVer, targetVer) = setupBaselines(
            ancMap, srcMap, tgtMap
        )
        val ancestorBaseline = BaselineAgent.decodeFromString(ancestorVer.content)
        val result =
            assertDoesNotThrow { BaselineAgent.merge(ancestorVer, sourceVer, targetVer) }

        assertEquals(0, result.conflicts.size)

        val proposed = BaselineAgent.decodeFromString(result.proposedMerge)
        assertEquals(ancestorBaseline.rootDir, proposed.rootDir)
        assertEquals(4, proposed.entries.size)
        assertEquals("a2", proposed.get("a"))
        assertEquals("b2", proposed.get("b"))
        assertEquals("c2", proposed.get("c"))
        assertEquals("d2", proposed.get("d"))
    }

    @Test
    fun `it should be able to do a non-conflicting merge with an add`() {
        val ancestorMapping = listOf(
            Pair("a", "a1"),
            Pair("b", "b1"),
            Pair("c", "c1"),
            Pair("d", "d1")
        )

        val sourceMapping = listOf(
            Pair("a", "a2"),
            Pair("b", "b1"),
            Pair("c", "c1"),
            Pair("d", "d2"),
            Pair("e", "e1")
        )
        val targetMapping = listOf(
            Pair("a", "a1"),
            Pair("b", "b2"),
            Pair("c", "c2"),
            Pair("d", "d1")
        )

        val (ancestor, source, target) = setupBaselines(
            ancestorMapping,
            sourceMapping,
            targetMapping
        )

        val ancestorBaseline = BaselineAgent.decodeFromString(ancestor.content)

        val result = BaselineAgent.merge(ancestor, source, target)

        assertEquals(0, result.conflicts.size)
        val proposed = BaselineAgent.decodeFromString(result.proposedMerge)
        assertEquals(ancestorBaseline.rootDir, proposed.rootDir)
        assertEquals(5, proposed.entries.size)
        assertEquals("a2", proposed.get("a"))
        assertEquals("b2", proposed.get("b"))
        assertEquals("c2", proposed.get("c"))
        assertEquals("d2", proposed.get("d"))
        assertEquals("e1", proposed.get("e"))
    }

    @Test
    fun `it should be able to do a non-conflicting merge with adds on both sides`() {
        val ancestorMapping = listOf(
            Pair("a", "a1"),
            Pair("b", "b1"),
            Pair("c", "c1"),
            Pair("d", "d1")
        )
        val sourceMapping = listOf(
            Pair("a", "a2"),
            Pair("b", "b1"),
            Pair("c", "c1"),
            Pair("d", "d2"),
            Pair("e", "e1")
        )

        val targetMapping = listOf(
            Pair("a", "a1"),
            Pair("b", "b2"),
            Pair("c", "c2"),
            Pair("d", "d1"),
            Pair("f", "f27")
        )

        val (ancestor, source, target) = setupBaselines(
            ancestorMapping,
            sourceMapping,
            targetMapping
        )
        val ancestorBaseline = BaselineAgent.decodeFromString(ancestor.content)

        val result = BaselineAgent.merge(ancestor, source, target)

        assertEquals(0, result.conflicts.size)
        val proposed = BaselineAgent.decodeFromString(result.proposedMerge)
        assertEquals(ancestorBaseline.rootDir, proposed.rootDir)
        assertEquals(6, proposed.entries.size)
        assertEquals("a2", proposed.get("a"))
        assertEquals("b2", proposed.get("b"))
        assertEquals("c2", proposed.get("c"))
        assertEquals("d2", proposed.get("d"))
        assertEquals("e1", proposed.get("e"))
        assertEquals("f27", proposed.get("f"))
    }

    @Test
    fun `it should be able to do a non-conflicting merge with a delete`() {
        val ancestorMapping = listOf(
            Pair("a", "a1"),
            Pair("b", "b1"),
            Pair("c", "c1"),
            Pair("d", "d1")
        )

        val sourceMapping = listOf(
            Pair("a", "a2"),
            Pair("b", "b1"),
            Pair("c", "c1")
        )


        val targetMapping = listOf(
            Pair("a", "a1"),
            Pair("b", "b2"),
            Pair("c", "c2"),
            Pair("d", "d1")
        )

        val (ancestorVer, sourceVer, targetVer) = setupBaselines(
            ancestorMapping,
            sourceMapping,
            targetMapping
        )
        val ancestorBaseline = BaselineAgent.decodeFromString(ancestorVer.content)

        val result = BaselineAgent.merge(ancestorVer, sourceVer, targetVer)

        assertEquals(0, result.conflicts.size)
        val proposed = BaselineAgent.decodeFromString(result.proposedMerge)
        assertEquals(ancestorBaseline.rootDir, proposed.rootDir)
        assertEquals(3, proposed.entries.size)
        assertEquals("a2", proposed.get("a"))
        assertEquals("b2", proposed.get("b"))
        assertEquals("c2", proposed.get("c"))
    }

    @Test
    fun `it should be able to do a non-conflicting merge with deletes on both sides`() {
        val ancestorMapping = listOf(
            Pair("a", "a1"),
            Pair("b", "b1"),
            Pair("c", "c1"),
            Pair("d", "d1")
        )
        val sourceMapping = listOf(
            Pair("a", "a2"),
            Pair("b", "b1"),
            Pair("c", "c1"),
        )
        val targetMapping = listOf(
            Pair("a", "a1"),
            Pair("b", "b2"),
            Pair("d", "d1")
        )


        val (ancestorVer, sourceVer, targetVer) = setupBaselines(
            ancestorMapping,
            sourceMapping,
            targetMapping
        )
        val ancestorBaseline = BaselineAgent.decodeFromString(ancestorVer.content)

        val result = BaselineAgent.merge(ancestorVer, sourceVer, targetVer)

        assertEquals(0, result.conflicts.size)
        val proposed = BaselineAgent.decodeFromString(result.proposedMerge)
        assertEquals(ancestorBaseline.rootDir, proposed.rootDir)
        assertEquals(2, proposed.entries.size)
        assertEquals("a2", proposed.get("a"))
        assertEquals("b2", proposed.get("b"))
    }


    @Test
    fun `it should be able to do a mod-mod merge with conflicts`() {
        val ancestorMapping = listOf(
            Pair("a", "a1"),
            Pair("b", "b1"),
            Pair("c", "c1"),
            Pair("d", "d1")
        )

        val sourceMapping = listOf(
            Pair("a", "a2"),
            Pair("b", "b1"),
            Pair("c", "c1"),
            Pair("d", "d2")
        )
        val targetMapping = listOf(
            Pair("a", "a1"),
            Pair("b", "b2"),
            Pair("c", "c2"),
            Pair("d", "d3")
        )


        val (ancestorVer, sourceVer, targetVer) = setupBaselines(
            ancestorMapping,
            sourceMapping,
            targetMapping
        )
        val ancestorBaseline = BaselineAgent.decodeFromString(ancestorVer.content)

        val result = BaselineAgent.merge(ancestorVer, sourceVer, targetVer)

        assertEquals(1, result.conflicts.size)
        val conflict = result.conflicts[0]
        val cDetail = BaselineConflict.decodeFromString(conflict.details)
        assertEquals(BaselineConflictType.MOD_MOD, cDetail.type)
        assertEquals("d", cDetail.artifactId)
        assertEquals("d2", cDetail.mergeSourceVersion)
        assertEquals("d3", cDetail.mergeTargetVersion)

        val proposed = BaselineAgent.decodeFromString(result.proposedMerge)
        assertEquals(ancestorBaseline.rootDir, proposed.rootDir)
        assertEquals(4, proposed.entries.size)
        assertEquals("a2", proposed.get("a"))
        assertEquals("b2", proposed.get("b"))
        assertEquals("c2", proposed.get("c"))
        assertEquals("d3", proposed.get("d"))
    }

    @Test
    fun `it should be able to do a mod-del merge with conflicts`() {
        val ancestorMapping = listOf(
            Pair("a", "a1"),
            Pair("b", "b1"),
            Pair("c", "c1"),
            Pair("d", "d1")
        )
        val sourceMapping = listOf(
            Pair("a", "a2"),
            Pair("b", "b1"),
            Pair("c", "c1"),
            Pair("d", "d2")
        )
        val targetMapping = listOf(
            Pair("a", "a1"),
            Pair("b", "b2"),
            Pair("c", "c2")
        )

        val (ancestorVer, sourceVer, targetVer) = setupBaselines(
            ancestorMapping,
            sourceMapping,
            targetMapping
        )
        val ancestorBaseline = BaselineAgent.decodeFromString(ancestorVer.content)

        val result = BaselineAgent.merge(ancestorVer, sourceVer, targetVer)

        assertEquals(1, result.conflicts.size)
        val conflict = result.conflicts[0]
        val cDetail = BaselineConflict.decodeFromString(conflict.details)
        assertEquals(BaselineConflictType.MOD_DEL, cDetail.type)
        assertEquals("d", cDetail.artifactId)
        assertEquals("d2", cDetail.mergeSourceVersion)
        assertEquals(null, cDetail.mergeTargetVersion)

        val proposed = BaselineAgent.decodeFromString(result.proposedMerge)
        assertEquals(ancestorBaseline.rootDir, proposed.rootDir)
        assertEquals(4, proposed.entries.size)
        assertEquals("a2", proposed.get("a"))
        assertEquals("b2", proposed.get("b"))
        assertEquals("c2", proposed.get("c"))
        assertEquals("d2", proposed.get("d"))
    }


    @Test
    fun `it should be able to do a del-mod merge with conflicts`() {
        val ancestorMapping = listOf(
            Pair("a", "a1"),
            Pair("b", "b1"),
            Pair("c", "c1"),
            Pair("d", "d1")
        )

        val sourceMapping = listOf(
            Pair("a", "a2"),
            Pair("b", "b1"),
            Pair("c", "c1")
        )

        val targetMapping = listOf(
            Pair("a", "a1"),
            Pair("b", "b2"),
            Pair("c", "c2"),
            Pair("d", "d2")
        )


        val (ancestorVer, sourceVer, targetVer) = setupBaselines(
            ancestorMapping,
            sourceMapping,
            targetMapping
        )

        val ancestorBaseline = BaselineAgent.decodeFromString(ancestorVer.content)

        val result = BaselineAgent.merge(ancestorVer, sourceVer, targetVer)

        assertEquals(1, result.conflicts.size)
        val conflict = result.conflicts[0]
        val cDetail = BaselineConflict.decodeFromString(conflict.details)
        assertEquals(BaselineConflictType.DEL_MOD, cDetail.type)
        assertEquals("d", cDetail.artifactId)
        assertNull(cDetail.mergeSourceVersion)
        assertEquals("d2", cDetail.mergeTargetVersion)

        val proposed = BaselineAgent.decodeFromString(result.proposedMerge)
        assertEquals(ancestorBaseline.rootDir, proposed.rootDir)
        assertEquals(4, proposed.entries.size)
        assertEquals("a2", proposed.get("a"))
        assertEquals("b2", proposed.get("b"))
        assertEquals("c2", proposed.get("c"))
        assertEquals("d2", proposed.get("d"))
    }

    @Test
    fun `it should not have any conflicts when both delete the same thing`() {
        val ancestorMapping = listOf(
            Pair("a", "a1"),
            Pair("b", "b1"),
            Pair("c", "c1"),
            Pair("d", "d1")
        )

        val sourceMapping = listOf(
            Pair("a", "a2"),
            Pair("b", "b1"),
            Pair("c", "c1")
        )

        val targetMapping = listOf(
            Pair("a", "a1"),
            Pair("b", "b2"),
            Pair("c", "c2")
        )

        val (ancestorVer, sourceVer, targetVer) = setupBaselines(
            ancestorMapping,
            sourceMapping, targetMapping
        )
        val ancestorBaseline = BaselineAgent.decodeFromString(ancestorVer.content)

        val result = BaselineAgent.merge(ancestorVer, sourceVer, targetVer)

        assertEquals(0, result.conflicts.size)

        val proposed = BaselineAgent.decodeFromString(result.proposedMerge)
        assertEquals(ancestorBaseline.rootDir, proposed.rootDir)
        assertEquals(3, proposed.entries.size)
        assertEquals("a2", proposed.get("a"))
        assertEquals("b2", proposed.get("b"))
        assertEquals("c2", proposed.get("c"))
    }
}
