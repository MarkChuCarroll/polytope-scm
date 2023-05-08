package org.goodmath.polytope.client

import org.goodmath.polytope.common.PtException


class ClientException(val operation: String, val error: String, cause: Throwable?=null):
    PtException(PtException.Kind.Client, "Error while $operation: $error", cause)

class ClientCommandException(val noun: String,
                             val verb: String,
                             val error: String,
                             val exitCode: Int,
                             cause: Throwable? = null)
    : PtException(PtException.Kind.Client, "Error performing $noun $verb: $error", cause)


