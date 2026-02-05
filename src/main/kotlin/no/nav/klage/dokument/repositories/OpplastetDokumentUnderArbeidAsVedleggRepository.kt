package no.nav.klage.dokument.repositories

import no.nav.klage.dokument.domain.dokumenterunderarbeid.OpplastetDokumentUnderArbeidAsVedlegg
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Transactional
interface OpplastetDokumentUnderArbeidAsVedleggRepository : JpaRepository<OpplastetDokumentUnderArbeidAsVedlegg, UUID> {

    @EntityGraph(attributePaths = ["dokarkivReferences"])
    fun findByParentId(dokumentId: UUID): Set<OpplastetDokumentUnderArbeidAsVedlegg>

    fun findByFerdigstiltIsLessThanAndMellomlagerIdIsNotNull(ferdigstiltBefore: LocalDateTime): List<OpplastetDokumentUnderArbeidAsVedlegg>
}
