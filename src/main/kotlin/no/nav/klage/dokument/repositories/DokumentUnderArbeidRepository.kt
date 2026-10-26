package no.nav.klage.dokument.repositories

import jakarta.persistence.LockModeType
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeid
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Transactional
interface DokumentUnderArbeidRepository : JpaRepository<DokumentUnderArbeid, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DokumentUnderArbeid d where d.id = :id")
    fun findByIdForUpdate(
        @Param("id") id: UUID,
    ): DokumentUnderArbeid?

    fun findByBehandlingId(behandlingId: UUID): Set<DokumentUnderArbeid>

    fun findByBehandlingIdAndFerdigstiltIsNull(behandlingId: UUID): List<DokumentUnderArbeid>

    fun findByBehandlingIdAndMarkertFerdigIsNull(behandlingId: UUID): Set<DokumentUnderArbeid>
}
