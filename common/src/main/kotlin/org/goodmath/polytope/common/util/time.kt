package org.goodmath.polytope.common.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

fun toLocalDateTime(ms: Long): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())
