package no.nav.klage.oppgave.service

import no.nav.klage.dokument.api.view.JournalfoertDokumentReference
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.GjenopptakITrygderettenbehandlingEtter2027
import no.nav.klage.oppgave.domain.behandling.GjenopptakITrygderettenbehandlingEtter2027Input
import no.nav.klage.oppgave.domain.behandling.GjenopptakITrygderettenbehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.GjenopptakITrygderettenbehandlingFoer2027Input
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent.Change.Companion.createChange
import no.nav.klage.oppgave.repositories.GjenopptakITrygderettenbehandlingEtter2027Repository
import no.nav.klage.oppgave.repositories.GjenopptakITrygderettenbehandlingFoer2027Repository
import no.nav.klage.oppgave.util.getLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class GjenopptakITrygderettenbehandlingService(
    private val gjenopptakITrygderettenbehandlingFoer2027Repository: GjenopptakITrygderettenbehandlingFoer2027Repository,
    private val gjenopptakITrygderettenbehandlingEtter2027Repository: GjenopptakITrygderettenbehandlingEtter2027Repository,
    private val behandlingService: BehandlingService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val kantService: KantService,
    @Value($$"${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun createGjenopptakITrygderettenbehandlingFoer2027(
        input: GjenopptakITrygderettenbehandlingFoer2027Input,
    ): GjenopptakITrygderettenbehandlingFoer2027 {
        val gjenopptakITrygderettenbehandlingFoer2027 =
            gjenopptakITrygderettenbehandlingFoer2027Repository.save(
                GjenopptakITrygderettenbehandlingFoer2027(
                    klager = input.klager.copy(),
                    sakenGjelder = input.sakenGjelder?.copy() ?: input.klager.toSakenGjelder(),
                    prosessfullmektig = input.prosessfullmektig,
                    ytelse = input.ytelse,
                    type = input.type,
                    kildeReferanse = input.kildeReferanse,
                    dvhReferanse = input.dvhReferanse,
                    fagsystem = input.fagsystem,
                    fagsakId = input.fagsakId,
                    sakstype = input.sakstype,
                    arkivsaksnummer = input.arkivsaksnummer,
                    mottattKlageinstans = input.sakMottattKlageinstans,
                    tildeling = null,
                    hjemler =
                        if (input.innsendingsHjemler.isNullOrEmpty()) {
                            mutableSetOf(Hjemmel.MANGLER)
                        } else {
                            input.innsendingsHjemler
                        },
                    sendtTilTrygderetten = input.sendtTilTrygderetten,
                    paaanketVedtaksdato = input.paaanketVedtaksdato,
                    forsterketRett = input.forsterketRett,
                    kjennelseMottatt = null,
                    previousSaksbehandlerident = input.previousSaksbehandlerident,
                    gosysOppgaveId = input.gosysOppgaveId,
                    tilbakekreving = input.tilbakekreving,
                    gosysOppgaveRequired = input.gosysOppgaveRequired,
                    initiatingSystem = Behandling.InitiatingSystem.KABAL,
                    previousBehandlingId = input.previousBehandlingId,
                ),
            )
        logger.debug("Created gjenopptakITrygderettenbehandlingFoer2027 {}", gjenopptakITrygderettenbehandlingFoer2027.id)

        if (kantService.kantShouldBeUsedForOldTypes()) {
            gjenopptakITrygderettenbehandlingFoer2027.kantReference =
                kantService.createSaksdataInKant(gjenopptakITrygderettenbehandlingFoer2027).id
        }

        behandlingService.washAndSetRegistreringshjemler(
            registreringsHjemmelSet = input.registreringsHjemmelSet,
            ytelse = input.ytelse,
            behandlingId = gjenopptakITrygderettenbehandlingFoer2027.id,
        )

        behandlingService.connectDocumentsToBehandling(
            behandlingId = gjenopptakITrygderettenbehandlingFoer2027.id,
            journalfoertDokumentReferenceSet =
                input.saksdokumenter
                    .map {
                        JournalfoertDokumentReference(
                            journalpostId = it.journalpostId,
                            dokumentInfoId = it.dokumentInfoId,
                        )
                    }.toSet(),
            saksbehandlerIdent = systembrukerIdent,
            systemUserContext = true,
            ignoreCheckSkrivetilgang = true,
        )

        applicationEventPublisher.publishEvent(
            BehandlingChangedEvent(
                behandling = gjenopptakITrygderettenbehandlingFoer2027,
                changeList =
                    listOfNotNull(
                        createChange(
                            saksbehandlerident = systembrukerIdent,
                            felt = BehandlingChangedEvent.Felt.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN_OPPRETTET,
                            fraVerdi = null,
                            tilVerdi = "Opprettet",
                            behandlingId = gjenopptakITrygderettenbehandlingFoer2027.id,
                        ),
                    ),
            ),
        )

        return gjenopptakITrygderettenbehandlingFoer2027
    }

    fun createGjenopptakITrygderettenbehandlingEtter2027(
        input: GjenopptakITrygderettenbehandlingEtter2027Input,
    ): GjenopptakITrygderettenbehandlingEtter2027 {
        val gjenopptakITrygderettenbehandlingEtter2027 =
            gjenopptakITrygderettenbehandlingEtter2027Repository.save(
                GjenopptakITrygderettenbehandlingEtter2027(
                    klager = input.klager.copy(),
                    sakenGjelder = input.sakenGjelder?.copy() ?: input.klager.toSakenGjelder(),
                    prosessfullmektig = input.prosessfullmektig,
                    ytelse = input.ytelse,
                    type = input.type,
                    trygderettenSaksnummer = input.trygderettenSaksnummer,
                    kildeReferanse = input.kildeReferanse,
                    dvhReferanse = input.dvhReferanse,
                    fagsystem = input.fagsystem,
                    fagsakId = input.fagsakId,
                    sakstype = input.sakstype,
                    arkivsaksnummer = input.arkivsaksnummer,
                    mottattKlageinstans = input.sakMottattKlageinstans,
                    tildeling = null,
                    hjemler =
                        if (input.innsendingsHjemler.isNullOrEmpty()) {
                            mutableSetOf(Hjemmel.MANGLER)
                        } else {
                            input.innsendingsHjemler
                        },
                    sendtTilTrygderetten = input.sendtTilTrygderetten,
                    paaanketVedtaksdato = input.paaanketVedtaksdato,
                    forsterketRett = input.forsterketRett,
                    kjennelseMottatt = null,
                    previousSaksbehandlerident = input.previousSaksbehandlerident,
                    gosysOppgaveId = input.gosysOppgaveId,
                    tilbakekreving = input.tilbakekreving,
                    gosysOppgaveRequired = input.gosysOppgaveRequired,
                    initiatingSystem = Behandling.InitiatingSystem.KABAL,
                    previousBehandlingId = input.previousBehandlingId,
                ),
            )
        logger.debug("Created gjenopptakITrygderettenbehandlingEtter2027 {}", gjenopptakITrygderettenbehandlingEtter2027.id)

        gjenopptakITrygderettenbehandlingEtter2027.kantReference =
            kantService.createSaksdataInKant(gjenopptakITrygderettenbehandlingEtter2027).id

        behandlingService.washAndSetRegistreringshjemler(
            registreringsHjemmelSet = input.registreringsHjemmelSet,
            ytelse = input.ytelse,
            behandlingId = gjenopptakITrygderettenbehandlingEtter2027.id,
        )

        behandlingService.connectDocumentsToBehandling(
            behandlingId = gjenopptakITrygderettenbehandlingEtter2027.id,
            journalfoertDokumentReferenceSet =
                input.saksdokumenter
                    .map {
                        JournalfoertDokumentReference(
                            journalpostId = it.journalpostId,
                            dokumentInfoId = it.dokumentInfoId,
                        )
                    }.toSet(),
            saksbehandlerIdent = systembrukerIdent,
            systemUserContext = true,
            ignoreCheckSkrivetilgang = true,
        )

        applicationEventPublisher.publishEvent(
            BehandlingChangedEvent(
                behandling = gjenopptakITrygderettenbehandlingEtter2027,
                changeList =
                    listOfNotNull(
                        createChange(
                            saksbehandlerident = systembrukerIdent,
                            felt = BehandlingChangedEvent.Felt.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN_OPPRETTET,
                            fraVerdi = null,
                            tilVerdi = "Opprettet",
                            behandlingId = gjenopptakITrygderettenbehandlingEtter2027.id,
                        ),
                    ),
            ),
        )

        return gjenopptakITrygderettenbehandlingEtter2027
    }
}
