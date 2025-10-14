package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.clients.kaka.KakaApiGateway
import no.nav.klage.oppgave.domain.behandling.*
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent.Change.Companion.createChange
import no.nav.klage.oppgave.domain.mottak.Mottak
import no.nav.klage.oppgave.repositories.GjenopptaksbehandlingRepository
import no.nav.klage.oppgave.util.getLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.Period

@Service
class GjenopptaksbehandlingService(
    private val gjenopptaksbehandlingRepository: GjenopptaksbehandlingRepository,
    private val dokumentService: DokumentService,
    private val behandlingService: BehandlingService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val kakaApiGateway: KakaApiGateway,
    @Value("\${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun createGjenopptaksbehandlingFromMottak(mottak: Mottak): Behandling {
        val gjenopptaksbehandling = if (mottak.isBasedOnJournalpost) {
            gjenopptaksbehandlingRepository.save(
                GjenopptaksbehandlingBasedOnJournalpost(
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
            gjenopptaksbehandlingRepository.save(
                GjenopptaksbehandlingBasedOnKabalBehandling(
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

        gjenopptaksbehandling.addMottakDokument(mottakDokumentSet = mottak.mottakDokument)

        logger.debug("Created {} with id {}", gjenopptaksbehandling::javaClass.name, gjenopptaksbehandling.id)

        behandlingService.connectDocumentsFromPreviousBehandlingToBehandling(
            behandlingId = gjenopptaksbehandling.id,
            saksbehandlerIdent = systembrukerIdent,
            systemUserContext = true,
            ignoreCheckSkrivetilgang = true
        )

        applicationEventPublisher.publishEvent(
            BehandlingChangedEvent(
                behandling = gjenopptaksbehandling,
                changeList = listOfNotNull(
                    createChange(
                        saksbehandlerident = systembrukerIdent,
                        felt = BehandlingChangedEvent.Felt.BEGJAERING_OM_GJENOPPTAKSBEHANDLING_MOTTATT,
                        fraVerdi = null,
                        tilVerdi = "Opprettet",
                        behandlingId = gjenopptaksbehandling.id,
                    )
                )
            )
        )

        applicationEventPublisher.publishEvent(
            BehandlingChangedEvent(
                behandling = gjenopptaksbehandling,
                changeList = listOfNotNull(
                    createChange(
                        saksbehandlerident = systembrukerIdent,
                        felt = BehandlingChangedEvent.Felt.BEGJAERING_OM_GJENOPPTAKSBEHANDLING_OPPRETTET,
                        fraVerdi = null,
                        tilVerdi = "Opprettet",
                        behandlingId = gjenopptaksbehandling.id,
                    )
                )
            )
        )

        gjenopptaksbehandling.opprettetSendt = true

        return gjenopptaksbehandling
    }

    fun createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(gjenopptakITrygderettenbehandling: GjenopptakITrygderettenbehandling): Gjenopptaksbehandling {
        //Dette er en midlertidig versjon, inntil vi får en direkte lenke til forrige behandling.
        val originalGjenopptaksbehandling =
            gjenopptaksbehandlingRepository.findById(gjenopptakITrygderettenbehandling.previousBehandlingId!!).get()

        val gjenopptaksbehandling = if (originalGjenopptaksbehandling is GjenopptaksbehandlingBasedOnJournalpost) {
            gjenopptaksbehandlingRepository.save(
                GjenopptaksbehandlingBasedOnJournalpost(
                    klager = gjenopptakITrygderettenbehandling.klager.copy(),
                    sakenGjelder = gjenopptakITrygderettenbehandling.sakenGjelder.copy(),
                    prosessfullmektig = gjenopptakITrygderettenbehandling.prosessfullmektig,
                    ytelse = gjenopptakITrygderettenbehandling.ytelse,
                    type = Type.BEGJAERING_OM_GJENOPPTAK,
                    kildeReferanse = gjenopptakITrygderettenbehandling.kildeReferanse,
                    dvhReferanse = gjenopptakITrygderettenbehandling.dvhReferanse,
                    fagsystem = gjenopptakITrygderettenbehandling.fagsystem,
                    fagsakId = gjenopptakITrygderettenbehandling.fagsakId,
                    mottattKlageinstans = gjenopptakITrygderettenbehandling.mottattKlageinstans,
                    tildeling = gjenopptakITrygderettenbehandling.tildeling,
                    //Hva slags frist?
                    frist = LocalDate.now() + Period.ofWeeks(0),
                    kakaKvalitetsvurderingId = kakaApiGateway.createKvalitetsvurdering(kvalitetsvurderingVersion = 2).kvalitetsvurderingId,
                    kakaKvalitetsvurderingVersion = 2,
                    hjemler = gjenopptakITrygderettenbehandling.hjemler,
                    klageBehandlendeEnhet = gjenopptakITrygderettenbehandling.tildeling?.enhet!!,
                    previousSaksbehandlerident = gjenopptakITrygderettenbehandling.tildeling?.saksbehandlerident,
                    gosysOppgaveId = gjenopptakITrygderettenbehandling.gosysOppgaveId,
                    tilbakekreving = gjenopptakITrygderettenbehandling.tilbakekreving,
                    varsletBehandlingstid = null,
                    forlengetBehandlingstidDraft = null,
                    gosysOppgaveRequired = gjenopptakITrygderettenbehandling.gosysOppgaveRequired,
                    initiatingSystem = Behandling.InitiatingSystem.KABAL,
                    previousBehandlingId = gjenopptakITrygderettenbehandling.id,
                )
            )
        } else {
            gjenopptaksbehandlingRepository.save(
                GjenopptaksbehandlingBasedOnKabalBehandling(
                    klager = gjenopptakITrygderettenbehandling.klager.copy(),
                    sakenGjelder = gjenopptakITrygderettenbehandling.sakenGjelder.copy(),
                    prosessfullmektig = gjenopptakITrygderettenbehandling.prosessfullmektig,
                    ytelse = gjenopptakITrygderettenbehandling.ytelse,
                    type = Type.BEGJAERING_OM_GJENOPPTAK,
                    kildeReferanse = gjenopptakITrygderettenbehandling.kildeReferanse,
                    dvhReferanse = gjenopptakITrygderettenbehandling.dvhReferanse,
                    fagsystem = gjenopptakITrygderettenbehandling.fagsystem,
                    fagsakId = gjenopptakITrygderettenbehandling.fagsakId,
                    mottattKlageinstans = gjenopptakITrygderettenbehandling.mottattKlageinstans,
                    tildeling = gjenopptakITrygderettenbehandling.tildeling,
                    //Hva slags frist?
                    frist = LocalDate.now() + Period.ofWeeks(0),
                    kakaKvalitetsvurderingId = kakaApiGateway.createKvalitetsvurdering(kvalitetsvurderingVersion = 2).kvalitetsvurderingId,
                    kakaKvalitetsvurderingVersion = 2,
                    hjemler = gjenopptakITrygderettenbehandling.hjemler,
                    klageBehandlendeEnhet = gjenopptakITrygderettenbehandling.tildeling?.enhet!!,
                    previousSaksbehandlerident = gjenopptakITrygderettenbehandling.tildeling?.saksbehandlerident,
                    gosysOppgaveId = gjenopptakITrygderettenbehandling.gosysOppgaveId,
                    tilbakekreving = gjenopptakITrygderettenbehandling.tilbakekreving,
                    varsletBehandlingstid = null,
                    forlengetBehandlingstidDraft = null,
                    gosysOppgaveRequired = gjenopptakITrygderettenbehandling.gosysOppgaveRequired,
                    sourceBehandlingId = gjenopptakITrygderettenbehandling.id,
                    initiatingSystem = Behandling.InitiatingSystem.KABAL,
                    previousBehandlingId = gjenopptakITrygderettenbehandling.id,
                )
            )
        }

        logger.debug(
            "Created gjenopptaksbehandling {} from gjenopptakITrygderettenbehandling {}",
            gjenopptaksbehandling.id,
            gjenopptakITrygderettenbehandling.id
        )

        behandlingService.connectDocumentsFromPreviousBehandlingToBehandling(
            behandlingId = gjenopptaksbehandling.id,
            saksbehandlerIdent = systembrukerIdent,
            systemUserContext = true,
            ignoreCheckSkrivetilgang = true
        )

        applicationEventPublisher.publishEvent(
            BehandlingChangedEvent(
                behandling = gjenopptaksbehandling,
                changeList = listOfNotNull(
                    createChange(
                        saksbehandlerident = gjenopptakITrygderettenbehandling.tildeling!!.saksbehandlerident,
                        felt = BehandlingChangedEvent.Felt.BEGJAERING_OM_GJENOPPTAKSBEHANDLING_OPPRETTET_BASERT_PAA_BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN,
                        fraVerdi = null,
                        tilVerdi = "Opprettet",
                        behandlingId = gjenopptaksbehandling.id,
                    )
                )
            )
        )

        //TODO: Undersøk om vi skal sende noen infomelding om at dette har skjedd

        return gjenopptaksbehandling
    }
}