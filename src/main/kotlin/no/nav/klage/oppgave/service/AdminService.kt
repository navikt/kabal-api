package no.nav.klage.oppgave.service

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class AdminService(private val indexService: IndexService) {

    companion object {
        private const val TWO_SECONDS = 2000L
    }

    fun syncEsWithDb() {
        indexService.reindexAllKlagebehandlinger()
        Thread.sleep(TWO_SECONDS)
        indexService.findAndLogOutOfSyncKlagebehandlinger()
    }

    fun deleteAllInES() {
        indexService.deleteAllKlagebehandlinger()
        Thread.sleep(TWO_SECONDS)
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "Europe/Paris")
    fun findAndLogOutOfSyncKlagebehandlinger() =
        indexService.findAndLogOutOfSyncKlagebehandlinger()

}