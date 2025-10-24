package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.clients.kaka.KakaApiGateway
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.Klagebehandling
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent.Change.Companion.createChange
import no.nav.klage.oppgave.domain.mottak.Mottak
import no.nav.klage.oppgave.repositories.KlagebehandlingRepository
import no.nav.klage.oppgave.util.KakaVersionUtil
import no.nav.klage.oppgave.util.getLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class KlagebehandlingService(
    private val klagebehandlingRepository: KlagebehandlingRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val dokumentService: DokumentService,
    private val kakaApiGateway: KakaApiGateway,
    @Value("\${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
    private val kakaVersionUtil: KakaVersionUtil,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun createKlagebehandlingFromMottak(
        mottak: Mottak,
    ): Klagebehandling {
        val kvalitetsvurderingVersion = kakaVersionUtil.getKakaVersion()

        val klagebehandling = klagebehandlingRepository.save(
            Klagebehandling(
                klager = mottak.klager.copy(),
                sakenGjelder = mottak.sakenGjelder?.copy() ?: mottak.klager.toSakenGjelder(),
                prosessfullmektig = mottak.prosessfullmektig,
                ytelse = mottak.ytelse,
                type = mottak.type,
                kildeReferanse = mottak.kildeReferanse,
                dvhReferanse = mottak.dvhReferanse,
                fagsystem = mottak.fagsystem,
                fagsakId = mottak.fagsakId,
                mottattVedtaksinstans = mottak.brukersKlageMottattVedtaksinstans!!,
                avsenderEnhetFoersteinstans = mottak.forrigeBehandlendeEnhet,
                previousSaksbehandlerident = mottak.forrigeSaksbehandlerident,
                mottattKlageinstans = mottak.sakMottattKaDato,
                tildeling = null,
                frist = mottak.generateFrist(),
                saksdokumenter = dokumentService.createSaksdokumenterFromJournalpostIdList(mottak.mottakDokument.map { it.journalpostId }),
                kakaKvalitetsvurderingId = kakaApiGateway.createKvalitetsvurdering(kvalitetsvurderingVersion = kvalitetsvurderingVersion).kvalitetsvurderingId,
                kakaKvalitetsvurderingVersion = kvalitetsvurderingVersion,
                hjemler = mottak.hjemler,
                kommentarFraFoersteinstans = mottak.kommentar,
                gosysOppgaveId = mottak.gosysOppgaveId,
                tilbakekreving = false,
                varsletBehandlingstid = null,
                forlengetBehandlingstidDraft = null,
                gosysOppgaveRequired = mottak.gosysOppgaveRequired,
                initiatingSystem = Behandling.InitiatingSystem.valueOf(mottak.sentFrom.name),
                previousBehandlingId = mottak.forrigeBehandlingId,
            )
        )

        klagebehandling.addMottakDokument(mottakDokumentSet = mottak.mottakDokument)

        logger.debug("Created klagebehandling {}", klagebehandling.id)

        applicationEventPublisher.publishEvent(
            BehandlingChangedEvent(
                behandling = klagebehandling,
                changeList = listOfNotNull(
                    createChange(
                        saksbehandlerident = systembrukerIdent,
                        felt = BehandlingChangedEvent.Felt.KLAGEBEHANDLING_MOTTATT,
                        fraVerdi = null,
                        tilVerdi = "Opprettet",
                        behandlingId = klagebehandling.id,
                    )
                )
            )
        )
        applicationEventPublisher.publishEvent(
            BehandlingChangedEvent(
                behandling = klagebehandling,
                changeList = listOfNotNull(
                    createChange(
                        saksbehandlerident = systembrukerIdent,
                        felt = BehandlingChangedEvent.Felt.KLAGEBEHANDLING_OPPRETTET,
                        fraVerdi = null,
                        tilVerdi = "Opprettet",
                        behandlingId = klagebehandling.id,
                    )
                )
            )
        )

        klagebehandling.opprettetSendt = true

        return klagebehandling
    }
}