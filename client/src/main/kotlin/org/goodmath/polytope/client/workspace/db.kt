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

package org.goodmath.polytope.client.workspace

import maryk.rocksdb.RocksDB
import maryk.rocksdb.WriteBatch
import org.goodmath.polytope.common.util.ParsingCommons
import org.goodmath.polytope.common.util.parse
import org.rocksdb.WriteOptions
import kotlin.text.Charsets.UTF_8


// A collection of extension functions for RocksDB to make code that saves and retrieves
// typed data cleaner. It's annoying, but due to the way that kotlin and gradle handle
// extension functions with reified types, this needs to be duplicated from the server
// instead of being shared in common.

/**
 * A version of rocksdb "put" that takes strings for the key and value instead of
 * byte arrays.
 */
fun RocksDB.put(key: String, value: String) {
    val keyBytes = key.toByteArray(UTF_8)
    val valueBytes = value.toByteArray(UTF_8)
    this.put(keyBytes, valueBytes)
}

/**
 * A version of rocksdb "put" that takes a typed value, and stores
 * it as rendered json.
 */
fun<T> RocksDB.putTyped(key: String, value: T) {
    this.put(key, ParsingCommons.klaxon.toJsonString(value))
}


/**
 * For clean updates, this is a helper that batches together deleting
 * the old value, and writing the new one, using the typed value
 * mechanism from above.
 */
fun<T> RocksDB.updateTyped(key: String, value: T) {
    val wb = WriteBatch()
    wb.delete(key.toByteArray())
    wb.putTyped(key, value)
    this.write(WriteOptions(), wb)
}

/**
 * For clean updates, this is a helper that lets you use putTyped
 * in a batch.
  */
fun<T> WriteBatch.putTyped(key: String, value: T) {
    this.put(key.toByteArray(UTF_8), ParsingCommons.klaxon.toJsonString(value).toByteArray(UTF_8))
}


/**
 * The counterpart to typed get. This will read a json string from
 * the DB, and then parse it into a typed value using kotlin serialization + klaxon.
 */
inline fun<reified T> RocksDB.getTyped(key: String): T? {
    val keyBytes = key.toByteArray(UTF_8)
    val resultBytes = this.get(keyBytes)
    return if (resultBytes != null) {
        ParsingCommons.klaxon.parse<T>(resultBytes)
    } else {
        null
    }
}

