package icu.neurospicy.fibi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.fromApplication


@SpringBootApplication
class TestFibiApplication {

    fun main(args: Array<String>) {
        fromApplication<FibiApplication>().run(*args)
    }

}