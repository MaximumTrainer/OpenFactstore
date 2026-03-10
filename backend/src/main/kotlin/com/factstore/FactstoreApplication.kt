package com.factstore

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FactstoreApplication

fun main(args: Array<String>) {
    runApplication<FactstoreApplication>(*args)
}
