package icu.neurospicy.fibi

import org.springframework.boot.fromApplication


fun main(args: Array<String>) {
    fromApplication<FibiApplication>().run(*args)
}
