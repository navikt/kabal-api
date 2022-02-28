package no.nav.klage.oppgave.clients.kabaldocument

import brave.Tracer
import no.nav.klage.oppgave.clients.kabaldocument.model.request.DokumentEnhetWithDokumentreferanserInput
import no.nav.klage.oppgave.clients.kabaldocument.model.response.DokumentEnhetFullfoerOutput
import no.nav.klage.oppgave.clients.kabaldocument.model.response.DokumentEnhetOutput
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
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

    fun createDokumentEnhetWithDokumentreferanser(
        input: DokumentEnhetWithDokumentreferanserInput
    ): DokumentEnhetOutput {
        return kabalDocumentWebClient.post()
            .uri { it.path("/dokumentenheter/meddokumentreferanser").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getAppAccessTokenWithKabalDocumentScope()}"
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

    fun fullfoerDokumentEnhet(
        dokumentEnhetId: UUID
    ): DokumentEnhetFullfoerOutput {
        return kabalDocumentWebClient.post()
            .uri { it.path("/dokumentenheter/{dokumentEnhetId}/fullfoer").build(dokumentEnhetId) }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getAppAccessTokenWithKabalDocumentScope()}"
            )
            .header("Nav-Call-Id", tracer.currentSpan().context().traceIdString())
            .retrieve()
            .bodyToMono<DokumentEnhetFullfoerOutput>()
            .block() ?: throw RuntimeException("DokumentEnhet could not be finalized")
    }
}