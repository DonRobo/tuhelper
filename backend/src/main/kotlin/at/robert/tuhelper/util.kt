package at.robert.tuhelper

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

private val loggers = ConcurrentHashMap<Class<*>, Logger>()
val Any.log: Logger
    get() = loggers.getOrPut(this::class.java) {
        LoggerFactory.getLogger(this::class.java)
    }

fun String.getUriParameter(param: String): String? = getUriParameters()[param]

fun String.getUriParameters(): Map<String, String> {
    val params = mutableMapOf<String, String>()
    val parts = this.split("?")
    if (parts.size == 2) {
        val paramParts = parts[1].split("&")
        paramParts.forEach {
            val param = it.split("=")
            if (param.size == 2) {
                params[param[0]] = param[1]
            }
        }
    }
    return params
}
