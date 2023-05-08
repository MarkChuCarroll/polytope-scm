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

package org.goodmath.polytope.common

import io.ktor.http.*

open class PtException(
    val kind: Kind,
    val msg: String,
    override val cause: Throwable? = null): Exception("Error($kind): $msg", cause) {

    enum class Kind {
        Internal, InvalidParameter, Permission,
        NotFound, Conflict, Authentication,
        Parsing, Constraint, TypeError,
        UserError, Client;

        companion object {
            fun fromStatusCode(code: HttpStatusCode): Kind {
                return when(code) {
                    HttpStatusCode.InternalServerError -> Internal
                    HttpStatusCode.BadRequest -> InvalidParameter
                    HttpStatusCode.Forbidden -> Permission
                    HttpStatusCode.NotFound -> NotFound
                    HttpStatusCode.Conflict -> Conflict
                    HttpStatusCode.Unauthorized -> Authentication
                    HttpStatusCode.PreconditionFailed -> Constraint
                    HttpStatusCode.UnprocessableEntity -> Parsing
                    HttpStatusCode.ExpectationFailed -> TypeError
                    HttpStatusCode.NotAcceptable -> UserError
                    else -> Internal
                }
            }

        }
    }

    constructor(status: HttpStatusCode, msg: String):
            this(Kind.fromStatusCode(status), msg, null)

    fun toStatusCode(): HttpStatusCode {
        return when(kind) {
            Kind.Internal -> HttpStatusCode.InternalServerError
            Kind.InvalidParameter -> HttpStatusCode.BadRequest
            Kind.Permission -> HttpStatusCode.Forbidden
            Kind.NotFound -> HttpStatusCode.NotFound
            Kind.Conflict -> HttpStatusCode.Conflict
            Kind.Authentication -> HttpStatusCode.Unauthorized
            Kind.Constraint -> HttpStatusCode.PreconditionFailed
            Kind.Parsing -> HttpStatusCode.UnprocessableEntity
            Kind.TypeError -> HttpStatusCode.ExpectationFailed
            Kind.UserError -> HttpStatusCode.NotAcceptable
            Kind.Client -> HttpStatusCode.InternalServerError
        }
    }
}