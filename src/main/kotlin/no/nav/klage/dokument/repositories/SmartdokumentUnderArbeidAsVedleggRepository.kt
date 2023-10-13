package no.nav.klage.dokument.repositories

import no.nav.klage.dokument.domain.dokumenterunderarbeid.SmartdokumentUnderArbeidAsVedlegg
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Transactional
interface SmartdokumentUnderArbeidAsVedleggRepository : JpaRepository<SmartdokumentUnderArbeidAsVedlegg, UUID> {

    fun findByParentId(dokumentId: UUID): Set<SmartdokumentUnderArbeidAsVedlegg>

    fun findByBehandlingIdAndMarkertFerdigIsNull(behandlingId: UUID): SortedSet<SmartdokumentUnderArbeidAsVedlegg>
}