package no.nav.klage.dokument.service

import no.nav.klage.dokument.clients.kabaljsontopdf.KabalJsonToPdfClient
import no.nav.klage.dokument.clients.kabaljsontopdf.domain.SvarbrevRequest
import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.oppgave.api.view.kabin.SvarbrevInput
import no.nav.klage.oppgave.domain.klage.PartId
import no.nav.klage.oppgave.service.PartSearchService
import no.nav.klage.oppgave.util.getLogger
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class KabalJsonToPdfService(
    private val kabalJsonToPdfClient: KabalJsonToPdfClient,
    private val partSearchService: PartSearchService
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun getSvarbrevPDF(
        svarbrevInput: SvarbrevInput,
        mottattKlageinstans: LocalDate,
        fristInWeeks: Int,
        sakenGjelder: PartId,
        ytelse: Ytelse,
        klager: PartId,
        avsenderEnhetId: String,
    ): ByteArray {
        val bytes = kabalJsonToPdfClient.getSvarbrevPDF(
            svarbrevRequest = SvarbrevRequest(
                title = svarbrevInput.title,
                sakenGjelder = SvarbrevRequest.Part(
                    name = partSearchService.searchPart(identifikator = sakenGjelder.value).name,
                    fnr = sakenGjelder.value,
                ),
                klager = if (klager.value != sakenGjelder.value) {
                    SvarbrevRequest.Part(
                        name = partSearchService.searchPart(identifikator = klager.value).name,
                        fnr = klager.value,
                    )
                } else null,
                ytelsenavn = ytelse.navn,
                fullmektigFritekst = svarbrevInput.fullmektigFritekst,
                receivedDate = mottattKlageinstans,
                behandlingstidInWeeks = svarbrevInput.varsletBehandlingstidWeeks,
                avsenderEnhetId = avsenderEnhetId,
                type = if (svarbrevInput.type == null) {
                    SvarbrevRequest.Type.ANKE
                } else {
                    SvarbrevRequest.Type.valueOf(
                        svarbrevInput.type.navn.uppercase()
                    )
                },
                customText = svarbrevInput.customText,
            )
        )
        return bytes
    }
}