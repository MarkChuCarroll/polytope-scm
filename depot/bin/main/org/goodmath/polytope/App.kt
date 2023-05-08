/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */
package org.goodmath.polytope

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.goodmath.polytope.server.configureSecurity

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    configureSecurity()
//    configureHTTP()
//    configureMonitoring()
//    configureSerialization()
//    configureRouting()
}
