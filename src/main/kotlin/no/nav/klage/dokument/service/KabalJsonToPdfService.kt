package no.nav.klage.dokument.service

import no.nav.klage.dokument.clients.kabaljsontopdf.KabalJsonToPdfClient
import no.nav.klage.dokument.clients.kabaljsontopdf.domain.DocumentValidationResponse
import no.nav.klage.dokument.clients.kabaljsontopdf.domain.InnholdsfortegnelseRequest
import no.nav.klage.dokument.clients.kabaljsontopdf.domain.SvarbrevRequest
import no.nav.klage.dokument.domain.PDFDocument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.Svarbrev
import no.nav.klage.kodeverk.Ytelse
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
        svarbrev: Svarbrev,
        mottattKlageinstans: LocalDate,
        sakenGjelderIdentifikator: String,
        sakenGjelderName: String,
        ytelse: Ytelse,
        klagerIdentifikator: String,
        klagerName: String,
        avsenderEnhetId: String,
    ): ByteArray {
        val bytes = kabalJsonToPdfClient.getSvarbrevPDF(
            svarbrevRequest = SvarbrevRequest(
                title = svarbrev.title,
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
                fullmektigFritekst = svarbrev.fullmektigFritekst,
                receivedDate = mottattKlageinstans,
                behandlingstidInWeeks = svarbrev.varsletBehandlingstidWeeks,
                avsenderEnhetId = avsenderEnhetId,
                type = SvarbrevRequest.Type.valueOf(
                    svarbrev.type.navn.uppercase()
                ),
                customText = svarbrev.customText,
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