plugins {
    id("org.springframework.boot") version "3.4.13"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.spring") version "2.0.20"
    kotlin("plugin.jpa") version "2.0.20"
    id("info.solidsoft.pitest") version "1.19.0-rc.3"
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
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.apache.commons:commons-compress:1.26.1")
    // Ledger: AWS QLDB driver (used when ledger.type=qldb)
    implementation("software.amazon.qldb:amazon-qldb-driver-java:2.3.1")
    // Vault: Spring Vault core (used when vault.enabled=true)
    implementation("org.springframework.vault:spring-vault-core:3.1.2")
    implementation("org.springframework.boot:spring-boot-starter-graphql")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("com.networknt:json-schema-validator:1.5.3")
    testRuntimeOnly("com.h2database:h2")
    testImplementation("org.springframework.amqp:spring-rabbit-test")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("au.com.dius.pact.provider:junit5spring:4.6.9")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Every distinct @SpringBootTest configuration keeps a cached application context, and the
    // suite outgrew the default 512 MB heap (OutOfMemoryError loading WorkloadIdentityTest). Evicting
    // contexts instead is not an option: closing one drops the shared in-memory test database.
    maxHeapSize = "1536m"
    // Lets EveryEndpointIsAuthorisedTest regenerate docs/api-authorisation-matrix.md (#155).
    systemProperty("factstore.update-matrix", System.getProperty("factstore.update-matrix") ?: "false")
    // Lets OpenApiSnapshotTest regenerate docs/openapi.json (#197).
    systemProperty("factstore.update-openapi", System.getProperty("factstore.update-openapi") ?: "false")
}

tasks.named<Test>("test") {
    exclude("**/pact/**")
    exclude("**/migration/**")
    // Measurements, not assertions: run with ./gradlew authBenchmark (#208).
    useJUnitPlatform { excludeTags("benchmark") }
}

// #197: fact schemas in schemas/ are the source of truth. Fails on a gap, an invalid schema, a
// breaking change between versions, or (with -PschemasBase=<dir>) an edit to a published version.
tasks.register<JavaExec>("schemaSpecCheck") {
    description = "Checks schemas/<name>/v<n>.json for validity and backwards compatibility (#197)"
    group = "verification"
    dependsOn("classes")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.factstore.tools.SchemaSpecCheck")
    val base = project.findProperty("schemasBase")?.toString()
    args = listOfNotNull(rootProject.file("../schemas").absolutePath, base)
}

tasks.register<Test>("eventSigningBenchmark") {
    description = "Measures the ingest latency added by signing stored events; writes build/reports/event-signing-benchmark.json (#184)"
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("benchmark") }
    filter { includeTestsMatching("*EventSigningBenchmark") }
    systemProperty("factstore.benchmark.out", layout.buildDirectory.file("reports/event-signing-benchmark.json").get().asFile.path)
    outputs.upToDateWhen { false }
}

tasks.register<Test>("authBenchmark") {
    description = "Measures authentication overhead per request; writes build/reports/auth-benchmark.json (#208)"
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("benchmark") }
    filter { includeTestsMatching("*AuthOverheadBenchmark") }
    systemProperty("factstore.benchmark.out", layout.buildDirectory.file("reports/auth-benchmark.json").get().asFile.path)
    outputs.upToDateWhen { false }
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

configure<info.solidsoft.gradle.pitest.PitestPluginExtension> {
    junit5PluginVersion.set("1.2.1")
    avoidCallsTo.set(setOf("kotlin.jvm.internal"))
    mutators.set(setOf("STRONGER"))
    targetClasses.set(setOf(
        "com.factstore.application.template.TemplateParser",
        "com.factstore.application.SlackCommandParser",
        "com.factstore.application.ScopeFilter",
        "com.factstore.application.CiContextResolver",
        "com.factstore.application.ComplianceMetricsService",
        "com.factstore.application.SecurityScanService"
    ))
    targetTests.set(setOf(
        "com.factstore.TemplateParserTest",
        "com.factstore.application.TemplateParserTest",
        "com.factstore.SlackCommandParserTest",
        "com.factstore.SnapshotScopeFilterTest",
        "com.factstore.CiContextResolverTest",
        "com.factstore.application.ComplianceMetricsServiceTest",
        "com.factstore.application.SecurityScanServiceTest"
    ))
    threads.set(Runtime.getRuntime().availableProcessors())
    outputFormats.set(setOf("XML", "HTML"))
    mutationThreshold.set(75)
    coverageThreshold.set(60)
}

tasks.named("check") {
    dependsOn("pitest")
}

tasks.bootJar {
    manifest {
        attributes["Implementation-Version"] = project.version
    }
}
