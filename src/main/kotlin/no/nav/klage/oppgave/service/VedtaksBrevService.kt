package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.domain.vedtaksbrev.*
import no.nav.klage.oppgave.repositories.BrevElementRepository
import no.nav.klage.oppgave.repositories.VedtaksBrevRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
@Transactional
class VedtaksBrevService(
    private val vedtaksBrevRepository: VedtaksBrevRepository,
    private val brevElementRepository: BrevElementRepository
) {
    fun createVedtaksBrev(vedtaksBrevView: VedtaksBrevView): VedtaksBrev {
        val newVedtaksBrev = VedtaksBrev(
            klagebehandlingId = vedtaksBrevView.klagebehandlingId,
            brevMal = vedtaksBrevView.brevMal
        )
        newVedtaksBrev.brevElements = newVedtaksBrev.brevMal.elementOrder.map {
            it.generateDefaultBrevElement(newVedtaksBrev.id)
        }
        vedtaksBrevRepository.save(newVedtaksBrev)
        return newVedtaksBrev
    }

    fun getVedtaksBrev(brevId: UUID): VedtaksBrev {
        return vedtaksBrevRepository.getOne(brevId)
    }

    fun getVedtaksBrevByKlagebehandlingId(klagebehandlingId: UUID): List<VedtaksBrev> {
        return vedtaksBrevRepository.findByKlagebehandlingId(klagebehandlingId)
    }

    fun deleteVedtaksbrev(brevId: UUID) {
        vedtaksBrevRepository.deleteById(brevId)
    }

    fun updateBrevElement(brevId: UUID, inputElement: BrevElementView): BrevElement {
        val existingElement = brevElementRepository.findByBrevIdAndKey(brevId, inputElement.key)
        existingElement.updateFields(inputElement)
        return existingElement
    }
}