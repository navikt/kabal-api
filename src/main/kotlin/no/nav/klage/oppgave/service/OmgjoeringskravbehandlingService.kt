package no.nav.klage.oppgave.service

import no.nav.klage.dokument.api.view.JournalfoertDokumentReference
import no.nav.klage.oppgave.clients.kaka.KakaApiGateway
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.OmgjoeringskravbehandlingBasedOnJournalpost
import no.nav.klage.oppgave.domain.behandling.OmgjoeringskravbehandlingBasedOnKabalBehandling
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent.Change.Companion.createChange
import no.nav.klage.oppgave.domain.mottak.Mottak
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
    private val kakaApiGateway: KakaApiGateway,
    @Value("\${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun createOmgjoeringskravbehandlingFromMottak(mottak: Mottak): Behandling {
        val omgjoeringskravbehandling = if (mottak.isBasedOnJournalpost) {
            omgjoeringskravbehandlingRepository.save(
                OmgjoeringskravbehandlingBasedOnJournalpost(
                    klager = mottak.klager.copy(),
                    sakenGjelder = mottak.sakenGjelder?.copy() ?: mottak.klager.toSakenGjelder(),
                    prosessfullmektig = mottak.prosessfullmektig,
                    ytelse = mottak.ytelse,
                    type = mottak.type,
                    kildeReferanse = mottak.kildeReferanse,
                    dvhReferanse = mottak.dvhReferanse,
                    fagsystem = mottak.fagsystem,
                    fagsakId = mottak.fagsakId,
                    mottattKlageinstans = mottak.sakMottattKaDato,
                    tildeling = null,
                    frist = mottak.generateFrist(),
                    saksdokumenter = dokumentService.createSaksdokumenterFromJournalpostIdList(mottak.mottakDokument.map { it.journalpostId }),
                    kakaKvalitetsvurderingId = kakaApiGateway.createKvalitetsvurdering(kvalitetsvurderingVersion = 2).kvalitetsvurderingId,
                    kakaKvalitetsvurderingVersion = 2,
                    hjemler = mottak.hjemler,
                    previousSaksbehandlerident = mottak.forrigeSaksbehandlerident,
                    oppgaveId = null,
                    klageBehandlendeEnhet = mottak.forrigeBehandlendeEnhet,
                    gosysOppgaveId = mottak.gosysOppgaveId,
                    tilbakekreving = false,
                    varsletBehandlingstid = null,
                    forlengetBehandlingstidDraft = null,
                    gosysOppgaveRequired = mottak.gosysOppgaveRequired,
                    initiatingSystem = Behandling.InitiatingSystem.valueOf(mottak.sentFrom.name),
                    previousBehandlingId = mottak.forrigeBehandlingId,
                )
            )
        } else {
            omgjoeringskravbehandlingRepository.save(
                OmgjoeringskravbehandlingBasedOnKabalBehandling(
                    klager = mottak.klager.copy(),
                    sakenGjelder = mottak.sakenGjelder?.copy() ?: mottak.klager.toSakenGjelder(),
                    prosessfullmektig = mottak.prosessfullmektig,
                    ytelse = mottak.ytelse,
                    type = mottak.type,
                    kildeReferanse = mottak.kildeReferanse,
                    dvhReferanse = mottak.dvhReferanse,
                    fagsystem = mottak.fagsystem,
                    fagsakId = mottak.fagsakId,
                    mottattKlageinstans = mottak.sakMottattKaDato,
                    tildeling = null,
                    frist = mottak.generateFrist(),
                    saksdokumenter = dokumentService.createSaksdokumenterFromJournalpostIdList(mottak.mottakDokument.map { it.journalpostId }),
                    kakaKvalitetsvurderingId = kakaApiGateway.createKvalitetsvurdering(kvalitetsvurderingVersion = 2).kvalitetsvurderingId,
                    kakaKvalitetsvurderingVersion = 2,
                    hjemler = mottak.hjemler,
                    sourceBehandlingId = mottak.forrigeBehandlingId,
                    previousSaksbehandlerident = mottak.forrigeSaksbehandlerident,
                    oppgaveId = null,
                    klageBehandlendeEnhet = mottak.forrigeBehandlendeEnhet,
                    gosysOppgaveId = mottak.gosysOppgaveId,
                    tilbakekreving = false,
                    varsletBehandlingstid = null,
                    forlengetBehandlingstidDraft = null,
                    gosysOppgaveRequired = mottak.gosysOppgaveRequired,
                    initiatingSystem = Behandling.InitiatingSystem.valueOf(mottak.sentFrom.name),
                    previousBehandlingId = mottak.forrigeBehandlingId,
                )
            )
        }

        omgjoeringskravbehandling.addMottakDokument(mottakDokumentSet = mottak.mottakDokument)

        logger.debug("Created {} with id {}", omgjoeringskravbehandling::javaClass.name, omgjoeringskravbehandling.id)

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
            BehandlingChangedEvent(
                behandling = omgjoeringskravbehandling,
                changeList = listOfNotNull(
                    createChange(
                        saksbehandlerident = systembrukerIdent,
                        felt = BehandlingChangedEvent.Felt.OMGJOERINGSKRAVBEHANDLING_MOTTATT,
                        fraVerdi = null,
                        tilVerdi = "Opprettet",
                        behandlingId = omgjoeringskravbehandling.id,
                    )
                )
            )
        )

        applicationEventPublisher.publishEvent(
            BehandlingChangedEvent(
                behandling = omgjoeringskravbehandling,
                changeList = listOfNotNull(
                    createChange(
                        saksbehandlerident = systembrukerIdent,
                        felt = BehandlingChangedEvent.Felt.OMGJOERINGSKRAVBEHANDLING_OPPRETTET,
                        fraVerdi = null,
                        tilVerdi = "Opprettet",
                        behandlingId = omgjoeringskravbehandling.id,
                    )
                )
            )
        )

        omgjoeringskravbehandling.opprettetSendt = true

        return omgjoeringskravbehandling
    }

}