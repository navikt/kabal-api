package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.repositories.KlagebehandlingRepository
import no.nav.klage.oppgave.util.getLogger
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class StatistikkTilDVHResenderService(
    private val klagebehandlingRepository: KlagebehandlingRepository,
    private val statistikkTilDVHService: StatistikkTilDVHService
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @Transactional(readOnly = true)
    fun resendAllKlagebehandlinger() {
        var pageable: Pageable =
            PageRequest.of(0, 50, Sort.by("created").descending())
        do {
            val page = klagebehandlingRepository.findAll(pageable)
            page.content.map { klagebehandling ->
                runCatching {
                    statistikkTilDVHService.process(klagebehandling)
                }.onFailure {
                    logger.warn("Failed to resend klagebehandling to DVH", it)
                }
            }
            pageable = page.nextPageable();
        } while (pageable.isPaged)
    }
}