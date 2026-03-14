plugins {
    id("org.springframework.boot") version "3.4.13"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.spring") version "2.0.20"
    kotlin("plugin.jpa") version "2.0.20"
}

group = "com.factstore"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.16")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.apache.commons:commons-compress:1.26.1")
    // Ledger: AWS QLDB driver (used when ledger.type=qldb)
    implementation("software.amazon.qldb:amazon-qldb-driver-java:2.3.1")
    // Vault: Spring Vault core (used when vault.enabled=true)
    implementation("org.springframework.vault:spring-vault-core:3.1.2")
    testRuntimeOnly("com.h2database:h2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("au.com.dius.pact.provider:junit5spring:4.6.9")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<Test>("test") {
    exclude("**/pact/**")
    exclude("**/migration/**")
}

tasks.register<Test>("contractTest") {
    description = "Runs provider Pact contract verification tests"
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    include("**/pact/**")
    filter { isFailOnNoMatchingTests = false }
}

tasks.register<Test>("migrationTest") {
    description = "Validates Flyway migrations against a real PostgreSQL instance"
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    include("**/migration/**")
    filter { isFailOnNoMatchingTests = false }
}

tasks.bootJar {
    manifest {
        attributes["Implementation-Version"] = project.version
    }
}
