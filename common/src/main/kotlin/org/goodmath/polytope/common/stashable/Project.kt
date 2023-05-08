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


enum class ProjectVersionSpecifierKind {
    History,
    Change,
    SavePoint,
    Baseline
}

@Serializable
data class ProjectVersionSpecifier(
    var kind: ProjectVersionSpecifierKind,
    val project: String,
    val history: String,
    val changeName: String? = null,
    val baselineId: Id<ArtifactVersion>? = null,
    val savePointId: Id<SavePoint>? = null,
    val number: Int?= null
) {

    companion object {
        fun history(project: String, history: String, number: Int? = null): ProjectVersionSpecifier {
            return ProjectVersionSpecifier(ProjectVersionSpecifierKind.History, project, history, null,null, null, number)
        }

        fun change(project: String, history: String, change: String): ProjectVersionSpecifier {
            return ProjectVersionSpecifier(ProjectVersionSpecifierKind.Change, project, history, change)
        }

        fun savePoint(project: String, history: String, savePoint: Id<SavePoint>): ProjectVersionSpecifier {
            return ProjectVersionSpecifier(ProjectVersionSpecifierKind.SavePoint, project, history, savePointId= savePoint)
        }

        fun baseline(project: String, history: String, baselineVersionId: Id<ArtifactVersion>): ProjectVersionSpecifier {
            return ProjectVersionSpecifier(ProjectVersionSpecifierKind.Baseline, project, history, baselineId = baselineVersionId)
        }

        private val pvsRe = Regex("(\\w*)\\((.*)\\)")

        // history(project@history) | history(project@history@version)
        // change(project@history@change)
        // savePoint(project@history@id)
        // baseline(project@history@id)
        fun fromString(s: String): ProjectVersionSpecifier {
            val matches = pvsRe.matchEntire(s) ?: throw PtException(
                PtException.Kind.InvalidParameter,
                "Invalid project version specifier"
            )
            val (kind, spec) = matches.destructured
            when (kind) {
                "history" -> {
                    val specParts = spec.split("@")
                    return when (specParts.size) {
                        2 -> {
                            history(specParts[0], specParts[1], null)
                        }
                        3 -> {
                            history(
                                specParts[0],
                                specParts[1], specParts[2].toInt()
                            )
                        }
                        else -> {
                            throw PtException(
                                PtException.Kind.InvalidParameter,
                                "Invalid project version specifier string '$s'"
                            )
                        }
                    }
                }
                "change" -> {
                    val specParts = spec.split("@")
                    return if (specParts.size == 3) {
                        change(
                            specParts[0],
                            specParts[1],
                            specParts[2]
                        )
                    } else {
                        throw PtException(
                            PtException.Kind.InvalidParameter,
                            "Invalid project version specifier string '$s'"
                        )
                    }
                }
                "savePoint" -> {
                    val specParts = spec.split("@")
                    return if (specParts.size == 3) {
                        savePoint(
                            specParts[0], specParts[1], savePoint = specParts[2])
                    } else {
                        throw PtException(
                            PtException.Kind.InvalidParameter,
                            "Invalid project version specifier string '$s'"
                        )
                    }
                }
                "baseline" -> {
                    val specParts = spec.split("@")
                    return if (specParts.size == 3) {
                        baseline(specParts[0], specParts[1], baselineVersionId = specParts[2])
                    } else {
                        throw PtException(
                            PtException.Kind.InvalidParameter,
                            "Invalid project version specifier string '$s'"
                        )

                    }
                }
                else ->
                    throw PtException(
                        PtException.Kind.InvalidParameter,
                        "Invalid project version specifier type in '$s'"
                    )
            }
        }
    }
}

@Serializable
data class Project(
    val name: String,
    val creator: String,
    val timestamp: Long,
    val description: String,
    val rootDir: Id<Artifact>,
    val baseline: Id<Artifact>,
    val histories: List<Id<History>>
)
