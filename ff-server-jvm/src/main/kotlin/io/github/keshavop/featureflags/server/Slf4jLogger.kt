package io.github.keshavop.featureflags.server

import io.github.keshavop.featureflags.core.log.FeatureFlagsLogger
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** Default logger: SLF4J, so the SDK logs through whatever backend the application uses. */
public class Slf4jLogger
    @JvmOverloads
    constructor(
        private val logger: Logger = LoggerFactory.getLogger("io.github.keshavop.featureflags"),
    ) : FeatureFlagsLogger {
        override fun debug(message: String): Unit = logger.debug(message)

        override fun info(message: String): Unit = logger.info(message)

        override fun warn(
            message: String,
            error: Throwable?,
        ): Unit = logger.warn(message, error)

        override fun error(
            message: String,
            error: Throwable?,
        ): Unit = logger.error(message, error)
    }
