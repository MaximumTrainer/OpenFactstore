package com.factstore

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import kotlin.system.exitProcess

@SpringBootApplication
class FactstoreApplication

internal val APP_VERSION: String =
    FactstoreApplication::class.java.`package`.implementationVersion ?: "0.0.1-SNAPSHOT"

internal val HELP_TEXT = """
    Factstore - Supply Chain Compliance Fact Store

    Usage: java -jar factstore.jar [options]
           docker run -p 8080:8080 ghcr.io/maximumtrainer/factstore:<tag> [options]

    Options:
      --help                       Show this help message and exit
      --version                    Show version information and exit
      --server.port=<port>         Override the HTTP server port (default: 8080)
      --spring.profiles.active=<p> Set the active Spring profile

    Examples:
      java -jar factstore.jar
      java -jar factstore.jar --server.port=9090
      docker run -p 8080:8080 ghcr.io/maximumtrainer/factstore:latest

    API Documentation (when running):
      Swagger UI:   http://localhost:8080/swagger-ui.html
      OpenAPI JSON: http://localhost:8080/api-docs

    See DEPLOY.md for full deployment instructions.
""".trimIndent()

/**
 * Handles --help and --version CLI flags.
 * Returns true if a flag was handled (caller should exit), false otherwise.
 */
internal fun handleCliArgs(args: Array<String>): Boolean {
    if ("--help" in args) {
        println(HELP_TEXT)
        return true
    }
    if ("--version" in args) {
        println("Factstore $APP_VERSION")
        return true
    }
    return false
}

fun main(args: Array<String>) {
    if (handleCliArgs(args)) {
        exitProcess(0)
    }
    runApplication<FactstoreApplication>(*args)
}
