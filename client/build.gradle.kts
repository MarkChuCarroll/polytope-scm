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

val cliktVersion: String by project
val guavaVersion: String by project
val hopliteVersion: String by project
val junitVersion: String by project
val klaxonVersion: String by project
val kotlinLibVersion: String by project
val kotlinSerializationVersion: String by project
val kotlinVersion: String by project
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

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":common"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinLibVersion")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-common:$kotlinLibVersion")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinLibVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinSerializationVersion")
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-cio")
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-client-auth")


    implementation("io.maryk.rocksdb:rocksdb-multiplatform:$rocksDbVersion")
    implementation("com.beust:klaxon:$klaxonVersion")
    implementation("com.sksamuel.hoplite:hoplite-core:$hopliteVersion")
    implementation("com.github.ajalt.clikt:clikt:$cliktVersion")
    implementation(project(mapOf("path" to ":common")))
    implementation("io.ktor:ktor-client-encoding")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("io.maryk.rocksdb:rocksdb-multiplatform:$rocksDbVersion")


    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    implementation("com.google.guava:guava:$guavaVersion")
    implementation("io.mockk:mockk:$mockkVersion")
}

application {
    mainClass.set("org.goodmath.polytope.client.commands.CommandLineKt")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}