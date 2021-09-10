package no.nav.klage.oppgave.service.distribusjon

import no.nav.klage.oppgave.clients.saf.graphql.Journalstatus
import no.nav.klage.oppgave.clients.saf.graphql.SafGraphQlClient
import no.nav.klage.oppgave.domain.klage.Klagebehandling
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setBrevMottakerFerdigstiltIJoark
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setJournalpostIdInBrevmottaker
import no.nav.klage.oppgave.exceptions.JournalpostFinalizationException
import no.nav.klage.oppgave.exceptions.JournalpostNotFoundException
import no.nav.klage.oppgave.gateway.JournalpostGateway
import no.nav.klage.oppgave.service.FileApiService
import no.nav.klage.oppgave.service.KlagebehandlingService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
@Transactional
class VedtakJournalfoeringService(
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val klagebehandlingService: KlagebehandlingService,
    private val fileApiService: FileApiService,
    private val journalpostGateway: JournalpostGateway,
    private val safClient: SafGraphQlClient,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
        const val SYSTEMBRUKER = "SYSTEMBRUKER" //TODO ??
        const val SYSTEM_JOURNALFOERENDE_ENHET = "9999"
    }

    fun opprettJournalpostForBrevMottaker(
        klagebehandlingId: UUID,
        vedtakId: UUID,
        brevMottakerId: UUID
    ): Klagebehandling {
        val klagebehandling = klagebehandlingService.getKlagebehandlingForUpdateBySystembruker(klagebehandlingId, null)

        val vedtak = klagebehandling.getVedtak(vedtakId)
        val brevMottaker = vedtak.getMottaker(brevMottakerId)
        val documentInStorage = fileApiService.getUploadedDocumentAsSystemUser(vedtak.mellomlagerId!!)
        if (brevMottaker.journalpostId == null) {
            val journalpostId = journalpostGateway.createJournalpostAsSystemUser(
                klagebehandling,
                documentInStorage,
                brevMottaker
            )

            val event =
                klagebehandling.setJournalpostIdInBrevmottaker(
                    vedtakId,
                    brevMottakerId,
                    journalpostId,
                    SYSTEMBRUKER
                )
            applicationEventPublisher.publishEvent(event)
        }
        return klagebehandling
    }

    fun ferdigstillJournalpostForBrevMottaker(
        klagebehandlingId: UUID,
        vedtakId: UUID,
        brevMottakerId: UUID
    ): Klagebehandling {
        val klagebehandling = klagebehandlingService.getKlagebehandlingForUpdateBySystembruker(klagebehandlingId, null)
        val vedtak = klagebehandling.getVedtak(vedtakId)
        val brevMottaker = vedtak.getMottaker(brevMottakerId)

        try {
            val journalpost = safClient.getJournalpostAsSystembruker(brevMottaker.journalpostId!!)
                ?: throw JournalpostNotFoundException("Journalpost med id ${brevMottaker.journalpostId} finnes ikke")
            if (journalpost.journalstatus != Journalstatus.FERDIGSTILT) {
                journalpostGateway.finalizeJournalpostAsSystemUser(brevMottaker.journalpostId!!, SYSTEM_JOURNALFOERENDE_ENHET)
            }
        }  catch (e: Exception) {
            logger.warn("Kunne ikke ferdigstille journalpost ${brevMottaker.journalpostId}", e)
            throw JournalpostFinalizationException("Klarte ikke å ferdigstille journalpost")
        }

        val event =
            klagebehandling.setBrevMottakerFerdigstiltIJoark(
                vedtakId,
                brevMottakerId,
                SYSTEMBRUKER
            )
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }
}