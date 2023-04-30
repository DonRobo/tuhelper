package at.robert.tuhelper

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

private val loggers = ConcurrentHashMap<Class<*>, Logger>()
val Any.log: Logger
    get() = loggers.getOrPut(this::class.java) {
        LoggerFactory.getLogger(this::class.java)
    }
