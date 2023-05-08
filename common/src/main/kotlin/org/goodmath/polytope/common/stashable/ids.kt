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

package org.goodmath.polytope.common.stashable

import java.util.*

const val ID_PROJECT: String = "pr"
const val ID_CHANGE: String = "chg"
const val ID_CHANGE_SAVE: String = "save"
const val ID_CONFLICT: String = "conflict"
const val ID_ARTIFACT: String = "art"
const val ID_VERSION: String = "ver"
const val ID_HISTORY: String = "ver"
const val ID_HISTORY_STEP: String = "step"
const val ID_WORKSPACE: String = "ws"

typealias Id<@Suppress("UNUSED_TYPEALIAS_PARAMETER") T> = String
fun<T> newId(kind: String) = kind + ":" + UUID.randomUUID().toString()

fun<T> idFrom(s: String): Id<T> = s
