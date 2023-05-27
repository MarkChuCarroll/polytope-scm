package org.goodmath.polytope.common.util

import java.io.File
import java.nio.file.Path

/**
 * This is a utility for helping to identify the type of a file.
 * As polytope grows and learns to support more types, they'll
 * get added to this file.
 *
 * Ideally, this should be somehow hooked into agents, so that it
 * can look at a file, and return the correct agent. The agents
 * should be able to provide filename hints and content tests
 * to determine if they're the correct agent for the file.
 */
class FileType private constructor(val typeName: String) {

    override fun toString(): String {
        return "FileType($typeName)"
    }
    companion object {
        val text = FileType("text")
        val binary = FileType("binary")

        /**
         * As a simple heuristic, to determine if a file is binary or text,
         * we read the first 1k bytes of the file, and look for control characters
         * other than carriage returns and tabs. If we see any of those, then we
         * assume the file is binary; otherwise, we assume the file is text.
         */
        fun of(f: File): FileType {
            val (prefix, len) = f.inputStream().use { inp ->
                val bytes = ByteArray(1024)
                val count = inp.read(bytes, 0, 1024)
                Pair(bytes, count)
            }
            return of(prefix, len)
        }

        fun of(p: Path): FileType = of(p.toFile())

        fun of(prefix: ByteArray, len: Int): FileType {
            for (idx in 0 until len) {
                val c = prefix[idx].toInt().toChar()
                if (c.category == CharCategory.CONTROL &&
                    c != '\n' && c != '\r' &&
                    c != '\t') {
                    return binary
                }
            }
            return text
        }
    }
}
