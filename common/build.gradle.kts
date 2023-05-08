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
val kotlinVersion: String by project
val logbackVersion: String by project
val mockkVersion: String by project

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.8.10"
    kotlin("plugin.serialization") version "1.8.10"
    id("io.ktor.plugin") version "2.3.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinLibVersion")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-common:$kotlinLibVersion")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinLibVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")

    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")
    implementation("io.ktor:ktor-client-core")

    implementation("com.beust:klaxon:$klaxonVersion")

    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    implementation("com.google.guava:guava:$guavaVersion")
    implementation("io.mockk:mockk:$mockkVersion")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("org.goodmath.polytope.common.StubKt")
}