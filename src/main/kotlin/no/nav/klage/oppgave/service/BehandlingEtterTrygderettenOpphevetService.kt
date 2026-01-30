package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.clients.kaka.KakaApiGateway
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.BehandlingEtterTrygderettenOpphevet
import no.nav.klage.oppgave.domain.behandling.BehandlingITrygderetten
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent.Change.Companion.createChange
import no.nav.klage.oppgave.repositories.BehandlingEtterTrygderettenOpphevetRepository
import no.nav.klage.oppgave.util.KakaVersionUtil
import no.nav.klage.oppgave.util.getLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
@Transactional
class BehandlingEtterTrygderettenOpphevetService(
    private val behandlingEtterTrygderettenOpphevetRepository: BehandlingEtterTrygderettenOpphevetRepository,
    private val behandlingService: BehandlingService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val kakaApiGateway: KakaApiGateway,
    @Value("\${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
    private val kakaVersionUtil: KakaVersionUtil,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun createBehandlingEtterTrygderettenOpphevet(behandling: BehandlingITrygderetten): BehandlingEtterTrygderettenOpphevet {
        behandling as Behandling

        val kvalitetsvurderingVersion = kakaVersionUtil.getKakaVersion()

        val behandlingEtterTrygderettenOpphevet = behandlingEtterTrygderettenOpphevetRepository.save(
                BehandlingEtterTrygderettenOpphevet(
                    klager = behandling.klager.copy(),
                    sakenGjelder = behandling.sakenGjelder.copy(),
                    prosessfullmektig = behandling.prosessfullmektig?.copy(),
                    ytelse = behandling.ytelse,
                    type = Type.BEHANDLING_ETTER_TRYGDERETTEN_OPPHEVET,
                    kildeReferanse = behandling.kildeReferanse,
                    dvhReferanse = behandling.dvhReferanse,
                    fagsystem = behandling.fagsystem,
                    fagsakId = behandling.fagsakId,
                    mottattKlageinstans = behandling.kjennelseMottatt!!,
                    tildeling = behandling.tildeling?.copy(tidspunkt = LocalDateTime.now()),
                    frist = LocalDate.now(),
                    kakaKvalitetsvurderingId = kakaApiGateway.createKvalitetsvurdering(kvalitetsvurderingVersion = kvalitetsvurderingVersion).kvalitetsvurderingId,
                    kakaKvalitetsvurderingVersion = kvalitetsvurderingVersion,
                    hjemler = behandling.hjemler,
                    previousSaksbehandlerident = behandling.tildeling?.saksbehandlerident,
                    gosysOppgaveId = behandling.gosysOppgaveId,
                    kjennelseMottatt = behandling.kjennelseMottatt!!,
                    ankeBehandlendeEnhet = behandling.tildeling?.enhet!!,
                    tilbakekreving = behandling.tilbakekreving,
                    varsletBehandlingstid = null,
                    forlengetBehandlingstidDraft = null,
                    gosysOppgaveRequired = behandling.gosysOppgaveRequired,
                    initiatingSystem = Behandling.InitiatingSystem.KABAL,
                    previousBehandlingId = behandling.id,
                )
            )

        logger.debug(
            "Created BehandlingEtterTrygderettenOpphevet {} from behandlingITrygderetten {}",
            behandlingEtterTrygderettenOpphevet.id,
            behandling.id
        )

        behandlingService.connectDocumentsFromPreviousBehandlingToBehandling(
            behandlingId = behandlingEtterTrygderettenOpphevet.id,
            saksbehandlerIdent = systembrukerIdent,
            systemUserContext = true,
            ignoreCheckSkrivetilgang = true
        )

        applicationEventPublisher.publishEvent(
            BehandlingChangedEvent(
                behandling = behandlingEtterTrygderettenOpphevet,
                changeList = listOfNotNull(
                    createChange(
                        saksbehandlerident = behandling.tildeling!!.saksbehandlerident,
                        felt = BehandlingChangedEvent.Felt.BEHANDLING_ETTER_TR_OPPHEVET_OPPRETTET,
                        fraVerdi = null,
                        tilVerdi = "Opprettet",
                        behandlingId = behandlingEtterTrygderettenOpphevet.id,
                    )
                )
            )
        )

        //TODO: Undersøk om vi skal sende noen infomelding om at dette har skjedd

        return behandlingEtterTrygderettenOpphevet
    }
}