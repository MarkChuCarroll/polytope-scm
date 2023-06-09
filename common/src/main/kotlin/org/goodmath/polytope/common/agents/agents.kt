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
import java.io.File
import java.lang.StringBuilder
import java.nio.file.Path
import java.security.MessageDigest
import java.util.*

data class MergeConflict(
    val id: Id<MergeConflict>,
    val artifactId: Id<Artifact>,
    val artifactType: String,
    val sourceVersion: Id<ArtifactVersion>,
    val targetVersion: Id<ArtifactVersion>,
    val details: String
) {
    fun render(indent: Int = 0): String {
        val result = StringBuilder()
        result.append("\t".repeat(indent))
            .append("Conflict: $id on $artifactId\n")
            .append("\t".repeat(indent))
            .append("Artifact type: $artifactType\n")
            .append("\t".repeat(indent))
            .append("Source version: $sourceVersion\n")
            .append("\t".repeat(indent))
            .append("Target version: $targetVersion\n")
        return result.toString()
    }
}

data class MergeResult(
    val artifactType: String,
    val artifactId: Id<Artifact>,
    val ancestorVersion: Id<ArtifactVersion>,
    val sourceVersion: Id<ArtifactVersion>,
    val targetVersion: Id<ArtifactVersion>,
    val proposedMerge: String,
    val conflicts: List<MergeConflict>
)

interface Agent<T> {
    val artifactType: String
    fun encodeToString(content: T): String
    fun decodeFromString(content: String): T

    fun contentHash(content: T): String {
        val str = encodeToString(content)
        val base64encoder = Base64.getEncoder()
        val digest = MessageDigest.getInstance("SHA-512")
        digest.update(str.toByteArray())
        return base64encoder.encodeToString(digest.digest())
    }

    fun merge(
        ancestor: ArtifactVersion,
        source: ArtifactVersion,
        target: ArtifactVersion
    ): MergeResult

    fun merge(startVersion: ArtifactVersion,
              endVersion: ArtifactVersion,
              targetVersion: ArtifactVersion,
              nearestCommonAncestor: ArtifactVersion): MergeResult


}

interface ContentHashable {
    fun contentHash(): String
    fun encodeAsString(): String
}

interface FileAgent<T: ContentHashable>: Agent<T> {

    /**
     * Given a reference to a file, return "true" if the file is a type that
     * can be processed by the agent.
     */
    fun canHandle(file: File): Boolean

    fun readFromDisk(path: Path): T
    fun writeToDisk(path: Path, value: T)

    fun stringFromDisk(path: Path): String

    fun readStringWithHash(path: Path): Pair<String, String> {
        val content = readFromDisk(path)
        val hash = contentHash(content)
        return Pair(encodeToString(content), hash)
    }

    fun stringToDisk(path: Path, content: String)

}

