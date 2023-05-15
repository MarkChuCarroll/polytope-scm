package org.goodmath.polytope.common.agents

import org.goodmath.polytope.common.agents.text.*
import org.goodmath.polytope.common.stashable.ArtifactVersion
import org.goodmath.polytope.common.stashable.VersionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertContentEquals

class TextAgentTest {


    private fun buildTextVersions(
        ancestorContent: List<String>,
        sourceContent: List<String>,
        targetContent: List<String>
    ): Triple<ArtifactVersion, ArtifactVersion, ArtifactVersion> {
        val ancestorVer = ArtifactVersion(
            id = "ancestorId",
            artifactId = "testdoc",
            artifactType = TextContentAgent.artifactType,
            timestamp = Instant.now().toEpochMilli(),
            creator = "me",
            content = TextContentAgent.encodeToString(TextContent(ancestorContent)),
            parents = emptyList(),
            metadata = emptyMap(),
            status = VersionStatus.Committed
        )
        val sourceVer = ArtifactVersion(
            id = "sourceId",
            artifactId = "testdoc",
            artifactType = TextContentAgent.artifactType,
            timestamp = Instant.now().toEpochMilli(),
            creator = "me",
            content = TextContentAgent.encodeToString(TextContent(sourceContent)),
            parents = listOf(ancestorVer.id),
            metadata = emptyMap(),
            status = VersionStatus.Committed
        )
        val targetVer = ArtifactVersion(
            id = "targetId",
            artifactId = "testdoc",
            artifactType = TextContentAgent.artifactType,
            timestamp = Instant.now().toEpochMilli(),
            creator = "me",
            content = TextContentAgent.encodeToString(TextContent(targetContent)),
            parents = listOf(ancestorVer.id),
            metadata = emptyMap(),
            status = VersionStatus.Committed
        )
        return Triple(ancestorVer, sourceVer, targetVer)
    }



    @Test
    fun `it should be able to encode and decode content`() {
        val content = listOf("aaa\n", "bbb\n", "ccc\n", "ddd\n", "eeee\n")
        val encoded = TextContentAgent.encodeToString(TextContent(content))
        val decoded = TextContentAgent.decodeFromString(encoded)
        assertEquals(content, decoded.content)
    }

    @Test
    fun `it should be able to generate a line-labelled diff`() {
        val ancestorText = listOf("a\n", "b\n", "c\n", "d\n", "e\n")
        val sourceText = listOf("a\n", "c\n", "q\n", "d\n", "e\n")

        val lab = TextContentAgent.createLabelledList(ancestorText, sourceText)
        assertEquals(6, lab.size)
        assertEquals(
            LabeledLine(LineLabel.Unmodified, "a\n", 0, 0, 1),
            lab[0]
        )
        assertEquals(
            LabeledLine(LineLabel.Deleted, "b\n", 1, null, 2),
            lab[1]
        )
        assertEquals(
            LabeledLine(LineLabel.Unmodified, "c\n", 2, 1, 3),
            lab[2]
        )
        assertEquals(
            LabeledLine(LineLabel.Inserted, "q\n", null, 2, 3),
            lab[3]
        )
        assertEquals(
            LabeledLine(LineLabel.Unmodified, "d\n", 3, 3, 4),
            lab[4]
        )
        assertEquals(
            LabeledLine(LineLabel.Unmodified, "e\n", 4, 4, 5),
            lab[5]
        )

    }

    @Test
    fun `it should be able to coalesce labeled lines into blocks`() {
        val ancestorText = listOf("a\n", "b\n", "c\n", "d\n", "e\n")
        val sourceText = listOf("a\n", "c\n", "q\n", "d\n", "e\n")
        val targetText = listOf("a\n", "b\n", "c\n", "d\n", "e\n")

        val labeledSrc = TextContentAgent.createLabelledList(ancestorText, sourceText)
        val labeledTgt = TextContentAgent.createLabelledList(ancestorText, targetText)
        val coalesced = TextContentAgent.coalesceLinesIntoBlocks(labeledSrc, labeledTgt)
        assertContentEquals(
            listOf(
                MergeBlock(
                    1,
                    arrayListOf(LabeledLine(LineLabel.Unmodified, "a\n", 0, 0, 1)),
                    arrayListOf(LabeledLine(LineLabel.Unmodified, "a\n", 0, 0, 1))
                ),
                MergeBlock(
                    2,
                    arrayListOf(LabeledLine(LineLabel.Deleted, "b\n", 1, null, 2)),
                    arrayListOf(LabeledLine(LineLabel.Unmodified, "b\n", 1, 1, 2))
                ),
                MergeBlock(
                    3,
                    arrayListOf(
                        LabeledLine(LineLabel.Unmodified, "c\n", 2, 1, 3),
                        LabeledLine(LineLabel.Inserted, "q\n", null, 2, 3)
                    ),
                    arrayListOf(LabeledLine(LineLabel.Unmodified, "c\n", 2, 2, 3))
                ),
                MergeBlock(
                    4,
                    arrayListOf(LabeledLine(LineLabel.Unmodified, "d\n", 3, 3, 4)),
                    arrayListOf(LabeledLine(LineLabel.Unmodified, "d\n", 3, 3, 4))
                ),
                MergeBlock(
                    5,
                    arrayListOf(LabeledLine(LineLabel.Unmodified, "e\n", 4, 4, 5)),
                    arrayListOf(LabeledLine(LineLabel.Unmodified, "e\n", 4, 4, 5))
                )
            ),
            coalesced
        )
    }

    @Test
    fun `it should be able to do merges of text`() {
        val ancestorText = listOf("a\n", "b\n", "c\n", "d\n", "e\n")
        val sourceText = listOf("a\n", "c\n", "q\n", "d\n", "e\n")
        val targetText = listOf("a\n", "b\n", "c\n", "d\n", "e\n")

        val (anc, src, tgt) = buildTextVersions(ancestorText, sourceText, targetText)

        val merge = TextContentAgent.merge(anc, src, tgt)
        val proposed = TextContentAgent.decodeFromString(merge.proposedMerge)
        assertContentEquals(sourceText, proposed.content)
        assertEquals(0, merge.conflicts.size)
    }
}
