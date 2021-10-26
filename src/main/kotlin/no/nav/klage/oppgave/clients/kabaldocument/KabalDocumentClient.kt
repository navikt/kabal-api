package no.nav.klage.oppgave.clients.kabaldocument

import brave.Tracer
import no.nav.klage.oppgave.clients.kabaldocument.model.request.DokumentEnhetInput
import no.nav.klage.oppgave.clients.kabaldocument.model.request.FilInput
import no.nav.klage.oppgave.clients.kabaldocument.model.response.DokumentEnhetOutput
import no.nav.klage.oppgave.clients.kabaldocument.model.response.HovedDokumentEditedOutput
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.util.*

@Component
class KabalDocumentClient(
    private val kabalDocumentWebClient: WebClient,
    private val tokenUtil: TokenUtil,
    private val tracer: Tracer
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun createDokumentEnhet(
        input: DokumentEnhetInput
    ): DokumentEnhetOutput {
        return kabalDocumentWebClient.post()
            .uri { it.path("/dokumentenheter").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithKabalDocumentScope()}"
            )
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(input)
            .header("Nav-Call-Id", tracer.currentSpan().context().traceIdString())
            .retrieve()
            .bodyToMono<DokumentEnhetOutput>()
            .block() ?: throw RuntimeException("Dokumentenhet could not be created")
    }

    fun getDokumentEnhet(
        dokumentEnhetId: UUID,
    ): DokumentEnhetOutput {
        return kabalDocumentWebClient.get()
            .uri { it.path("/dokumentenheter/{dokumentEnhetId}").build(dokumentEnhetId) }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithKabalDocumentScope()}"
            )
            .header("Nav-Call-Id", tracer.currentSpan().context().traceIdString())
            .retrieve()
            .bodyToMono<DokumentEnhetOutput>()
            .block() ?: throw RuntimeException("Dokumentenhet could not be fetched")

    }

    fun uploadHovedDokument(
        dokumentEnhetId: UUID,
        input: FilInput
    ): HovedDokumentEditedOutput {
        val bodyBuilder = MultipartBodyBuilder()
        bodyBuilder.part("file", input.file.bytes).filename(input.file.originalFilename)
        return kabalDocumentWebClient
            .post()
            .uri { it.path("/dokumentenheter/{dokumentEnhetId}/innhold").build(dokumentEnhetId) }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithKabalDocumentScope()}"
            )
            .header("Nav-Call-Id", tracer.currentSpan().context().traceIdString())
            .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
            .retrieve()
            .bodyToMono<HovedDokumentEditedOutput>()
            .block()
    }

    fun getHovedDokument(dokumentEnhetId: UUID): Pair<ByteArray, MediaType> {
        return kabalDocumentWebClient.get()
            .uri { it.path("/dokumentenheter/{dokumentEnhetId}/innhold").build(dokumentEnhetId) }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithKabalDocumentScope()}"
            )
            .header("Nav-Call-Id", tracer.currentSpan().context().traceIdString())
            .retrieve()
            .toEntity(ByteArray::class.java)
            .map {
                val type = it.headers.contentType
                Pair(
                    it.body ?: throw RuntimeException("no document data"),
                    type ?: throw RuntimeException("no content type")
                )
            }
            .block() ?: throw RuntimeException("Hoveddokument could not be fetched")
    }

    fun deleteHovedDokument(
        dokumentEnhetId: UUID,
    ): HovedDokumentEditedOutput {
        return kabalDocumentWebClient.delete()
            .uri { it.path("/dokumentenheter/{dokumentEnhetId}/innhold").build(dokumentEnhetId) }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithKabalDocumentScope()}"
            )
            .header("Nav-Call-Id", tracer.currentSpan().context().traceIdString())
            .retrieve()
            .bodyToMono<HovedDokumentEditedOutput>()
            .block() ?: throw RuntimeException("Hoveddokument could not be deleted")
    }

    fun fullfoerDokumentEnhet(
        dokumentEnhetId: UUID
    ) {
        kabalDocumentWebClient.post()
            .uri { it.path("/dokumentenheter/{dokumentEnhetId}/fullfoer").build(dokumentEnhetId) }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getAppAccessTokenWithKabalDocumentScope()}"
            )
            .header("Nav-Call-Id", tracer.currentSpan().context().traceIdString())
            .retrieve()
    }
}