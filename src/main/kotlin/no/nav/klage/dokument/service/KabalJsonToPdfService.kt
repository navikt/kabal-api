package no.nav.klage.dokument.service

import no.nav.klage.dokument.clients.kabaljsontopdf.KabalJsonToPdfClient
import no.nav.klage.dokument.clients.kabaljsontopdf.domain.DocumentValidationResponse
import no.nav.klage.dokument.clients.kabaljsontopdf.domain.InnholdsfortegnelseRequest
import no.nav.klage.dokument.clients.kabaljsontopdf.domain.SvarbrevRequest
import no.nav.klage.dokument.domain.PDFDocument
import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.oppgave.api.view.kabin.SvarbrevInput
import no.nav.klage.oppgave.util.getLogger
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class KabalJsonToPdfService(
    private val kabalJsonToPdfClient: KabalJsonToPdfClient,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun getSvarbrevPDF(
        svarbrevInput: SvarbrevInput,
        mottattKlageinstans: LocalDate,
        fristInWeeks: Int,
        sakenGjelderIdentifikator: String,
        sakenGjelderName: String,
        ytelse: Ytelse,
        klagerIdentifikator: String,
        klagerName: String,
        avsenderEnhetId: String,
    ): ByteArray {
        val bytes = kabalJsonToPdfClient.getSvarbrevPDF(
            svarbrevRequest = SvarbrevRequest(
                title = svarbrevInput.title,
                sakenGjelder = SvarbrevRequest.Part(
                    name = sakenGjelderName,
                    fnr = sakenGjelderIdentifikator,
                ),
                klager = if (klagerIdentifikator != sakenGjelderIdentifikator) {
                    SvarbrevRequest.Part(
                        name = klagerName,
                        fnr = klagerIdentifikator,
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

    fun getPDFDocument(json: String): PDFDocument {
        return kabalJsonToPdfClient.getPDFDocument(json)
    }

    fun getInnholdsfortegnelse(innholdsfortegnelseRequest: InnholdsfortegnelseRequest): PDFDocument {
        return kabalJsonToPdfClient.getInnholdsfortegnelse(innholdsfortegnelseRequest)
    }

    fun validateJsonDocument(documentJson: String): DocumentValidationResponse {
        return kabalJsonToPdfClient.validateJsonDocument(documentJson)
    }
}