package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.clients.kaka.KakaApiGateway
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingEtter2027
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.AnkebehandlingEtter2027
import no.nav.klage.oppgave.domain.behandling.AnkebehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent.Change.Companion.createChange
import no.nav.klage.oppgave.domain.mottak.Mottak
import no.nav.klage.oppgave.repositories.AnkebehandlingEtter2027Repository
import no.nav.klage.oppgave.repositories.AnkebehandlingFoer2027Repository
import no.nav.klage.oppgave.util.KakaVersionUtil
import no.nav.klage.oppgave.util.getLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.Period

@Service
@Transactional
class AnkebehandlingService(
    private val ankebehandlingFoer2027Repository: AnkebehandlingFoer2027Repository,
    private val ankebehandlingEtter2027Repository: AnkebehandlingEtter2027Repository,
    private val kakaApiGateway: KakaApiGateway,
    private val dokumentService: DokumentService,
    private val behandlingService: BehandlingService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    @Value($$"${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
    private val kakaVersionUtil: KakaVersionUtil,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun createAnkebehandlingFromMottak(mottak: Mottak): AnkebehandlingFoer2027 {
        val kvalitetsvurderingVersion = kakaVersionUtil.getKakaVersion()

        val ankebehandling =
            ankebehandlingFoer2027Repository.save(
                AnkebehandlingFoer2027(
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
                    saksdokumenter =
                        dokumentService.createSaksdokumenterFromJournalpostIdList(
                            mottak.mottakDokument.map { it.journalpostId },
                        ),
                    kakaKvalitetsvurderingId =
                        kakaApiGateway
                            .createKvalitetsvurdering(
                                kvalitetsvurderingVersion = kvalitetsvurderingVersion,
                            ).kvalitetsvurderingId,
                    kakaKvalitetsvurderingVersion = kvalitetsvurderingVersion,
                    hjemler = mottak.hjemler,
                    klageBehandlendeEnhet = mottak.forrigeBehandlendeEnhet,
                    paaanketVedtaksdato = behandlingService.resolvePaaanketVedtaksdatoFromPreviousBehandling(mottak.forrigeBehandlingId),
                    previousSaksbehandlerident = mottak.forrigeSaksbehandlerident,
                    gosysOppgaveId = mottak.gosysOppgaveId,
                    tilbakekreving = false,
                    varsletBehandlingstid = null,
                    forlengetBehandlingstidDraft = null,
                    gosysOppgaveRequired = mottak.gosysOppgaveRequired,
                    initiatingSystem = Behandling.InitiatingSystem.valueOf(mottak.sentFrom.name),
                    previousBehandlingId = mottak.forrigeBehandlingId,
                ),
            )

        ankebehandling.addMottakDokument(mottakDokumentSet = mottak.mottakDokument)

        logger.debug("Created ankebehandling {}", ankebehandling.id)

        behandlingService.connectDocumentsFromPreviousBehandlingToBehandling(
            behandlingId = ankebehandling.id,
            saksbehandlerIdent = systembrukerIdent,
            systemUserContext = true,
            ignoreCheckSkrivetilgang = true,
        )

        applicationEventPublisher.publishEvent(
            BehandlingChangedEvent(
                behandling = ankebehandling,
                changeList =
                    listOfNotNull(
                        createChange(
                            saksbehandlerident = systembrukerIdent,
                            felt = BehandlingChangedEvent.Felt.ANKEBEHANDLING_MOTTATT,
                            fraVerdi = null,
                            tilVerdi = "Opprettet",
                            behandlingId = ankebehandling.id,
                        ),
                    ),
            ),
        )

        applicationEventPublisher.publishEvent(
            BehandlingChangedEvent(
                behandling = ankebehandling,
                changeList =
                    listOfNotNull(
                        createChange(
                            saksbehandlerident = systembrukerIdent,
                            felt = BehandlingChangedEvent.Felt.ANKEBEHANDLING_OPPRETTET,
                            fraVerdi = null,
                            tilVerdi = "Opprettet",
                            behandlingId = ankebehandling.id,
                        ),
                    ),
            ),
        )

        ankebehandling.opprettetSendt = true

        return ankebehandling
    }

    /**
     * An anke etter 2027 reaches Kabal the same way as an anke foer 2027 does, the only difference being
     * the type of behandling that is created. Anker registered in Kabin on behalf of Trygderetten also
     * carry the saksnummer Trygderetten gave the case.
     */
    fun createAnkebehandlingEtter2027FromMottak(mottak: Mottak): AnkebehandlingEtter2027 {
        val kvalitetsvurderingVersion = kakaVersionUtil.getKakaVersion()

        // Validated when the mottak is created, so this only guards against future callers.
        val trygderettenSaksnummer =
            mottak.trygderettenSaksnummer
                ?: error("Mottak with kildereferanse ${mottak.kildeReferanse} for anke etter 2027 is missing saksnummer fra Trygderetten.")

        val ankebehandling =
            ankebehandlingEtter2027Repository.save(
                AnkebehandlingEtter2027(
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
                    saksdokumenter =
                        dokumentService.createSaksdokumenterFromJournalpostIdList(
                            mottak.mottakDokument.map { it.journalpostId },
                        ),
                    // TODO: Finn ut mer her.
                    kakaKvalitetsvurderingId = null,
                    kakaKvalitetsvurderingVersion = kvalitetsvurderingVersion,
                    hjemler = mottak.hjemler,
                    klageBehandlendeEnhet = mottak.forrigeBehandlendeEnhet,
                    paaanketVedtaksdato = behandlingService.resolvePaaanketVedtaksdatoFromPreviousBehandling(mottak.forrigeBehandlingId),
                    trygderettenSaksnummer = trygderettenSaksnummer,
                    previousSaksbehandlerident = mottak.forrigeSaksbehandlerident,
                    gosysOppgaveId = mottak.gosysOppgaveId,
                    tilbakekreving = false,
                    gosysOppgaveRequired = mottak.gosysOppgaveRequired,
                    initiatingSystem = Behandling.InitiatingSystem.valueOf(mottak.sentFrom.name),
                    previousBehandlingId = mottak.forrigeBehandlingId,
                ),
            )

        ankebehandling.addMottakDokument(mottakDokumentSet = mottak.mottakDokument)

        logger.debug("Created ankebehandling etter 2027 {}", ankebehandling.id)

        behandlingService.connectDocumentsFromPreviousBehandlingToBehandling(
            behandlingId = ankebehandling.id,
            saksbehandlerIdent = systembrukerIdent,
            systemUserContext = true,
            ignoreCheckSkrivetilgang = true,
        )

        applicationEventPublisher.publishEvent(
            BehandlingChangedEvent(
                behandling = ankebehandling,
                changeList =
                    listOfNotNull(
                        createChange(
                            saksbehandlerident = systembrukerIdent,
                            felt = BehandlingChangedEvent.Felt.ANKEBEHANDLING_MOTTATT,
                            fraVerdi = null,
                            tilVerdi = "Opprettet",
                            behandlingId = ankebehandling.id,
                        ),
                    ),
            ),
        )

        applicationEventPublisher.publishEvent(
            BehandlingChangedEvent(
                behandling = ankebehandling,
                changeList =
                    listOfNotNull(
                        createChange(
                            saksbehandlerident = systembrukerIdent,
                            felt = BehandlingChangedEvent.Felt.ANKEBEHANDLING_OPPRETTET,
                            fraVerdi = null,
                            tilVerdi = "Opprettet",
                            behandlingId = ankebehandling.id,
                        ),
                    ),
            ),
        )

        ankebehandling.opprettetSendt = true

        return ankebehandling
    }

    fun createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(
        ankeITrygderettenbehandlingFoer2027: AnkeITrygderettenbehandlingFoer2027,
    ): AnkebehandlingFoer2027 {
        val ankebehandling =
            ankebehandlingFoer2027Repository.save(
                AnkebehandlingFoer2027(
                    previousBehandlingId = ankeITrygderettenbehandlingFoer2027.id,
                    klager = ankeITrygderettenbehandlingFoer2027.klager.copy(),
                    sakenGjelder = ankeITrygderettenbehandlingFoer2027.sakenGjelder.copy(),
                    prosessfullmektig = ankeITrygderettenbehandlingFoer2027.prosessfullmektig,
                    ytelse = ankeITrygderettenbehandlingFoer2027.ytelse,
                    type = Type.ANKE_FOER_2027,
                    kildeReferanse = ankeITrygderettenbehandlingFoer2027.kildeReferanse,
                    dvhReferanse = ankeITrygderettenbehandlingFoer2027.dvhReferanse,
                    fagsystem = ankeITrygderettenbehandlingFoer2027.fagsystem,
                    fagsakId = ankeITrygderettenbehandlingFoer2027.fagsakId,
                    mottattKlageinstans = ankeITrygderettenbehandlingFoer2027.mottattKlageinstans,
                    tildeling = ankeITrygderettenbehandlingFoer2027.tildeling,
                    frist = LocalDate.now() + Period.ofWeeks(0),
                    kakaKvalitetsvurderingId = kakaApiGateway.createKvalitetsvurdering(kvalitetsvurderingVersion = 2).kvalitetsvurderingId,
                    kakaKvalitetsvurderingVersion = 2,
                    hjemler = ankeITrygderettenbehandlingFoer2027.hjemler,
                    klageBehandlendeEnhet = ankeITrygderettenbehandlingFoer2027.tildeling?.enhet!!,
                    paaanketVedtaksdato = ankeITrygderettenbehandlingFoer2027.paaanketVedtaksdato,
                    forsterketRett = ankeITrygderettenbehandlingFoer2027.forsterketRett,
                    previousSaksbehandlerident = ankeITrygderettenbehandlingFoer2027.tildeling?.saksbehandlerident,
                    gosysOppgaveId = ankeITrygderettenbehandlingFoer2027.gosysOppgaveId,
                    tilbakekreving = ankeITrygderettenbehandlingFoer2027.tilbakekreving,
                    varsletBehandlingstid = null,
                    forlengetBehandlingstidDraft = null,
                    gosysOppgaveRequired = ankeITrygderettenbehandlingFoer2027.gosysOppgaveRequired,
                    initiatingSystem = Behandling.InitiatingSystem.KABAL,
                ),
            )
        logger.debug(
            "Created ankebehandlingFoer2027 {} from ankeITrygderettenbehandlingFoer2027 {}",
            ankebehandling.id,
            ankeITrygderettenbehandlingFoer2027.id,
        )

        behandlingService.connectDocumentsFromPreviousBehandlingToBehandling(
            behandlingId = ankebehandling.id,
            saksbehandlerIdent = systembrukerIdent,
            systemUserContext = true,
            ignoreCheckSkrivetilgang = true,
        )

        applicationEventPublisher.publishEvent(
            BehandlingChangedEvent(
                behandling = ankebehandling,
                changeList =
                    listOfNotNull(
                        createChange(
                            saksbehandlerident = ankeITrygderettenbehandlingFoer2027.tildeling!!.saksbehandlerident,
                            felt = BehandlingChangedEvent.Felt.ANKEBEHANDLING_OPPRETTET_BASERT_PAA_ANKE_I_TRYGDERETTEN,
                            fraVerdi = null,
                            tilVerdi = "Opprettet",
                            behandlingId = ankebehandling.id,
                        ),
                    ),
            ),
        )

        // TODO: Undersøk om vi skal sende noen infomelding om at dette har skjedd

        return ankebehandling
    }

    fun createAnkebehandlingEtter2027FromAnkeITrygderettenbehandlingEtter2027(
        ankeITrygderettenbehandlingEtter2027: AnkeITrygderettenbehandlingEtter2027,
    ): AnkebehandlingEtter2027 {
        val ankebehandling =
            ankebehandlingEtter2027Repository.save(
                AnkebehandlingEtter2027(
                    previousBehandlingId = ankeITrygderettenbehandlingEtter2027.id,
                    klager = ankeITrygderettenbehandlingEtter2027.klager.copy(),
                    sakenGjelder = ankeITrygderettenbehandlingEtter2027.sakenGjelder.copy(),
                    prosessfullmektig = ankeITrygderettenbehandlingEtter2027.prosessfullmektig,
                    ytelse = ankeITrygderettenbehandlingEtter2027.ytelse,
                    type = Type.ANKE_ETTER_2027,
                    kildeReferanse = ankeITrygderettenbehandlingEtter2027.kildeReferanse,
                    dvhReferanse = ankeITrygderettenbehandlingEtter2027.dvhReferanse,
                    fagsystem = ankeITrygderettenbehandlingEtter2027.fagsystem,
                    fagsakId = ankeITrygderettenbehandlingEtter2027.fagsakId,
                    mottattKlageinstans = ankeITrygderettenbehandlingEtter2027.mottattKlageinstans,
                    tildeling = ankeITrygderettenbehandlingEtter2027.tildeling,
                    frist = LocalDate.now() + Period.ofWeeks(0),
                    // TODO: Fnn ut mer her
                    kakaKvalitetsvurderingId = null,
                    kakaKvalitetsvurderingVersion = 2,
                    hjemler = ankeITrygderettenbehandlingEtter2027.hjemler,
                    klageBehandlendeEnhet = ankeITrygderettenbehandlingEtter2027.tildeling?.enhet!!,
                    paaanketVedtaksdato = ankeITrygderettenbehandlingEtter2027.paaanketVedtaksdato,
                    forsterketRett = ankeITrygderettenbehandlingEtter2027.forsterketRett,
                    previousSaksbehandlerident = ankeITrygderettenbehandlingEtter2027.tildeling?.saksbehandlerident,
                    gosysOppgaveId = ankeITrygderettenbehandlingEtter2027.gosysOppgaveId,
                    tilbakekreving = ankeITrygderettenbehandlingEtter2027.tilbakekreving,
                    trygderettenSaksnummer = ankeITrygderettenbehandlingEtter2027.trygderettenSaksnummer,
                    gosysOppgaveRequired = ankeITrygderettenbehandlingEtter2027.gosysOppgaveRequired,
                    initiatingSystem = Behandling.InitiatingSystem.KABAL,
                ),
            )
        logger.debug(
            "Created ankebehandlingEtter2027 {} from ankeITrygderettenbehandlingEtter2027 {}",
            ankebehandling.id,
            ankeITrygderettenbehandlingEtter2027.id,
        )

        behandlingService.connectDocumentsFromPreviousBehandlingToBehandling(
            behandlingId = ankebehandling.id,
            saksbehandlerIdent = systembrukerIdent,
            systemUserContext = true,
            ignoreCheckSkrivetilgang = true,
        )

        applicationEventPublisher.publishEvent(
            BehandlingChangedEvent(
                behandling = ankebehandling,
                changeList =
                    listOfNotNull(
                        createChange(
                            saksbehandlerident = ankeITrygderettenbehandlingEtter2027.tildeling!!.saksbehandlerident,
                            felt = BehandlingChangedEvent.Felt.ANKEBEHANDLING_OPPRETTET_BASERT_PAA_ANKE_I_TRYGDERETTEN,
                            fraVerdi = null,
                            tilVerdi = "Opprettet",
                            behandlingId = ankebehandling.id,
                        ),
                    ),
            ),
        )

        // TODO: Undersøk om vi skal sende noen infomelding om at dette har skjedd

        return ankebehandling
    }
}
