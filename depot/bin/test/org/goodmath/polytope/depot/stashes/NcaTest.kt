package org.goodmath.polytope.depot.stashes

import org.goodmath.polytope.TestStub
import org.goodmath.polytope.common.agents.text.TextAgent
import org.goodmath.polytope.common.stashable.Artifact
import org.goodmath.polytope.common.stashable.ArtifactVersion
import org.goodmath.polytope.common.stashable.AuthenticatedUser
import org.goodmath.polytope.common.stashable.Id
import org.goodmath.polytope.depot.Depot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NcaTest: TestStub() {


    private lateinit var aVer: ArtifactVersion
    private lateinit var bVer: ArtifactVersion
    private lateinit var cVer: ArtifactVersion
    private lateinit var dVer: ArtifactVersion
    private lateinit var eVer: ArtifactVersion
    private lateinit var fVer: ArtifactVersion
    private lateinit var gVer: ArtifactVersion
    private lateinit var hVer: ArtifactVersion
    private lateinit var iVer: ArtifactVersion
    private lateinit var jVer: ArtifactVersion
    private lateinit var kVer: ArtifactVersion
    private lateinit var lVer: ArtifactVersion

    private fun buildArtifactHistory(
        auth: AuthenticatedUser,
        depot: Depot,
        artifactId: Id<Artifact>,
        rootVersion: Id<ArtifactVersion>
    ) {
        // Create a bunch of versions with a history:

        // A is a parent of B and D
        // B is a parent of C and G
        // D is a parent of E
        // C is a parent of F
        // G is a parent of F, J, and K
        // F is a parent of I
        // E is a parent of I, L, and H
        // I is a parent of K

        aVer = depot.artifacts.createVersion(
            auth,
            "testie",
            artifactId,
            TextAgent.artifactType,
            "aaaaaaa\n",
            listOf(rootVersion),
            emptyMap()
        )
        // B < A
        bVer = depot.artifacts.createVersion(
            auth,
            "test%e",
            artifactId,
            TextAgent.artifactType,
            "bbbbbb\n",
            listOf(aVer.id), emptyMap()
        )
        // C < B
        cVer = depot.artifacts.createVersion(
            auth,
            "testie",
            artifactId,
            TextAgent.artifactType,
            "cccccccc\n",
            listOf(bVer.id),

            emptyMap()
        )

        // D < A
        dVer = depot.artifacts.createVersion(
            auth,
            "testie",
            artifactId,
            TextAgent.artifactType,
            "dddddddd\n",
            listOf(aVer.id),

            emptyMap()
        )

        // G < B
        gVer = depot.artifacts.createVersion(
            auth,
            "testie",
            artifactId,
            TextAgent.artifactType,
            "gggggggg\n",
            listOf(bVer.id),

            emptyMap()
        )
        // E < D
        eVer = depot.artifacts.createVersion(
            auth,
            "testie",
            artifactId,
            TextAgent.artifactType,
            "eeeeeeee\n",
            listOf(dVer.id),

            emptyMap()
        )
        // F < C, G
        fVer = depot.artifacts.createVersion(
            auth,
            "testie",
            artifactId,
            TextAgent.artifactType,
            "ffffffff\n",
            listOf(cVer.id, gVer.id),

            emptyMap()
        )
        // J < G
        jVer = depot.artifacts.createVersion(
            auth,
            "testie",
            artifactId,
            TextAgent.artifactType,
            "jjjjjjjj\n",
            listOf(gVer.id),

            emptyMap()
        )
        // I < E, F
        iVer = depot.artifacts.createVersion(
            auth,
            "testie",
            artifactId,
            TextAgent.artifactType,
            "iiiiiiii\n",
            listOf(eVer.id, fVer.id),

            emptyMap()
        )
        // K < G, I
        kVer = depot.artifacts.createVersion(
            auth,
            "testie",
            artifactId,
            TextAgent.artifactType,
            "kkkkkkkk\n",
            listOf(gVer.id, iVer.id),

            emptyMap()
        )
        // L < E
        lVer = depot.artifacts.createVersion(
            auth,
            "testie",
            artifactId,
            TextAgent.artifactType,
            "llllllll\n",
            listOf(eVer.id),

            emptyMap()
        )
        // H < E
        hVer = depot.artifacts.createVersion(
            auth,
            "testie",
            artifactId,
            TextAgent.artifactType,
            "hhhhhhhh\n",
            listOf(eVer.id),

            emptyMap()
        )
    }

    @Test
    fun `it should be able to find the LCS in a version graph`() {
        val auth = depot.users.authenticate("tester", "tester tests")
        val pr = depot.projects.createProject(
            auth,
            "testie",
            "a test"
        )

        val (artifact, rootVer) = depot.artifacts.createArtifact(
            auth,
            pr.name,
            TextAgent.artifactType,
            "00000000\n",
            emptyMap()
        )

        buildArtifactHistory(auth, depot, artifact.id, rootVer.id)

        val cj = depot.artifacts.nearestCommonAncestor(
            auth,
            pr.name,
            artifact.id,
            cVer.id,
            jVer.id
        )
        assertEquals(bVer.id, cj)
        val hj = depot.artifacts.nearestCommonAncestor(
            auth,
            pr.name,
            artifact.id,
            hVer.id,
            jVer.id
        )
        assertEquals(aVer.id, hj)

        val hk = depot.artifacts.nearestCommonAncestor(
            auth,
            pr.name,
            artifact.id,
            hVer.id,
            kVer.id
        )
        assertEquals(eVer.id, hk)

        val ig = depot.artifacts.nearestCommonAncestor(
            auth,
            pr.name,
            artifact.id,
            iVer.id,
            gVer.id
        )
        assertEquals(gVer.id, ig)

        val gi = depot.artifacts.nearestCommonAncestor(
            auth,
            pr.name,
            artifact.id,
            gVer.id,
            iVer.id
        )
        assertEquals(gVer.id, gi)
    }

    @Test
    fun `it should be able to check ancestry`() {
        val auth = depot.users.authenticate("tester", "tester tests")
        val pr = depot.projects.createProject(
            auth,
            "testie",
            "a test"
        )

        val (artifact, rootVer) = depot.artifacts.createArtifact(
            auth,
            pr.name,
            TextAgent.artifactType,
            "00000000\n",
            emptyMap()
        )

        buildArtifactHistory(auth, depot, artifact.id, rootVer.id)
        val historyNamed = mapOf(
            aVer.id to "a", bVer.id to "b", cVer.id to "c",
            dVer.id to "d", eVer.id to "e", fVer.id to "f", gVer.id to "g", hVer.id to "h", iVer.id to "i",
            jVer.id to "j", kVer.id to "k", lVer.id to "l", rootVer.id to "root"
        )


        val gHist = depot.artifacts.allAncestors(auth, pr.name, artifact.id, gVer.id)
        assertEquals(setOf("a", "b", "g", "root"), gHist.map { historyNamed[it] }.toSet())

        val iHist = depot.artifacts.allAncestors(auth, pr.name, artifact.id, iVer.id)
        assertEquals(
            setOf("root", "a", "b", "c", "d", "e", "f", "g", "i"),
            iHist.map { historyNamed[it] }.toSet()
        )
    }

    @Test
    fun `it should be able to get the closed history`() {
        val auth = depot.users.authenticate("tester", "tester tests")
        val pr = depot.projects.createProject(
            auth,
            "testie",
            "a test"
        )

        val (artifact, rootVer) = depot.artifacts.createArtifact(
            auth,
            pr.name,
            TextAgent.artifactType,
            "00000000\n",
            emptyMap()
        )

        buildArtifactHistory(auth, depot, artifact.id, rootVer.id)
        assertTrue {
            depot.artifacts.versionIsAncestor(
                auth, pr.name, artifact.id,
                aVer.id, lVer.id
            )
        }
        assertFalse {
            depot.artifacts.versionIsAncestor(
                auth, pr.name, artifact.id,
                lVer.id, aVer.id
            )
        }
    }
}