package no.nav.klage.oppgave.clients.saf.rest

import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.klage.oppgave.util.logErrorResponse
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.nio.file.Files
import java.nio.file.Path


@Component
class SafRestClient(
    private val safWebClient: WebClient,
    private val tokenUtil: TokenUtil,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    @Retryable
    fun getDokument(
        dokumentInfoId: String,
        journalpostId: String,
        variantFormat: String = "ARKIV"
    ): Resource {
        return try {
            runWithTimingAndLogging {
                val dataBufferFlux = safWebClient.get()
                    .uri(
                        "/rest/hentdokument/{journalpostId}/{dokumentInfoId}/{variantFormat}",
                        journalpostId,
                        dokumentInfoId,
                        variantFormat
                    )
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithSafScope()}"
                    )
                    .retrieve()
                    .onStatus(HttpStatusCode::isError) { response ->
                        logErrorResponse(response, ::getDokument.name, secureLogger)
                    }
                    .bodyToFlux(DataBuffer::class.java)

                val tempFile = Files.createTempFile(null, null)

                DataBufferUtils.write(dataBufferFlux, tempFile).block()
                FileSystemResource(tempFile)
            }
        } catch (badRequest: WebClientResponseException.BadRequest) {
            logger.warn("Got a 400 fetching dokument with journalpostId $journalpostId, dokumentInfoId $dokumentInfoId and variantFormat $variantFormat")
            throw badRequest
        } catch (unautorized: WebClientResponseException.Unauthorized) {
            logger.warn("Got a 401 fetching dokument with journalpostId $journalpostId, dokumentInfoId $dokumentInfoId and variantFormat $variantFormat")
            throw unautorized
        } catch (forbidden: WebClientResponseException.Forbidden) {
            logger.warn("Got a 403 fetching dokument with journalpostId $journalpostId, dokumentInfoId $dokumentInfoId and variantFormat $variantFormat")
            throw forbidden
        } catch (notFound: WebClientResponseException.NotFound) {
            logger.warn("Got a 404 fetching dokument with journalpostId $journalpostId, dokumentInfoId $dokumentInfoId and variantFormat $variantFormat")
            throw notFound
        }
    }

    fun getDokumentAsStream(
        dokumentInfoId: String,
        journalpostId: String,
        variantFormat: String = "ARKIV"
    ): Pair<Flux<DataBuffer>, ClientResponse.Headers> {
        return try {
            runWithTimingAndLogging {
                var headers: ClientResponse.Headers? = null
                val dataBufferFlux = safWebClient.get()
                    .uri(
                        "/rest/hentdokument/{journalpostId}/{dokumentInfoId}/{variantFormat}",
                        journalpostId,
                        dokumentInfoId,
                        variantFormat
                    )
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithSafScope()}"
                    )
                    .exchangeToFlux {
                        headers = it.headers()
                        it.bodyToFlux(DataBuffer::class.java)
                    }

                dataBufferFlux to headers!!
            }
        } catch (badRequest: WebClientResponseException.BadRequest) {
            logger.warn("Got a 400 fetching dokument with journalpostId $journalpostId, dokumentInfoId $dokumentInfoId and variantFormat $variantFormat")
            throw badRequest
        } catch (unautorized: WebClientResponseException.Unauthorized) {
            logger.warn("Got a 401 fetching dokument with journalpostId $journalpostId, dokumentInfoId $dokumentInfoId and variantFormat $variantFormat")
            throw unautorized
        } catch (forbidden: WebClientResponseException.Forbidden) {
            logger.warn("Got a 403 fetching dokument with journalpostId $journalpostId, dokumentInfoId $dokumentInfoId and variantFormat $variantFormat")
            throw forbidden
        } catch (notFound: WebClientResponseException.NotFound) {
            logger.warn("Got a 404 fetching dokument with journalpostId $journalpostId, dokumentInfoId $dokumentInfoId and variantFormat $variantFormat")
            throw notFound
        }
    }

    @Retryable
    fun downloadDocumentAsMono(
        dokumentInfoId: String,
        journalpostId: String,
        variantFormat: String = "ARKIV",
        pathToFile: Path,
    ): Mono<Void> {
        return try {
            runWithTimingAndLogging {
                val flux: Flux<DataBuffer> = safWebClient.get()
                    .uri(
                        "/rest/hentdokument/{journalpostId}/{dokumentInfoId}/{variantFormat}",
                        journalpostId,
                        dokumentInfoId,
                        variantFormat
                    )
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithSafScope()}"
                    )
                    .retrieve()
                    .onStatus(HttpStatusCode::isError) { response ->
                        logErrorResponse(response, ::downloadDocumentAsMono.name, secureLogger)
                    }
                    .bodyToFlux(DataBuffer::class.java)

                DataBufferUtils.write(flux, pathToFile)
            }
        } catch (badRequest: WebClientResponseException.BadRequest) {
            logger.warn("Got a 400 fetching dokument with journalpostId $journalpostId, dokumentInfoId $dokumentInfoId and variantFormat $variantFormat")
            throw badRequest
        } catch (unautorized: WebClientResponseException.Unauthorized) {
            logger.warn("Got a 401 fetching dokument with journalpostId $journalpostId, dokumentInfoId $dokumentInfoId and variantFormat $variantFormat")
            throw unautorized
        } catch (forbidden: WebClientResponseException.Forbidden) {
            logger.warn("Got a 403 fetching dokument with journalpostId $journalpostId, dokumentInfoId $dokumentInfoId and variantFormat $variantFormat")
            throw forbidden
        } catch (notFound: WebClientResponseException.NotFound) {
            logger.warn("Got a 404 fetching dokument with journalpostId $journalpostId, dokumentInfoId $dokumentInfoId and variantFormat $variantFormat")
            throw notFound
        }
    }

    fun <T> runWithTimingAndLogging(block: () -> T): T {
        val start = System.currentTimeMillis()
        try {
            return block.invoke()
        } finally {
            val end = System.currentTimeMillis()
            logger.debug("Time it took to call saf: ${end - start} millis")
        }
    }
}


