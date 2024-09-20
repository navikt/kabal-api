package no.nav.klage.oppgave.service

import no.nav.klage.dokument.api.view.JournalfoertDokumentReference
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.domain.klage.Behandling
import no.nav.klage.oppgave.domain.klage.Mottak
import no.nav.klage.oppgave.domain.klage.Omgjoeringskravbehandling
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.repositories.OmgjoeringskravbehandlingRepository
import no.nav.klage.oppgave.util.getLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class OmgjoeringskravbehandlingService(
    private val omgjoeringskravbehandlingRepository: OmgjoeringskravbehandlingRepository,
    private val dokumentService: DokumentService,
    private val behandlingService: BehandlingService,
    private val behandlingRepository: BehandlingRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
    @Value("\${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun createOmgjoeringskravbehandlingFromMottak(mottak: Mottak): Behandling {
        val omgjoeringskravbehandling = omgjoeringskravbehandlingRepository.save(
            Omgjoeringskravbehandling(
                klager = mottak.klager.copy(),
                sakenGjelder = mottak.sakenGjelder?.copy() ?: mottak.klager.toSakenGjelder(),
                ytelse = mottak.ytelse,
                type = mottak.type,
                kildeReferanse = mottak.kildeReferanse,
                dvhReferanse = mottak.dvhReferanse,
                fagsystem = mottak.fagsystem,
                fagsakId = mottak.fagsakId,
                innsendt = mottak.innsendtDato,
                mottattKlageinstans = mottak.sakMottattKaDato,
                tildeling = null,
                frist = mottak.generateFrist(),
                mottakId = mottak.id,
                saksdokumenter = dokumentService.createSaksdokumenterFromJournalpostIdList(mottak.mottakDokument.map { it.journalpostId }),
                kakaKvalitetsvurderingId = null,
                kakaKvalitetsvurderingVersion = 2,
                hjemler = mottak.mapToBehandlingHjemler(),
                sourceBehandlingId = mottak.forrigeBehandlingId,
                previousSaksbehandlerident = mottak.forrigeSaksbehandlerident,
                oppgaveId = null,
                klageBehandlendeEnhet = mottak.forrigeBehandlendeEnhet,
            )
        )
        logger.debug("Created omgjoeringskravbehandling {} for mottak {}", omgjoeringskravbehandling.id, mottak.id)

        if (mottak.forrigeBehandlingId != null) {
            val behandling = behandlingRepository.findById(mottak.forrigeBehandlingId).get()
            val dokumenter = behandling.saksdokumenter

            logger.debug(
                "Adding saksdokumenter from behandling {} to omgjoeringskravbehandling {}",
                mottak.forrigeBehandlingId,
                omgjoeringskravbehandling.id
            )

            behandlingService.connectDocumentsToBehandling(
                behandlingId = omgjoeringskravbehandling.id,
                journalfoertDokumentReferenceSet = dokumenter.map {
                    JournalfoertDokumentReference(
                        journalpostId = it.journalpostId,
                        dokumentInfoId = it.dokumentInfoId
                    )
                }.toSet(),
                saksbehandlerIdent = systembrukerIdent,
                systemUserContext = true,
                ignoreCheckSkrivetilgang = true
            )
        }

        applicationEventPublisher.publishEvent(
            BehandlingEndretEvent(
                behandling = omgjoeringskravbehandling,
                endringslogginnslag = emptyList()
            )
        )
        return omgjoeringskravbehandling
    }

}