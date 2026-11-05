package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.clients.kaka.KakaApiGateway
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.GjenopptakITrygderettenbehandlingEtter2027
import no.nav.klage.oppgave.domain.behandling.GjenopptakITrygderettenbehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.GjenopptaksbehandlingEtter2027
import no.nav.klage.oppgave.domain.behandling.GjenopptaksbehandlingFoer2027
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent.Change.Companion.createChange
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent.Felt
import no.nav.klage.oppgave.domain.mottak.Mottak
import no.nav.klage.oppgave.repositories.GjenopptaksbehandlingEtter2027Repository
import no.nav.klage.oppgave.repositories.GjenopptaksbehandlingFoer2027Repository
import no.nav.klage.oppgave.util.KakaVersionUtil
import no.nav.klage.oppgave.util.getLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.Period

@Service
class GjenopptaksbehandlingService(
    private val gjenopptaksbehandlingFoer2027Repository: GjenopptaksbehandlingFoer2027Repository,
    private val gjenopptaksbehandlingEtter2027Repository: GjenopptaksbehandlingEtter2027Repository,
    private val dokumentService: DokumentService,
    private val behandlingService: BehandlingService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val kakaApiGateway: KakaApiGateway,
    @Value($$"${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
    private val kakaVersionUtil: KakaVersionUtil,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun createGjenopptaksbehandlingFoer2027FromMottak(mottak: Mottak): GjenopptaksbehandlingFoer2027 {
        val kvalitetsvurderingVersion = kakaVersionUtil.getKakaVersion()
        val gjenopptaksbehandling =
            gjenopptaksbehandlingFoer2027Repository.save(
                GjenopptaksbehandlingFoer2027(
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
                    kakaKvalitetsvurderingId =
                        kakaApiGateway
                            .createKvalitetsvurdering(
                                kvalitetsvurderingVersion = kvalitetsvurderingVersion,
                            ).kvalitetsvurderingId,
                    kakaKvalitetsvurderingVersion = kvalitetsvurderingVersion,
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
                    paaanketVedtaksdato =
                        if (mottak.isBasedOnJournalpost) {
                            null
                        } else {
                            behandlingService.resolvePaaanketVedtaksdatoFromPreviousBehandling(
                                mottak.forrigeBehandlingId,
                            )
                        },
                ),
            )

        gjenopptaksbehandling.addMottakDokument(mottakDokumentSet = mottak.mottakDokument)

        logger.debug("Created {} with id {}", gjenopptaksbehandling.javaClass.name, gjenopptaksbehandling.id)

        behandlingService.connectDocumentsFromPreviousBehandlingToBehandling(
            behandlingId = gjenopptaksbehandling.id,
            saksbehandlerIdent = systembrukerIdent,
            systemUserContext = true,
            ignoreCheckSkrivetilgang = true,
        )

        applicationEventPublisher.publishEvent(
            BehandlingChangedEvent(
                behandling = gjenopptaksbehandling,
                changeList =
                    listOfNotNull(
                        createChange(
                            saksbehandlerident = systembrukerIdent,
                            felt = Felt.BEGJAERING_OM_GJENOPPTAKSBEHANDLING_MOTTATT,
                            fraVerdi = null,
                            tilVerdi = "Opprettet",
                            behandlingId = gjenopptaksbehandling.id,
                        ),
                    ),
            ),
        )

        applicationEventPublisher.publishEvent(
            BehandlingChangedEvent(
                behandling = gjenopptaksbehandling,
                changeList =
                    listOfNotNull(
                        createChange(
                            saksbehandlerident = systembrukerIdent,
                            felt = Felt.BEGJAERING_OM_GJENOPPTAKSBEHANDLING_OPPRETTET,
                            fraVerdi = null,
                            tilVerdi = "Opprettet",
                            behandlingId = gjenopptaksbehandling.id,
                        ),
                    ),
            ),
        )

        gjenopptaksbehandling.opprettetSendt = true

        return gjenopptaksbehandling
    }

    fun createGjenopptaksbehandlingEtter2027FromMottak(mottak: Mottak): GjenopptaksbehandlingEtter2027 {
        val kvalitetsvurderingVersion = kakaVersionUtil.getKakaVersion()

        // Validated when the mottak is created, so this only guards against future callers.
        val trygderettenSaksnummer =
            mottak.trygderettenSaksnummer
                ?: error("Mottak with kildereferanse ${mottak.kildeReferanse} for anke etter 2027 is missing saksnummer fra Trygderetten.")

        val gjenopptaksbehandling =
            gjenopptaksbehandlingEtter2027Repository.save(
                GjenopptaksbehandlingEtter2027(
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
                    kakaKvalitetsvurderingId =
                        kakaApiGateway
                            .createKvalitetsvurdering(
                                kvalitetsvurderingVersion = kvalitetsvurderingVersion,
                            ).kvalitetsvurderingId,
                    kakaKvalitetsvurderingVersion = kvalitetsvurderingVersion,
                    hjemler = mottak.hjemler,
                    previousSaksbehandlerident = mottak.forrigeSaksbehandlerident,
                    klageBehandlendeEnhet = mottak.forrigeBehandlendeEnhet,
                    gosysOppgaveId = mottak.gosysOppgaveId,
                    tilbakekreving = false,
                    gosysOppgaveRequired = mottak.gosysOppgaveRequired,
                    initiatingSystem = Behandling.InitiatingSystem.valueOf(mottak.sentFrom.name),
                    previousBehandlingId = mottak.forrigeBehandlingId,
                    paaanketVedtaksdato =
                        if (mottak.isBasedOnJournalpost) {
                            null
                        } else {
                            behandlingService.resolvePaaanketVedtaksdatoFromPreviousBehandling(
                                mottak.forrigeBehandlingId,
                            )
                        },
                    trygderettenSaksnummer = trygderettenSaksnummer,
                ),
            )

        gjenopptaksbehandling.addMottakDokument(mottakDokumentSet = mottak.mottakDokument)

        logger.debug("Created {} with id {}", gjenopptaksbehandling.javaClass.name, gjenopptaksbehandling.id)

        behandlingService.connectDocumentsFromPreviousBehandlingToBehandling(
            behandlingId = gjenopptaksbehandling.id,
            saksbehandlerIdent = systembrukerIdent,
            systemUserContext = true,
            ignoreCheckSkrivetilgang = true,
        )

        applicationEventPublisher.publishEvent(
            BehandlingChangedEvent(
                behandling = gjenopptaksbehandling,
                changeList =
                    listOfNotNull(
                        createChange(
                            saksbehandlerident = systembrukerIdent,
                            felt = Felt.BEGJAERING_OM_GJENOPPTAKSBEHANDLING_MOTTATT,
                            fraVerdi = null,
                            tilVerdi = "Opprettet",
                            behandlingId = gjenopptaksbehandling.id,
                        ),
                    ),
            ),
        )

        applicationEventPublisher.publishEvent(
            BehandlingChangedEvent(
                behandling = gjenopptaksbehandling,
                changeList =
                    listOfNotNull(
                        createChange(
                            saksbehandlerident = systembrukerIdent,
                            felt = Felt.BEGJAERING_OM_GJENOPPTAKSBEHANDLING_OPPRETTET,
                            fraVerdi = null,
                            tilVerdi = "Opprettet",
                            behandlingId = gjenopptaksbehandling.id,
                        ),
                    ),
            ),
        )

        gjenopptaksbehandling.opprettetSendt = true

        return gjenopptaksbehandling
    }

    fun createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
        gjenopptakITrygderettenbehandlingFoer2027: GjenopptakITrygderettenbehandlingFoer2027,
    ): GjenopptaksbehandlingFoer2027 {
        val kvalitetsvurderingVersion = kakaVersionUtil.getKakaVersion()

        val gjenopptaksbehandling =
            gjenopptaksbehandlingFoer2027Repository.save(
                GjenopptaksbehandlingFoer2027(
                    klager = gjenopptakITrygderettenbehandlingFoer2027.klager.copy(),
                    sakenGjelder = gjenopptakITrygderettenbehandlingFoer2027.sakenGjelder.copy(),
                    prosessfullmektig = gjenopptakITrygderettenbehandlingFoer2027.prosessfullmektig,
                    ytelse = gjenopptakITrygderettenbehandlingFoer2027.ytelse,
                    type = Type.BEGJAERING_OM_GJENOPPTAK_FOER_2027,
                    kildeReferanse = gjenopptakITrygderettenbehandlingFoer2027.kildeReferanse,
                    dvhReferanse = gjenopptakITrygderettenbehandlingFoer2027.dvhReferanse,
                    fagsystem = gjenopptakITrygderettenbehandlingFoer2027.fagsystem,
                    fagsakId = gjenopptakITrygderettenbehandlingFoer2027.fagsakId,
                    sakstype = gjenopptakITrygderettenbehandlingFoer2027.sakstype,
                    arkivsaksnummer = gjenopptakITrygderettenbehandlingFoer2027.arkivsaksnummer,
                    mottattKlageinstans = gjenopptakITrygderettenbehandlingFoer2027.mottattKlageinstans,
                    tildeling = gjenopptakITrygderettenbehandlingFoer2027.tildeling,
                    // Hva slags frist?
                    frist = LocalDate.now() + Period.ofWeeks(0),
                    kakaKvalitetsvurderingId =
                        kakaApiGateway
                            .createKvalitetsvurdering(
                                kvalitetsvurderingVersion = kvalitetsvurderingVersion,
                            ).kvalitetsvurderingId,
                    kakaKvalitetsvurderingVersion = kvalitetsvurderingVersion,
                    hjemler = gjenopptakITrygderettenbehandlingFoer2027.hjemler,
                    klageBehandlendeEnhet = gjenopptakITrygderettenbehandlingFoer2027.tildeling?.enhet!!,
                    paaanketVedtaksdato = gjenopptakITrygderettenbehandlingFoer2027.paaanketVedtaksdato,
                    forsterketRett = gjenopptakITrygderettenbehandlingFoer2027.forsterketRett,
                    previousSaksbehandlerident = gjenopptakITrygderettenbehandlingFoer2027.tildeling?.saksbehandlerident,
                    gosysOppgaveId = gjenopptakITrygderettenbehandlingFoer2027.gosysOppgaveId,
                    tilbakekreving = gjenopptakITrygderettenbehandlingFoer2027.tilbakekreving,
                    varsletBehandlingstid = null,
                    forlengetBehandlingstidDraft = null,
                    gosysOppgaveRequired = gjenopptakITrygderettenbehandlingFoer2027.gosysOppgaveRequired,
                    initiatingSystem = Behandling.InitiatingSystem.KABAL,
                    previousBehandlingId = gjenopptakITrygderettenbehandlingFoer2027.id,
                ),
            )

        logger.debug(
            "Created gjenopptaksbehandling {} from gjenopptakITrygderettenbehandling {}",
            gjenopptaksbehandling.id,
            gjenopptakITrygderettenbehandlingFoer2027.id,
        )

        behandlingService.connectDocumentsFromPreviousBehandlingToBehandling(
            behandlingId = gjenopptaksbehandling.id,
            saksbehandlerIdent = systembrukerIdent,
            systemUserContext = true,
            ignoreCheckSkrivetilgang = true,
        )

        applicationEventPublisher.publishEvent(
            BehandlingChangedEvent(
                behandling = gjenopptaksbehandling,
                changeList =
                    listOfNotNull(
                        createChange(
                            saksbehandlerident = gjenopptakITrygderettenbehandlingFoer2027.tildeling!!.saksbehandlerident,
                            felt = Felt.BEGJAERING_OM_GJENOPPTAKSBEHANDLING_OPPRETTET_BASERT_PAA_BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN,
                            fraVerdi = null,
                            tilVerdi = "Opprettet",
                            behandlingId = gjenopptaksbehandling.id,
                        ),
                    ),
            ),
        )

        return gjenopptaksbehandling
    }

    fun createGjenopptaksbehandlingEtter2027FromGjenopptakITrygderettenbehandlingEtter2027(
        gjenopptakITrygderettenbehandlingEtter2027: GjenopptakITrygderettenbehandlingEtter2027,
    ): GjenopptaksbehandlingEtter2027 {
        val kvalitetsvurderingVersion = kakaVersionUtil.getKakaVersion()

        val gjenopptaksbehandling =
            gjenopptaksbehandlingEtter2027Repository.save(
                GjenopptaksbehandlingEtter2027(
                    klager = gjenopptakITrygderettenbehandlingEtter2027.klager.copy(),
                    sakenGjelder = gjenopptakITrygderettenbehandlingEtter2027.sakenGjelder.copy(),
                    prosessfullmektig = gjenopptakITrygderettenbehandlingEtter2027.prosessfullmektig,
                    ytelse = gjenopptakITrygderettenbehandlingEtter2027.ytelse,
                    type = Type.BEGJAERING_OM_GJENOPPTAK_ETTER_2027,
                    kildeReferanse = gjenopptakITrygderettenbehandlingEtter2027.kildeReferanse,
                    dvhReferanse = gjenopptakITrygderettenbehandlingEtter2027.dvhReferanse,
                    fagsystem = gjenopptakITrygderettenbehandlingEtter2027.fagsystem,
                    fagsakId = gjenopptakITrygderettenbehandlingEtter2027.fagsakId,
                    sakstype = gjenopptakITrygderettenbehandlingEtter2027.sakstype,
                    arkivsaksnummer = gjenopptakITrygderettenbehandlingEtter2027.arkivsaksnummer,
                    mottattKlageinstans = gjenopptakITrygderettenbehandlingEtter2027.mottattKlageinstans,
                    tildeling = gjenopptakITrygderettenbehandlingEtter2027.tildeling,
                    // Hva slags frist?
                    frist = LocalDate.now() + Period.ofWeeks(0),
                    kakaKvalitetsvurderingId =
                        kakaApiGateway
                            .createKvalitetsvurdering(
                                kvalitetsvurderingVersion = kvalitetsvurderingVersion,
                            ).kvalitetsvurderingId,
                    kakaKvalitetsvurderingVersion = kvalitetsvurderingVersion,
                    hjemler = gjenopptakITrygderettenbehandlingEtter2027.hjemler,
                    klageBehandlendeEnhet = gjenopptakITrygderettenbehandlingEtter2027.tildeling?.enhet!!,
                    paaanketVedtaksdato = gjenopptakITrygderettenbehandlingEtter2027.paaanketVedtaksdato,
                    forsterketRett = gjenopptakITrygderettenbehandlingEtter2027.forsterketRett,
                    previousSaksbehandlerident = gjenopptakITrygderettenbehandlingEtter2027.tildeling?.saksbehandlerident,
                    gosysOppgaveId = gjenopptakITrygderettenbehandlingEtter2027.gosysOppgaveId,
                    tilbakekreving = gjenopptakITrygderettenbehandlingEtter2027.tilbakekreving,
                    gosysOppgaveRequired = gjenopptakITrygderettenbehandlingEtter2027.gosysOppgaveRequired,
                    initiatingSystem = Behandling.InitiatingSystem.KABAL,
                    previousBehandlingId = gjenopptakITrygderettenbehandlingEtter2027.id,
                    trygderettenSaksnummer = gjenopptakITrygderettenbehandlingEtter2027.trygderettenSaksnummer,
                ),
            )

        logger.debug(
            "Created gjenopptaksbehandling {} from gjenopptakITrygderettenbehandling {}",
            gjenopptaksbehandling.id,
            gjenopptakITrygderettenbehandlingEtter2027.id,
        )

        behandlingService.connectDocumentsFromPreviousBehandlingToBehandling(
            behandlingId = gjenopptaksbehandling.id,
            saksbehandlerIdent = systembrukerIdent,
            systemUserContext = true,
            ignoreCheckSkrivetilgang = true,
        )

        applicationEventPublisher.publishEvent(
            BehandlingChangedEvent(
                behandling = gjenopptaksbehandling,
                changeList =
                    listOfNotNull(
                        createChange(
                            saksbehandlerident = gjenopptakITrygderettenbehandlingEtter2027.tildeling!!.saksbehandlerident,
                            felt = Felt.BEGJAERING_OM_GJENOPPTAKSBEHANDLING_OPPRETTET_BASERT_PAA_BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN,
                            fraVerdi = null,
                            tilVerdi = "Opprettet",
                            behandlingId = gjenopptaksbehandling.id,
                        ),
                    ),
            ),
        )

        return gjenopptaksbehandling
    }
}
