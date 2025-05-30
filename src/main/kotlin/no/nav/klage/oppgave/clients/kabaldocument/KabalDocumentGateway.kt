package no.nav.klage.oppgave.clients.kabaldocument

import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsHoveddokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsVedlegg
import no.nav.klage.dokument.domain.dokumenterunderarbeid.Innholdsfortegnelse
import no.nav.klage.oppgave.clients.kabaldocument.model.response.DokumentEnhetFullfoerOutput
import no.nav.klage.oppgave.domain.klage.Behandling
import no.nav.klage.oppgave.util.getLogger
import org.springframework.stereotype.Service
import java.util.*

@Service
class KabalDocumentGateway(
    private val kabalDocumentClient: KabalDocumentClient,
    private val kabalDocumentMapper: KabalDocumentMapper
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun createKomplettDokumentEnhet(
        behandling: Behandling,
        hovedDokument: DokumentUnderArbeidAsHoveddokument,
        vedlegg: Set<DokumentUnderArbeidAsVedlegg>,
        innholdsfortegnelse: Innholdsfortegnelse?,
    ): UUID {
        return UUID.fromString(
            kabalDocumentClient.createDokumentEnhetWithDokumentreferanser(
                kabalDocumentMapper.mapBehandlingToDokumentEnhetWithDokumentreferanser(
                    behandling = behandling,
                    hovedDokument = hovedDokument,
                    vedlegg = vedlegg,
                    innholdsfortegnelse = innholdsfortegnelse,
                )
            ).id
        )
    }

    fun fullfoerDokumentEnhet(dokumentEnhetId: UUID): DokumentEnhetFullfoerOutput =
        kabalDocumentClient.fullfoerDokumentEnhet(dokumentEnhetId)
}