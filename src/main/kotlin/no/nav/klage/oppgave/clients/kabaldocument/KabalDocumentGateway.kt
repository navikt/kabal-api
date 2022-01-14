package no.nav.klage.oppgave.clients.kabaldocument

import no.nav.klage.oppgave.clients.kabaldocument.model.Rolle
import no.nav.klage.oppgave.clients.kabaldocument.model.request.FilInput
import no.nav.klage.oppgave.clients.kabaldocument.model.response.JournalpostId
import no.nav.klage.oppgave.domain.DokumentInnhold
import no.nav.klage.oppgave.domain.DokumentInnholdOgTittel
import no.nav.klage.oppgave.domain.DokumentMetadata
import no.nav.klage.oppgave.domain.klage.Klagebehandling
import no.nav.klage.oppgave.exceptions.JournalpostNotFoundException
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime
import java.util.*

@Service
class KabalDocumentGateway(
    private val kabalDocumentClient: KabalDocumentClient,
    private val kabalDocumentMapper: KabalDocumentMapper
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    fun createDokumentEnhet(
        klagebehandling: Klagebehandling
    ): UUID {
        return UUID.fromString(
            kabalDocumentClient.createDokumentEnhet(
                kabalDocumentMapper.mapKlagebehandlingToDokumentEnhetInput(klagebehandling)
            ).id
        )
    }

    fun uploadHovedDokument(
        dokumentEnhetId: UUID,
        multipartFile: MultipartFile
    ): LocalDateTime {
        val response = kabalDocumentClient.uploadHovedDokument(dokumentEnhetId, FilInput(file = multipartFile))
        return response.fileMetadata!!.opplastet
    }

    fun getHovedDokument(dokumentEnhetId: UUID): DokumentInnhold {
        val response = kabalDocumentClient.getHovedDokument(dokumentEnhetId)
        return DokumentInnhold(content = response.first, contentType = response.second)
    }

    fun getMetadataOmHovedDokument(dokumentEnhetId: UUID): DokumentMetadata? {
        val dokumentEnhet = kabalDocumentClient.getDokumentEnhet(dokumentEnhetId)

        return dokumentEnhet.hovedDokument?.let {
            DokumentMetadata(
                title = it.name,
                size = it.size,
                opplastet = it.opplastet
            )
        }
    }

    fun getHovedDokumentOgMetadata(dokumentEnhetId: UUID): DokumentInnholdOgTittel {
        val dokumentMetadata = getMetadataOmHovedDokument(dokumentEnhetId)
            ?: throw JournalpostNotFoundException("Hoveddokument er ikke lastet opp")
        val dokumentInnhold = getHovedDokument(dokumentEnhetId)
        return DokumentInnholdOgTittel(
            title = dokumentMetadata.title, content = dokumentInnhold.content, contentType = dokumentInnhold.contentType
        )
    }

    fun deleteHovedDokument(dokumentEnhetId: UUID): LocalDateTime =
        kabalDocumentClient.deleteHovedDokument(dokumentEnhetId).modified

    fun fullfoerDokumentEnhet(dokumentEnhetId: UUID): JournalpostId =
        kabalDocumentClient.fullfoerDokumentEnhet(dokumentEnhetId).brevMottakerWithJoarkAndDokDistInfoList.first {
            it.rolle == Rolle.HOVEDADRESSAT
        }.journalpostId

    fun isHovedDokumentUploaded(dokumentEnhetId: UUID): Boolean {
        return getMetadataOmHovedDokument(dokumentEnhetId) != null
    }

}