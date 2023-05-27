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

val guavaVersion: String by project
val hopliteVersion: String by project
val junitVersion: String by project
val klaxonVersion: String by project
val kotlinLibVersion: String by project
val kotlinSerializationVersion: String by project
val logbackVersion: String by project
val mockkVersion: String by project
val rocksDbVersion: String by project
val slf4jVersion: String by project

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.8.10"
    kotlin("plugin.serialization") version "1.8.10"
    id("io.ktor.plugin") version "2.3.0"
    application
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":common"))
    implementation(project(mapOf("path" to ":common")))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinLibVersion")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-common:$kotlinLibVersion")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinLibVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinSerializationVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")

    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-auth-jvm")
    implementation("io.ktor:ktor-server-auth-jwt-jvm")
    implementation("io.ktor:ktor-server-sessions-jvm")
    implementation("io.ktor:ktor-server-resources")
    implementation("io.ktor:ktor-server-compression-jvm")
    implementation("io.ktor:ktor-server-openapi")
    implementation("io.ktor:ktor-server-swagger")
    implementation("io.ktor:ktor-server-call-logging-jvm")
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    implementation("com.google.guava:guava:$guavaVersion")
    implementation("io.maryk.rocksdb:rocksdb-multiplatform:$rocksDbVersion")
    implementation("com.beust:klaxon:$klaxonVersion")
    implementation("com.sksamuel.hoplite:hoplite-core:$hopliteVersion")

    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("io.ktor:ktor-server-tests-jvm")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
}

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
