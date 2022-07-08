package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.oppgave.api.view.OversendtAnkeITrygderettenV1
import no.nav.klage.oppgave.api.view.createAnkeITrygderettenbehandlingInput
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.domain.klage.AnkeITrygderettenbehandling
import no.nav.klage.oppgave.domain.klage.AnkeITrygderettenbehandlingInput
import no.nav.klage.oppgave.domain.klage.Delbehandling
import no.nav.klage.oppgave.repositories.AnkeITrygderettenbehandlingRepository
import no.nav.klage.oppgave.util.getLogger
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import javax.transaction.Transactional

@Service
@Transactional
class AnkeITrygderettenbehandlingService(
    private val ankeITrygderettenbehandlingRepository: AnkeITrygderettenbehandlingRepository,
    private val vedtakService: VedtakService,
    private val behandlingService: BehandlingService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val mottakService: MottakService,
    private val dokumentService: DokumentService,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        const val SYSTEMBRUKER = "SYSTEMBRUKER"
    }

    fun createAnkeITrygderettenbehandling(input: AnkeITrygderettenbehandlingInput): AnkeITrygderettenbehandling {
        val ankeITrygderettenbehandling = ankeITrygderettenbehandlingRepository.save(
            AnkeITrygderettenbehandling(
                klager = input.klager.copy(),
                sakenGjelder = input.sakenGjelder?.copy() ?: input.klager.toSakenGjelder(),
                ytelse = input.ytelse,
                type = input.type,
                kildeReferanse = input.kildeReferanse,
                dvhReferanse = input.dvhReferanse,
                sakFagsystem = input.sakFagsystem,
                sakFagsakId = input.sakFagsakId,
                mottattKlageinstans = input.sakMottattKlageinstans,
                tildeling = null,
                delbehandlinger = setOf(Delbehandling()),
                hjemler = if (input.innsendingsHjemler == null || input.innsendingsHjemler.isEmpty()) {
                    mutableSetOf(Hjemmel.MANGLER)
                } else {
                    input.innsendingsHjemler
                },
                sendtTilTrygderetten = input.sendtTilTrygderetten,
                kjennelseMottatt = null,
            )
        )
        logger.debug("Created ankeITrygderettenbehandling ${ankeITrygderettenbehandling.id}")

        if (input.registreringsHjemmelSet != null) {
            vedtakService.setHjemler(
                behandlingId = ankeITrygderettenbehandling.id,
                hjemler = input.registreringsHjemmelSet,
                utfoerendeSaksbehandlerIdent = SYSTEMBRUKER,
                systemUserContext = true,
            )
        }

        input.saksdokumenter.forEach {
            behandlingService.connectDokumentToBehandling(
                behandlingId = ankeITrygderettenbehandling.id,
                journalpostId = it.journalpostId,
                dokumentInfoId = it.dokumentInfoId,
                saksbehandlerIdent = SYSTEMBRUKER,
                systemUserContext = true,
            )
        }

        applicationEventPublisher.publishEvent(
            BehandlingEndretEvent(
                behandling = ankeITrygderettenbehandling,
                endringslogginnslag = emptyList()
            )
        )
        return ankeITrygderettenbehandling
    }

    fun createAnkeITrygderettenbehandling(input: OversendtAnkeITrygderettenV1) {
        mottakService.validateAnkeITrygderettenV1(input)
        val inputDocuments =
            dokumentService.createSaksdokumenterFromJournalpostIdSet(input.tilknyttedeJournalposter.map { it.journalpostId })
        createAnkeITrygderettenbehandling(
            input.createAnkeITrygderettenbehandlingInput(inputDocuments)
        )
    }
}