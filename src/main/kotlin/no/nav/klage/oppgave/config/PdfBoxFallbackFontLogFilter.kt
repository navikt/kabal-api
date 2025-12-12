package no.nav.klage.oppgave.config

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.turbo.TurboFilter
import ch.qos.logback.core.spi.FilterReply
import no.nav.klage.oppgave.util.getLogger
import org.slf4j.Marker

class PdfBoxFallbackFontLogFilter : TurboFilter() {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val ourLogger = getLogger(javaClass.enclosingClass)
    }

    override fun decide(
        marker: Marker?,
        logger: Logger?,
        level: Level?,
        format: String?,
        params: Array<out Any>?,
        throwable: Throwable?
    ): FilterReply {
        if (level == Level.WARN &&
            logger?.name == "org.apache.pdfbox.pdmodel.font.PDType1Font" &&
            format?.contains("Using fallback font") == true
        ) {
            ourLogger.debug("Suppressing warning when pdfbox is using fallback font.")
            return FilterReply.DENY
        }

        return FilterReply.NEUTRAL
    }
}