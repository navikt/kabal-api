package no.nav.klage.dokument.repositories

import no.nav.klage.dokument.domain.dokumenterunderarbeid.OpplastetDokumentUnderArbeidAsHoveddokument
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Transactional
interface OpplastetDokumentUnderArbeidAsHoveddokumentRepository : JpaRepository<OpplastetDokumentUnderArbeidAsHoveddokument, UUID> {

    fun findByBehandlingId(behandlingId: UUID): Set<OpplastetDokumentUnderArbeidAsHoveddokument>

    fun findByBehandlingIdAndMarkertFerdigIsNull(behandlingId: UUID): Set<OpplastetDokumentUnderArbeidAsHoveddokument>

    fun findByMarkertFerdigNotNullAndFerdigstiltNull(): Set<OpplastetDokumentUnderArbeidAsHoveddokument>
}