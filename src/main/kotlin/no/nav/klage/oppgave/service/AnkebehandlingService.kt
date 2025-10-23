package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.clients.kaka.KakaApiGateway
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandling
import no.nav.klage.oppgave.domain.behandling.Ankebehandling
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent.Change.Companion.createChange
import no.nav.klage.oppgave.domain.mottak.Mottak
import no.nav.klage.oppgave.repositories.AnkebehandlingRepository
import no.nav.klage.oppgave.util.KakaVersionUtil
import no.nav.klage.oppgave.util.getLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.Period
import java.util.*

@Service
@Transactional
class AnkebehandlingService(
    private val ankebehandlingRepository: AnkebehandlingRepository,
    private val kakaApiGateway: KakaApiGateway,
    private val dokumentService: DokumentService,
    private val behandlingService: BehandlingService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    @Value("\${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
    private val kakaVersionUtil: KakaVersionUtil,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun getAnkebehandlingerBasedOnSourceBehandlingId(sourceBehandlingId: UUID): List<Ankebehandling> {
        return ankebehandlingRepository.findBySourceBehandlingIdAndFeilregistreringIsNull(sourceBehandlingId = sourceBehandlingId)
    }

    fun createAnkebehandlingFromMottak(
        mottak: Mottak,
    ): Ankebehandling {
        val kvalitetsvurderingVersion = kakaVersionUtil.getKakaVersion()

        val ankebehandling = ankebehandlingRepository.save(
            Ankebehandling(
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
                kakaKvalitetsvurderingId = kakaApiGateway.createKvalitetsvurdering(kvalitetsvurderingVersion = kvalitetsvurderingVersion).kvalitetsvurderingId,
                kakaKvalitetsvurderingVersion = kvalitetsvurderingVersion,
                hjemler = mottak.hjemler,
                klageBehandlendeEnhet = mottak.forrigeBehandlendeEnhet,
                sourceBehandlingId = mottak.forrigeBehandlingId,
                previousSaksbehandlerident = mottak.forrigeSaksbehandlerident,
                gosysOppgaveId = mottak.gosysOppgaveId,
                tilbakekreving = false,
                varsletBehandlingstid = null,
                forlengetBehandlingstidDraft = null,
                gosysOppgaveRequired = mottak.gosysOppgaveRequired,
                initiatingSystem = Behandling.InitiatingSystem.valueOf(mottak.sentFrom.name),
                previousBehandlingId = mottak.forrigeBehandlingId,
            )
        )

        ankebehandling.addMottakDokument(mottakDokumentSet = mottak.mottakDokument)

        logger.debug("Created ankebehandling {}", ankebehandling.id)

        behandlingService.connectDocumentsFromPreviousBehandlingToBehandling(
            behandlingId = ankebehandling.id,
            saksbehandlerIdent = systembrukerIdent,
            systemUserContext = true,
            ignoreCheckSkrivetilgang = true
        )

        applicationEventPublisher.publishEvent(
            BehandlingChangedEvent(
                behandling = ankebehandling,
                changeList = listOfNotNull(
                    createChange(
                        saksbehandlerident = systembrukerIdent,
                        felt = BehandlingChangedEvent.Felt.ANKEBEHANDLING_MOTTATT,
                        fraVerdi = null,
                        tilVerdi = "Opprettet",
                        behandlingId = ankebehandling.id,
                    )
                )
            )
        )

        applicationEventPublisher.publishEvent(
            BehandlingChangedEvent(
                behandling = ankebehandling,
                changeList = listOfNotNull(
                    createChange(
                        saksbehandlerident = systembrukerIdent,
                        felt = BehandlingChangedEvent.Felt.ANKEBEHANDLING_OPPRETTET,
                        fraVerdi = null,
                        tilVerdi = "Opprettet",
                        behandlingId = ankebehandling.id,
                    )
                )
            )
        )

        ankebehandling.opprettetSendt = true

        return ankebehandling
    }

    fun createAnkebehandlingFromAnkeITrygderettenbehandling(ankeITrygderettenbehandling: AnkeITrygderettenbehandling): Ankebehandling {
        val ankebehandling = ankebehandlingRepository.save(
            Ankebehandling(
                previousBehandlingId = ankeITrygderettenbehandling.id,
                klager = ankeITrygderettenbehandling.klager.copy(),
                sakenGjelder = ankeITrygderettenbehandling.sakenGjelder.copy(),
                prosessfullmektig = ankeITrygderettenbehandling.prosessfullmektig,
                ytelse = ankeITrygderettenbehandling.ytelse,
                type = Type.ANKE,
                kildeReferanse = ankeITrygderettenbehandling.kildeReferanse,
                dvhReferanse = ankeITrygderettenbehandling.dvhReferanse,
                fagsystem = ankeITrygderettenbehandling.fagsystem,
                fagsakId = ankeITrygderettenbehandling.fagsakId,
                mottattKlageinstans = ankeITrygderettenbehandling.mottattKlageinstans,
                tildeling = ankeITrygderettenbehandling.tildeling,
                frist = LocalDate.now() + Period.ofWeeks(0),
                kakaKvalitetsvurderingId = kakaApiGateway.createKvalitetsvurdering(kvalitetsvurderingVersion = 2).kvalitetsvurderingId,
                kakaKvalitetsvurderingVersion = 2,
                hjemler = ankeITrygderettenbehandling.hjemler,
                klageBehandlendeEnhet = ankeITrygderettenbehandling.tildeling?.enhet!!,
                sourceBehandlingId = ankeITrygderettenbehandling.id,
                previousSaksbehandlerident = ankeITrygderettenbehandling.tildeling?.saksbehandlerident,
                gosysOppgaveId = ankeITrygderettenbehandling.gosysOppgaveId,
                tilbakekreving = ankeITrygderettenbehandling.tilbakekreving,
                varsletBehandlingstid = null,
                forlengetBehandlingstidDraft = null,
                gosysOppgaveRequired = ankeITrygderettenbehandling.gosysOppgaveRequired,
                initiatingSystem = Behandling.InitiatingSystem.KABAL,
            )
        )
        logger.debug(
            "Created ankebehandling {} from ankeITrygderettenbehandling {}",
            ankebehandling.id,
            ankeITrygderettenbehandling.id
        )

        behandlingService.connectDocumentsFromPreviousBehandlingToBehandling(
            behandlingId = ankebehandling.id,
            saksbehandlerIdent = systembrukerIdent,
            systemUserContext = true,
            ignoreCheckSkrivetilgang = true
        )

        applicationEventPublisher.publishEvent(
            BehandlingChangedEvent(
                behandling = ankebehandling,
                changeList = listOfNotNull(
                    createChange(
                        saksbehandlerident = ankeITrygderettenbehandling.tildeling!!.saksbehandlerident,
                        felt = BehandlingChangedEvent.Felt.ANKEBEHANDLING_OPPRETTET_BASERT_PAA_ANKE_I_TRYGDERETTEN,
                        fraVerdi = null,
                        tilVerdi = "Opprettet",
                        behandlingId = ankebehandling.id,
                    )
                )
            )
        )

        //TODO: Undersøk om vi skal sende noen infomelding om at dette har skjedd

        return ankebehandling
    }
}