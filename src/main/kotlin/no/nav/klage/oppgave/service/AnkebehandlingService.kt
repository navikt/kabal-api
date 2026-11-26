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
    private val kantService: KantService,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun createAnkebehandlingFoer2027FromMottak(mottak: Mottak): AnkebehandlingFoer2027 {
        val kvalitetsvurderingVersion = kakaVersionUtil.getKakaVersion()

        val ankebehandlingFoer2027 =
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
                    sakstype = mottak.sakstype,
                    arkivsaksnummer = mottak.arkivsaksnummer,
                    mottattKlageinstans = mottak.sakMottattKaDato,
                    tildeling = null,
                    frist = mottak.generateFrist(),
                    saksdokumenter =
                        dokumentService.createSaksdokumenterFromJournalpostIdList(
                            mottak.mottakDokument.map { it.journalpostId },
                        ),
                    kakaKvalitetsvurderingId = null,
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

        // TODO: Remove after the switch
        if (kantService.kantShouldBeUsedForOldTypes()) {
            ankebehandlingFoer2027.kantReference = kantService.createSaksdataInKant(ankebehandlingFoer2027).id
        } else {
            ankebehandlingFoer2027.kakaKvalitetsvurderingId =
                kakaApiGateway
                    .createKvalitetsvurdering(
                        kvalitetsvurderingVersion = kvalitetsvurderingVersion,
                    ).kvalitetsvurderingId
        }

        ankebehandlingFoer2027.addMottakDokument(mottakDokumentSet = mottak.mottakDokument)

        logger.debug("Created ankebehandling {}", ankebehandlingFoer2027.id)

        behandlingService.connectDocumentsFromPreviousBehandlingToBehandling(
            behandlingId = ankebehandlingFoer2027.id,
            saksbehandlerIdent = systembrukerIdent,
            systemUserContext = true,
            ignoreCheckSkrivetilgang = true,
        )

        applicationEventPublisher.publishEvent(
            BehandlingChangedEvent(
                behandling = ankebehandlingFoer2027,
                changeList =
                    listOfNotNull(
                        createChange(
                            saksbehandlerident = systembrukerIdent,
                            felt = BehandlingChangedEvent.Felt.ANKEBEHANDLING_MOTTATT,
                            fraVerdi = null,
                            tilVerdi = "Opprettet",
                            behandlingId = ankebehandlingFoer2027.id,
                        ),
                    ),
            ),
        )

        applicationEventPublisher.publishEvent(
            BehandlingChangedEvent(
                behandling = ankebehandlingFoer2027,
                changeList =
                    listOfNotNull(
                        createChange(
                            saksbehandlerident = systembrukerIdent,
                            felt = BehandlingChangedEvent.Felt.ANKEBEHANDLING_OPPRETTET,
                            fraVerdi = null,
                            tilVerdi = "Opprettet",
                            behandlingId = ankebehandlingFoer2027.id,
                        ),
                    ),
            ),
        )

        ankebehandlingFoer2027.opprettetSendt = true

        return ankebehandlingFoer2027
    }

    /**
     * An anke etter 2027 reaches Kabal the same way as an anke foer 2027 does, the only difference being
     * the type of behandling that is created. Anker registered in Kabin on behalf of Trygderetten also
     * carry the saksnummer Trygderetten gave the case.
     */
    fun createAnkebehandlingEtter2027FromMottak(mottak: Mottak): AnkebehandlingEtter2027 {
        // Validated when the mottak is created, so this only guards against future callers.
        val trygderettenSaksnummer =
            mottak.trygderettenSaksnummer
                ?: error("Mottak with kildereferanse ${mottak.kildeReferanse} for anke etter 2027 is missing saksnummer fra Trygderetten.")

        val ankebehandlingEtter2027 =
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
                    sakstype = mottak.sakstype,
                    arkivsaksnummer = mottak.arkivsaksnummer,
                    mottattKlageinstans = mottak.sakMottattKaDato,
                    tildeling = null,
                    frist = mottak.generateFrist(),
                    saksdokumenter =
                        dokumentService.createSaksdokumenterFromJournalpostIdList(
                            mottak.mottakDokument.map { it.journalpostId },
                        ),
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

        ankebehandlingEtter2027.addMottakDokument(mottakDokumentSet = mottak.mottakDokument)
        ankebehandlingEtter2027.kantReference = kantService.createSaksdataInKant(ankebehandlingEtter2027).id

        logger.debug("Created ankebehandling etter 2027 {}", ankebehandlingEtter2027.id)

        behandlingService.connectDocumentsFromPreviousBehandlingToBehandling(
            behandlingId = ankebehandlingEtter2027.id,
            saksbehandlerIdent = systembrukerIdent,
            systemUserContext = true,
            ignoreCheckSkrivetilgang = true,
        )

        applicationEventPublisher.publishEvent(
            BehandlingChangedEvent(
                behandling = ankebehandlingEtter2027,
                changeList =
                    listOfNotNull(
                        createChange(
                            saksbehandlerident = systembrukerIdent,
                            felt = BehandlingChangedEvent.Felt.ANKEBEHANDLING_MOTTATT,
                            fraVerdi = null,
                            tilVerdi = "Opprettet",
                            behandlingId = ankebehandlingEtter2027.id,
                        ),
                    ),
            ),
        )

        applicationEventPublisher.publishEvent(
            BehandlingChangedEvent(
                behandling = ankebehandlingEtter2027,
                changeList =
                    listOfNotNull(
                        createChange(
                            saksbehandlerident = systembrukerIdent,
                            felt = BehandlingChangedEvent.Felt.ANKEBEHANDLING_OPPRETTET,
                            fraVerdi = null,
                            tilVerdi = "Opprettet",
                            behandlingId = ankebehandlingEtter2027.id,
                        ),
                    ),
            ),
        )

        ankebehandlingEtter2027.opprettetSendt = true

        return ankebehandlingEtter2027
    }

    fun createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(
        ankeITrygderettenbehandlingFoer2027: AnkeITrygderettenbehandlingFoer2027,
    ): AnkebehandlingFoer2027 {
        val kvalitetsvurderingVersion = kakaVersionUtil.getKakaVersion()
        val ankebehandlingFoer2027 =
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
                    sakstype = ankeITrygderettenbehandlingFoer2027.sakstype,
                    arkivsaksnummer = ankeITrygderettenbehandlingFoer2027.arkivsaksnummer,
                    mottattKlageinstans = ankeITrygderettenbehandlingFoer2027.mottattKlageinstans,
                    tildeling = ankeITrygderettenbehandlingFoer2027.tildeling,
                    frist = LocalDate.now() + Period.ofWeeks(0),
                    kakaKvalitetsvurderingId = null,
                    kakaKvalitetsvurderingVersion = kvalitetsvurderingVersion,
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
            ankebehandlingFoer2027.id,
            ankeITrygderettenbehandlingFoer2027.id,
        )

        // TODO: Remove after the switch
        if (kantService.kantShouldBeUsedForOldTypes()) {
            ankebehandlingFoer2027.kantReference = kantService.createSaksdataInKant(ankebehandlingFoer2027).id
        } else {
            ankebehandlingFoer2027.kakaKvalitetsvurderingId =
                kakaApiGateway.createKvalitetsvurdering(kvalitetsvurderingVersion = 2).kvalitetsvurderingId
        }

        behandlingService.connectDocumentsFromPreviousBehandlingToBehandling(
            behandlingId = ankebehandlingFoer2027.id,
            saksbehandlerIdent = systembrukerIdent,
            systemUserContext = true,
            ignoreCheckSkrivetilgang = true,
        )

        applicationEventPublisher.publishEvent(
            BehandlingChangedEvent(
                behandling = ankebehandlingFoer2027,
                changeList =
                    listOfNotNull(
                        createChange(
                            saksbehandlerident = ankeITrygderettenbehandlingFoer2027.tildeling!!.saksbehandlerident,
                            felt = BehandlingChangedEvent.Felt.ANKEBEHANDLING_OPPRETTET_BASERT_PAA_ANKE_I_TRYGDERETTEN,
                            fraVerdi = null,
                            tilVerdi = "Opprettet",
                            behandlingId = ankebehandlingFoer2027.id,
                        ),
                    ),
            ),
        )

        return ankebehandlingFoer2027
    }

    fun createAnkebehandlingEtter2027FromAnkeITrygderettenbehandlingEtter2027(
        ankeITrygderettenbehandlingEtter2027: AnkeITrygderettenbehandlingEtter2027,
    ): AnkebehandlingEtter2027 {
        val ankebehandlingEtter2027 =
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
                    sakstype = ankeITrygderettenbehandlingEtter2027.sakstype,
                    arkivsaksnummer = ankeITrygderettenbehandlingEtter2027.arkivsaksnummer,
                    mottattKlageinstans = ankeITrygderettenbehandlingEtter2027.mottattKlageinstans,
                    tildeling = ankeITrygderettenbehandlingEtter2027.tildeling,
                    frist = LocalDate.now() + Period.ofWeeks(0),
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
        ankebehandlingEtter2027.kantReference = kantService.createSaksdataInKant(ankebehandlingEtter2027).id
        logger.debug(
            "Created ankebehandlingEtter2027 {} from ankeITrygderettenbehandlingEtter2027 {}",
            ankebehandlingEtter2027.id,
            ankeITrygderettenbehandlingEtter2027.id,
        )

        behandlingService.connectDocumentsFromPreviousBehandlingToBehandling(
            behandlingId = ankebehandlingEtter2027.id,
            saksbehandlerIdent = systembrukerIdent,
            systemUserContext = true,
            ignoreCheckSkrivetilgang = true,
        )

        applicationEventPublisher.publishEvent(
            BehandlingChangedEvent(
                behandling = ankebehandlingEtter2027,
                changeList =
                    listOfNotNull(
                        createChange(
                            saksbehandlerident = ankeITrygderettenbehandlingEtter2027.tildeling!!.saksbehandlerident,
                            felt = BehandlingChangedEvent.Felt.ANKEBEHANDLING_OPPRETTET_BASERT_PAA_ANKE_I_TRYGDERETTEN,
                            fraVerdi = null,
                            tilVerdi = "Opprettet",
                            behandlingId = ankebehandlingEtter2027.id,
                        ),
                    ),
            ),
        )

        return ankebehandlingEtter2027
    }
}
