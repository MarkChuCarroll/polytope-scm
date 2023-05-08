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

package org.goodmath.polytope.depot.util

import com.beust.klaxon.Klaxon
import org.goodmath.polytope.PtException
import kotlin.text.Charsets.UTF_8

object ParsingCommons {
    val klaxon = Klaxon()
}

inline fun<reified T> Klaxon.parse(bytes: ByteArray): T =
    ParsingCommons.klaxon.parse<T>(bytes.toString(UTF_8))
        ?: throw PtException(PtException.Kind.Parsing,
            "Error parsing json representation")

fun<T> Klaxon.toBytes(t: T): ByteArray =
    ParsingCommons.klaxon.toJsonString(t).toByteArray(UTF_8)