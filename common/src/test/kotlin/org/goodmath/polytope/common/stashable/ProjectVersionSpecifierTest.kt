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

import org.goodmath.polytope.common.stashable.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ProjectVersionSpecifierTest {
    @Test
    fun testParseProjectVersionSpecifier() {
        val one = ProjectVersionSpecifier.fromString("history(pr@mine@17)")
        assertEquals(ProjectVersionSpecifier.history(ID_PROJECT, "mine", 17), one)
        val two = ProjectVersionSpecifier.fromString("history(pr@mine)")
        assertEquals(ProjectVersionSpecifier.history(ID_PROJECT, "mine", null), two)
        val three = ProjectVersionSpecifier.fromString("change(pr@hist@this_one)")
        assertEquals(ProjectVersionSpecifier.change(ID_PROJECT, "hist", "this_one"), three)
        val id = newId<SavePoint>(ID_CHANGE_SAVE)
        val four = ProjectVersionSpecifier.fromString("savePoint(ps@hist@$id)")
        assertEquals(ProjectVersionSpecifier.savePoint("ps", "hist", id).toString(), four.toString())
    }
}