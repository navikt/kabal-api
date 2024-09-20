package no.nav.klage.oppgave.service

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.klage.dokument.repositories.OpplastetDokumentUnderArbeidAsHoveddokumentRepository
import no.nav.klage.dokument.repositories.OpplastetDokumentUnderArbeidAsVedleggRepository
import no.nav.klage.dokument.repositories.SmartdokumentUnderArbeidAsHoveddokumentRepository
import no.nav.klage.dokument.repositories.SmartdokumentUnderArbeidAsVedleggRepository
import no.nav.klage.dokument.service.MellomlagerService
import no.nav.klage.oppgave.util.getLogger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@Service
class CleanupMellomlagerService(
    private val mellomlagerService: MellomlagerService,
    private val opplastetDokumentUnderArbeidAsHoveddokumentRepository: OpplastetDokumentUnderArbeidAsHoveddokumentRepository,
    private val opplastetDokumentUnderArbeidAsVedleggRepository: OpplastetDokumentUnderArbeidAsVedleggRepository,
    private val smartdokumentUnderArbeidAsHoveddokumentRepository: SmartdokumentUnderArbeidAsHoveddokumentRepository,
    private val smartdokumentUnderArbeidAsVedleggRepository: SmartdokumentUnderArbeidAsVedleggRepository,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @Scheduled(timeUnit = TimeUnit.MINUTES, fixedDelay = 60, initialDelay = 10)
    @SchedulerLock(name = "cleanupFinalizedDUAs")
    @Transactional
    fun cleanupFinalizedDUAs() {
        val oneWeekAgo = LocalDateTime.now().minusWeeks(1)
        logger.debug("cleanupFinalizedDUAs, getting candidates finalized before {}.", oneWeekAgo)

        val candidates = opplastetDokumentUnderArbeidAsHoveddokumentRepository.findByFerdigstiltIsLessThanAndMellomlagerIdIsNotNull(ferdigstiltBefore = oneWeekAgo) +
                opplastetDokumentUnderArbeidAsVedleggRepository.findByFerdigstiltIsLessThanAndMellomlagerIdIsNotNull(ferdigstiltBefore = oneWeekAgo) +
                smartdokumentUnderArbeidAsHoveddokumentRepository.findByFerdigstiltIsLessThanAndMellomlagerIdIsNotNull(ferdigstiltBefore = oneWeekAgo) +
                smartdokumentUnderArbeidAsVedleggRepository.findByFerdigstiltIsLessThanAndMellomlagerIdIsNotNull(ferdigstiltBefore = oneWeekAgo)

        logger.debug("cleanupFinalizedDUAs, found {} candidates.", candidates.size)

        candidates.forEach { candidate ->
            mellomlagerService.deleteDocument(mellomlagerId = candidate.mellomlagerId!!, systemContext = true)
            candidate.mellomlagerId = null
        }

        logger.debug("Finished cleanupFinalizedDUAs.")
    }
}