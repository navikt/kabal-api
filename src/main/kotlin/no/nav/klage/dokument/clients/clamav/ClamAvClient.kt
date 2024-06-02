package no.nav.klage.dokument.clients.clamav

import no.nav.klage.oppgave.util.getLogger
import org.springframework.core.io.FileSystemResource
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.io.File

@Component
class ClamAvClient(private val clamAvWebClient: WebClient) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun hasVirus(file: File): Boolean {
        logger.debug("Scanning document")

        var start = System.currentTimeMillis()
        val bodyBuilder = MultipartBodyBuilder()
        bodyBuilder.part(file.name, FileSystemResource(file)).filename(file.name)
        logger.debug("File added to body. Time taken: ${System.currentTimeMillis() - start} ms")

        start = System.currentTimeMillis()
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
        logger.debug("Response received. Time taken: ${System.currentTimeMillis() - start} ms")

        if (response == null) {
            logger.warn("No response from virus scan.")
            return false
        }

        if (response.size != 1) {
            logger.warn("Wrong size response from virus scan.")
            return false
        }

        val (_, result) = response[0]
        logger.debug("${file.name} ${result.name}")
        return when (result) {
            ClamAvResult.OK -> false
            ClamAvResult.FOUND -> {
                logger.warn("${file.name} has virus")
                true
            }
            ClamAvResult.ERROR -> {
                logger.warn("Error from virus scan for file ${file.name}")
                throw RuntimeException("Error from virus scan for file ${file.name}")
            }
        }
    }
}