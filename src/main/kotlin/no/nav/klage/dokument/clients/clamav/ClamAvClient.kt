package no.nav.klage.dokument.clients.clamav

import no.nav.klage.oppgave.util.getLogger
import org.springframework.core.io.Resource
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class ClamAvClient(private val clamAvWebClient: WebClient) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun scan(resource: Resource): Boolean {
        logger.debug("Scanning document")

        val bodyBuilder = MultipartBodyBuilder()
        bodyBuilder.part("file", resource).filename("file")

        val response = try {
            clamAvWebClient.post()
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .retrieve()
                .bodyToMono<List<ScanResult>>()
                .block()
        } catch (ex: Throwable) {
            logger.warn("Error from clamAV", ex)
            listOf(ScanResult("Unknown", ClamAvResult.ERROR))
        }

        if (response == null) {
            logger.warn("No response from virus scan.")
            return false
        }

        if (response.size != 1) {
            logger.warn("Wrong size response from virus scan.")
            return false
        }

        val (filename, result) = response[0]
        logger.debug("$filename ${result.name}")
        return when (result) {
            ClamAvResult.OK -> true
            ClamAvResult.FOUND -> {
                logger.warn("$filename has virus")
                false
            }
            ClamAvResult.ERROR -> {
                logger.warn("Error from virus scan on file $filename")
                false
            }
        }
    }
}