package org.goodmath.polytope.common.util

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FiletypeTest {
e    fun randomishByte(i: Int): Byte {
        return ((i * i) % 128).toByte()
    }

    fun randomishChar(i: Int): Byte {
        var base = (i % 128).toChar()
        if (base.category == CharCategory.CONTROL) {
            return randomishChar(i + 7)
        }
        return base.code.toByte()
    }

    @Test
    fun testFileTypeDetection() {
        val bytes = Array<Byte>(2048) { i -> randomishByte(i) }.toByteArray()
        val chars = Array<Byte>(2048) { i -> randomishChar(i) }.toByteArray()
        val bstr = bytes.toString(Charsets.UTF_8)
        val cstr = chars.toString(Charsets.UTF_8)

        assertEquals(FileType.text, FileType.of(chars, 2048))
        assertEquals(FileType.binary, FileType.of(bytes, 2048))
    }
}
