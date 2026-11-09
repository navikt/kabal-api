package no.nav.klage.oppgave.clients.kabaldocument

import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsHoveddokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsVedlegg
import no.nav.klage.dokument.domain.dokumenterunderarbeid.Innholdsfortegnelse
import no.nav.klage.oppgave.clients.kabaldocument.model.request.DokumentEnhetWithDokumentreferanserInput
import no.nav.klage.oppgave.clients.kabaldocument.model.response.DokumentEnhetFullfoerOutput
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.util.getLogger
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class KabalDocumentGateway(
    private val kabalDocumentClient: KabalDocumentClient,
    private val kabalDocumentMapper: KabalDocumentMapper,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    /** Reads lazy entity data, so it must run inside a transaction. */
    fun createKomplettDokumentEnhetInput(
        behandling: Behandling,
        hovedDokument: DokumentUnderArbeidAsHoveddokument,
        vedlegg: Set<DokumentUnderArbeidAsVedlegg>,
        innholdsfortegnelse: Innholdsfortegnelse?,
    ): DokumentEnhetWithDokumentreferanserInput =
        kabalDocumentMapper.mapBehandlingToDokumentEnhetWithDokumentreferanser(
            behandling = behandling,
            hovedDokument = hovedDokument,
            vedlegg = vedlegg,
            innholdsfortegnelse = innholdsfortegnelse,
        )

    /** Slow HTTP call (up to minutes). Do not call it inside a transaction. */
    fun createDokumentEnhet(input: DokumentEnhetWithDokumentreferanserInput): UUID =
        UUID.fromString(kabalDocumentClient.createDokumentEnhetWithDokumentreferanser(input).id)

    /** Slow HTTP call (up to minutes). Do not call it inside a transaction. */
    fun fullfoerDokumentEnhet(dokumentEnhetId: UUID): DokumentEnhetFullfoerOutput =
        kabalDocumentClient.fullfoerDokumentEnhet(dokumentEnhetId)

    fun feilregistrerSakstilknytningInDev(journalpostId: String) {
        kabalDocumentClient.feilregistrerSakstilknytningInDev(journalpostId = journalpostId)
    }
}
