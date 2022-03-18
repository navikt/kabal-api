package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.oppgave.clients.kaka.KakaApiGateway
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.domain.klage.Ankebehandling
import no.nav.klage.oppgave.domain.klage.Delbehandling
import no.nav.klage.oppgave.domain.klage.Mottak
import no.nav.klage.oppgave.domain.klage.MottakHjemmel
import no.nav.klage.oppgave.repositories.AnkebehandlingRepository
import no.nav.klage.oppgave.repositories.KlagebehandlingRepository
import no.nav.klage.oppgave.util.getLogger
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import javax.transaction.Transactional

@Service
@Transactional
class AnkebehandlingService(
    private val ankebehandlingRepository: AnkebehandlingRepository,
    private val klagebehandlingRepository: KlagebehandlingRepository,
    private val kakaApiGateway: KakaApiGateway,
    private val dokumentService: DokumentService,
    private val vedtakService: VedtakService,
    private val behandlingService: BehandlingService,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        const val SYSTEMBRUKER = "SYSTEMBRUKER"
    }

    fun createAnkebehandlingFromMottak(mottak: Mottak): Ankebehandling {
        val ankebehandling = ankebehandlingRepository.save(
            Ankebehandling(
                klager = mottak.klager.copy(),
                sakenGjelder = mottak.sakenGjelder?.copy() ?: mottak.klager.toSakenGjelder(),
                ytelse = mottak.ytelse,
                type = mottak.type,
                kildeReferanse = mottak.kildeReferanse,
                dvhReferanse = mottak.dvhReferanse,
                sakFagsystem = mottak.sakFagsystem,
                sakFagsakId = mottak.sakFagsakId,
                innsendt = mottak.innsendtDato,
                mottattKlageinstans = mottak.sakMottattKaDato,
                tildeling = null,
                frist = mottak.generateFrist(),
                mottakId = mottak.id,
                delbehandlinger = setOf(Delbehandling()),
                saksdokumenter = dokumentService.createSaksdokumenterFromJournalpostIdSet(mottak.mottakDokument.map { it.journalpostId }),
                kakaKvalitetsvurderingId = kakaApiGateway.createKvalitetsvurdering(),
                hjemler = createHjemmelSetFromMottak(mottak.hjemler),
                kildesystem = mottak.kildesystem,
                klageBehandlendeEnhet = mottak.forrigeBehandlendeEnhet,
                klagebehandlingId = mottak.forrigeBehandlingId,
            )
        )
        logger.debug("Created ankebehandling ${ankebehandling.id} for mottak ${mottak.id}")

        if (mottak.forrigeBehandlingId != null) {
            logger.debug("Getting registreringshjemler from klagebehandling ${mottak.forrigeBehandlingId} for ankebehandling ${ankebehandling.id}")
            val klagebehandling = klagebehandlingRepository.getById(mottak.forrigeBehandlingId)
            vedtakService.setHjemler(
                behandlingId = ankebehandling.id,
                hjemler = klagebehandling.currentDelbehandling().hjemler,
                utfoerendeSaksbehandlerIdent = SYSTEMBRUKER,
            )

            val klagebehandlingDokumenter = klagebehandling.saksdokumenter

            logger.debug("Adding saksdokumenter from klagebehandling ${mottak.forrigeBehandlingId} to ankebehandling ${ankebehandling.id}")
            klagebehandlingDokumenter.forEach{
                behandlingService.connectDokumentToBehandling(
                    behandlingId = ankebehandling.id,
                    journalpostId = it.journalpostId,
                    dokumentInfoId = it.dokumentInfoId,
                    saksbehandlerIdent = SYSTEMBRUKER
                )
            }
        }

        applicationEventPublisher.publishEvent(
            BehandlingEndretEvent(
                behandling = ankebehandling,
                endringslogginnslag = emptyList()
            )
        )
        return ankebehandling
    }

    private fun createHjemmelSetFromMottak(hjemler: Set<MottakHjemmel>?): MutableSet<Hjemmel> =
        if (hjemler == null || hjemler.isEmpty()) {
            mutableSetOf(Hjemmel.MANGLER)
        } else {
            hjemler.map { Hjemmel.of(it.hjemmelId) }.toMutableSet()
        }
}