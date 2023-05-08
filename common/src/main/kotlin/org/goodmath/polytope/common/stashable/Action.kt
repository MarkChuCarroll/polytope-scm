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

import kotlinx.serialization.Serializable
import org.goodmath.polytope.common.PtException


enum class ActionLevel {
    Read, Write, Delete, Admin
}


enum class ActionScopeType {
    Project, Depot, Global
}

/*
 * For permissions:
 * - a permission grant the ability to perform a set of operations in a particular
 *   scope.
 * - Scopes are hierarchical: depot scopes include all project scopes; Global scopes
 *   include all project and depot scopes.
 * - every scope has a scope name. The scope name "*" includes all scopes of that level.
 * - The set of permitted operations is determined by the action level.
 *   Action levels are ordered, and higher action levels include the levels beneath them.
 */
@Serializable
data class Action(
    val scopeType: ActionScopeType,
    val scopeName: String,
    val level: ActionLevel
) {

    /**
     * A permission action includes a requested action if the requested action
     * is at a lower level; or if the requested action is at the same level as
     * the permission with the same scope name; and the action level of the
     * requested action is less than or equal to the action level of the permission
     *
     * @param requested a requested action.
     * @return true if this permitted action includes the requested action.
     */
    private fun includes(requested: Action): Boolean {
        return (level >= requested.level) &&
            (scopeType > requested.scopeType ||
                (scopeType == requested.scopeType && (scopeName == requested.scopeName ||
                        scopeName == "*")))

    }


    fun permittedFor(user: AuthenticatedUser): Boolean {
        return user.permittedActions.any { it.includes(this) }
    }

    fun render(): String {
        val s = when(scopeType) {
            ActionScopeType.Global -> "G"
            ActionScopeType.Project -> "P"
            ActionScopeType.Depot -> "D"
        }
        val l = when(level) {
            ActionLevel.Admin -> "A"
            ActionLevel.Delete -> "D"
            ActionLevel.Write -> "W"
            ActionLevel.Read -> "R"
        }
        return "$s$l:$scopeName"
    }

    companion object {

        val adminUsers = Action(
            ActionScopeType.Global,
            "users",
            ActionLevel.Admin
        )

        val readUsers = Action(
            ActionScopeType.Global,
            "users",
            ActionLevel.Read
        )

        fun writeProject(project: String) = Action(
            ActionScopeType.Project,
            project,
            ActionLevel.Write
        )

        fun deleteProject(project: String) = Action(
            ActionScopeType.Project,
            project,
            ActionLevel.Delete
        )


        fun readProject(project: String) = Action(
            ActionScopeType.Project,
            project,
            ActionLevel.Read
        )

        fun adminProject(project: String) = Action(
            ActionScopeType.Project,
            project,
            ActionLevel.Admin
        )

        val readDepot = Action(
            ActionScopeType.Depot,
            "projects",
            ActionLevel.Read
        )

        val writeDepot = Action(
            ActionScopeType.Depot,
            "projects",
            ActionLevel.Write
        )

        val createProject = Action(
            ActionScopeType.Depot,
            "*",
            ActionLevel.Write
        )

        /**
         * Parse an action from a human-friendly concise syntax.
         *
         * An action is written:
         *    SL:str where S is the abbreviation of the scope type,
         *       L is the abbreviation of the action level, and str
         *       is the scope name.
         *
         * Action levels Read, Write, Delete, and Admin are abbreviated to RWDA
         * Scope types are abbreviate to G for global, D for depot, and P for project.
         *
         * So global admin would be written GA:*, administration privs on a project foo
         * would be PA:foo, etc.
         */
        fun parse(act: String): Action {
            val match = actionRe.matchEntire(act)
                ?: throw PtException(PtException.Kind.Parsing,
                    "Invalid action string; could not parse '${act}'")
            val (scopeAbbr, levelAbbr, name) = match.destructured
            val scope = when(scopeAbbr) {
                "G", "g" -> ActionScopeType.Global
                "D", "d" -> ActionScopeType.Depot
                "P", "p" -> ActionScopeType.Project
                else -> throw PtException(PtException.Kind.Parsing,
                    "Invalid scope type '$scopeAbbr'")
            }
            val level = when(levelAbbr) {
                "R", "r" -> ActionLevel.Read
                "W", "w" -> ActionLevel.Write
                "D", "d" -> ActionLevel.Delete
                "A", "a" -> ActionLevel.Admin
                else -> throw PtException(PtException.Kind.Parsing,
                    "Invalid action level '$levelAbbr'")
            }
            return Action(scope, name, level)
        }
        val actionRe = Regex("([GgDdPp])([RrWwDdAa]):(.*)")

    }


}
