package no.nav.klage.dokument.repositories

import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeid
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Transactional
interface DokumentUnderArbeidRepository : JpaRepository<DokumentUnderArbeid, UUID> {

    fun findByBehandlingId(behandlingId: UUID): Set<DokumentUnderArbeid>

    fun findByBehandlingIdAndFerdigstiltIsNull(behandlingId: UUID): List<DokumentUnderArbeid>

    fun findByBehandlingIdAndMarkertFerdigIsNull(behandlingId: UUID): Set<DokumentUnderArbeid>
}