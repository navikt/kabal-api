package no.nav.klage.oppgave.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import jakarta.transaction.Transactional
import no.nav.klage.dokument.api.view.JournalfoertDokumentReference
import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.clients.kaka.KakaApiGateway
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.domain.klage.AnkeITrygderettenbehandling
import no.nav.klage.oppgave.domain.klage.BehandlingEtterTrygderettenOpphevet
import no.nav.klage.oppgave.domain.klage.Endringslogginnslag
import no.nav.klage.oppgave.domain.klage.Felt
import no.nav.klage.oppgave.repositories.BehandlingEtterTrygderettenOpphevetRepository
import no.nav.klage.oppgave.util.getLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.Period

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

    fun getCompletedBehandlingEtterTrygderettenOpphevetServiceByPartIdValue(
        partIdValue: String
    ): List<BehandlingEtterTrygderettenOpphevet> {
        return behandlingEtterTrygderettenOpphevetRepository.getCompletedBehandlinger(partIdValue = partIdValue)
    }

    fun createBehandlingEtterTrygderettenOpphevet(ankeITrygderettenbehandling: AnkeITrygderettenbehandling): BehandlingEtterTrygderettenOpphevet {
        val behandlingEtterTrygderettenOpphevet = behandlingEtterTrygderettenOpphevetRepository.save(
            BehandlingEtterTrygderettenOpphevet(
                klager = ankeITrygderettenbehandling.klager.copy(),
                sakenGjelder = ankeITrygderettenbehandling.sakenGjelder.copy(),
                ytelse = ankeITrygderettenbehandling.ytelse,
                type = Type.BEHANDLING_ETTER_TRYGDERETTEN_OPPHEVET,
                kildeReferanse = ankeITrygderettenbehandling.kildeReferanse,
                dvhReferanse = ankeITrygderettenbehandling.dvhReferanse,
                fagsystem = ankeITrygderettenbehandling.fagsystem,
                fagsakId = ankeITrygderettenbehandling.fagsakId,
                mottattKlageinstans = ankeITrygderettenbehandling.mottattKlageinstans,
                tildeling = null,
                frist = LocalDate.now() + Period.ofWeeks(12),
                kakaKvalitetsvurderingId = kakaApiGateway.createKvalitetsvurdering(kvalitetsvurderingVersion = 2).kvalitetsvurderingId,
                kakaKvalitetsvurderingVersion = 2,
                hjemler = ankeITrygderettenbehandling.hjemler,
                sourceBehandlingId = ankeITrygderettenbehandling.id,
                previousSaksbehandlerident = ankeITrygderettenbehandling.tildeling?.saksbehandlerident,
                oppgaveId = ankeITrygderettenbehandling.oppgaveId,
                kjennelseMottatt = ankeITrygderettenbehandling.kjennelseMottatt!!,
            )
        )
        logger.debug(
            "Created BehandlingEtterTrygderettenOpphevet {} from ankeITrygderettenbehandling {}",
            behandlingEtterTrygderettenOpphevet.id,
            ankeITrygderettenbehandling.id
        )

        behandlingService.connectDocumentsToBehandling(
            behandlingId = behandlingEtterTrygderettenOpphevet.id,
            journalfoertDokumentReferenceSet = ankeITrygderettenbehandling.saksdokumenter.map {
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
            BehandlingEndretEvent(
                behandling = behandlingEtterTrygderettenOpphevet,
                endringslogginnslag = listOfNotNull(
                    Endringslogginnslag.endringslogg(
                        saksbehandlerident = ankeITrygderettenbehandling.tildeling!!.saksbehandlerident,
                        felt = Felt.BEHANDLING_ETTER_TR_OPPHEVET_OPPRETTET,
                        fraVerdi = null,
                        tilVerdi = "Opprettet",
                        behandlingId = behandlingEtterTrygderettenOpphevet.id,
                        tidspunkt = behandlingEtterTrygderettenOpphevet.created,
                    )
                )
            )
        )

        //TODO: Undersøk om vi skal sende noen infomelding om at dette har skjedd

        return behandlingEtterTrygderettenOpphevet
    }
}