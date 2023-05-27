package org.goodmath.polytope.common.agents

import org.goodmath.polytope.common.stashable.ArtifactVersion
import org.goodmath.polytope.common.stashable.ID_CONFLICT
import org.goodmath.polytope.common.stashable.newId
import org.goodmath.polytope.common.util.FileType
import org.goodmath.polytope.common.util.ParsingCommons
import java.io.File
import java.nio.file.Path
import java.util.Base64
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

data class BinaryContent(
    val content: ByteArray
) : ContentHashable {
    override fun contentHash(): String =
        BinaryContentAgent.contentHash(this)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BinaryContent
        return content.contentEquals(other.content)
    }

    override fun hashCode(): Int {
        return content.contentHashCode()
    }


}

data class BinaryMergeConflict(
    val message: String = "Binary artifacts cannot be merged"
) {
    fun encodeToString(): String {
        return ParsingCommons.klaxon.toJsonString(this)
    }

    companion object {
        fun decodeFromString(cf: String): BinaryMergeConflict {
            return ParsingCommons.klaxon.parse(cf)!!
        }
    }

}
object BinaryContentAgent: FileAgent<BinaryContent> {
    private val extensions = hashSetOf("bin", "exe", "jar", "zip", "gz", "tgz", "class")
    override fun canHandle(file: File): Boolean {
        return if (file.extension in extensions) {
            true
        } else {
            FileType.of(file) == FileType.binary
        }
    }

    override fun readFromDisk(path: Path): BinaryContent {
        val bytes = path.readBytes()
        return BinaryContent(bytes)
    }

    override fun stringFromDisk(path: Path): String {
        return encodeToString(readFromDisk(path))
    }

    override fun stringToDisk(path: Path, content: String) {
        writeToDisk(path, decodeFromString(content))
    }

    override fun writeToDisk(path: Path, value: BinaryContent) {
        path.writeBytes(value.content)
    }

    override val artifactType: String = "binary"
    override fun decodeFromString(content: String): BinaryContent {
        val bin = Base64.getDecoder().decode(content)
        return BinaryContent(bin)
    }

    override fun merge(ancestor: ArtifactVersion, source: ArtifactVersion, target: ArtifactVersion): MergeResult {
        return MergeResult(artifactType, ancestor.artifactId, ancestor.id, source.id, target.id,
            target.content, listOf(MergeConflict(newId<MergeConflict>(ID_CONFLICT),
                ancestor.artifactId, artifactType, source.id, target.id,
                BinaryMergeConflict().encodeToString())))
    }

    override fun encodeToString(content: BinaryContent): String {
        return Base64.getEncoder().encodeToString(content.content)
    }
}
