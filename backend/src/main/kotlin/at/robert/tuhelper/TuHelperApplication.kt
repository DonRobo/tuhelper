package at.robert.tuhelper

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TuHelperApplication

fun main(args: Array<String>) {
	runApplication<TuHelperApplication>(*args)
}
