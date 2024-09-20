package no.nav.klage.dokument.repositories

import no.nav.klage.dokument.domain.dokumenterunderarbeid.OpplastetDokumentUnderArbeidAsHoveddokument
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Transactional
interface OpplastetDokumentUnderArbeidAsHoveddokumentRepository : JpaRepository<OpplastetDokumentUnderArbeidAsHoveddokument, UUID> {

    fun findByBehandlingIdAndDokarkivReferencesIsNotEmpty(behandlingId: UUID): Set<OpplastetDokumentUnderArbeidAsHoveddokument>

    fun findByMarkertFerdigNotNullAndFerdigstiltNull(): Set<OpplastetDokumentUnderArbeidAsHoveddokument>

    fun findByMarkertFerdigNotNullAndFerdigstiltNullAndBehandlingId(behandlingId: UUID): Set<OpplastetDokumentUnderArbeidAsHoveddokument>

    fun findByBehandlingIdAndMarkertFerdigNotNull(behandlingId: UUID): Set<OpplastetDokumentUnderArbeidAsHoveddokument>

    fun findByFerdigstiltIsLessThanAndMellomlagerIdIsNotNull(ferdigstiltBefore: LocalDateTime): List<OpplastetDokumentUnderArbeidAsHoveddokument>
}