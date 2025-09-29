package no.nav.klage.oppgave.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import no.nav.klage.dokument.api.view.JournalfoertDokumentReference
import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.clients.kaka.KakaApiGateway
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandling
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.BehandlingEtterTrygderettenOpphevet
import no.nav.klage.oppgave.domain.behandling.GjenopptakITrygderettenbehandling
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent.Change.Companion.createChange
import no.nav.klage.oppgave.repositories.BehandlingEtterTrygderettenOpphevetRepository
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
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val objectMapperBehandlingEvents = ObjectMapper().registerModule(JavaTimeModule()).configure(
            SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false
        )
    }

    fun createBehandlingEtterTrygderettenOpphevet(behandling: Behandling): BehandlingEtterTrygderettenOpphevet {
        val behandlingEtterTrygderettenOpphevet = if (behandling is AnkeITrygderettenbehandling) {
            behandlingEtterTrygderettenOpphevetRepository.save(
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
                    kakaKvalitetsvurderingId = kakaApiGateway.createKvalitetsvurdering(kvalitetsvurderingVersion = 2).kvalitetsvurderingId,
                    kakaKvalitetsvurderingVersion = 2,
                    hjemler = behandling.hjemler,
                    sourceBehandlingId = behandling.id,
                    previousSaksbehandlerident = behandling.tildeling?.saksbehandlerident,
                    gosysOppgaveId = behandling.gosysOppgaveId,
                    kjennelseMottatt = behandling.kjennelseMottatt!!,
                    ankeBehandlendeEnhet = behandling.tildeling?.enhet!!,
                    tilbakekreving = behandling.tilbakekreving,
                    varsletBehandlingstid = null,
                    forlengetBehandlingstidDraft = null,
                    gosysOppgaveRequired = behandling.gosysOppgaveRequired,
                )
            )
        } else if (behandling is GjenopptakITrygderettenbehandling) {
            behandlingEtterTrygderettenOpphevetRepository.save(
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
                    kakaKvalitetsvurderingId = kakaApiGateway.createKvalitetsvurdering(kvalitetsvurderingVersion = 2).kvalitetsvurderingId,
                    kakaKvalitetsvurderingVersion = 2,
                    hjemler = behandling.hjemler,
                    sourceBehandlingId = behandling.id,
                    previousSaksbehandlerident = behandling.tildeling?.saksbehandlerident,
                    gosysOppgaveId = behandling.gosysOppgaveId,
                    kjennelseMottatt = behandling.kjennelseMottatt!!,
                    ankeBehandlendeEnhet = behandling.tildeling?.enhet!!,
                    tilbakekreving = behandling.tilbakekreving,
                    varsletBehandlingstid = null,
                    forlengetBehandlingstidDraft = null,
                    gosysOppgaveRequired = behandling.gosysOppgaveRequired,
                )
            )
        } else {
            throw Exception("Wrong input class for behandling ${behandling.id}")
        }

        logger.debug(
            "Created BehandlingEtterTrygderettenOpphevet {} from ankeITrygderettenbehandling {}",
            behandlingEtterTrygderettenOpphevet.id,
            behandling.id
        )

        behandlingService.connectDocumentsToBehandling(
            behandlingId = behandlingEtterTrygderettenOpphevet.id,
            journalfoertDokumentReferenceSet = behandling.saksdokumenter.map {
                JournalfoertDokumentReference(
                    journalpostId = it.journalpostId,
                    dokumentInfoId = it.dokumentInfoId
                )
            }.toSet(),
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