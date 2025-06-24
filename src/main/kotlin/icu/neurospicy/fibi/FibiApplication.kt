package icu.neurospicy.fibi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FibiApplication

fun main(args: Array<String>) {
    runApplication<FibiApplication>(*args)
}
