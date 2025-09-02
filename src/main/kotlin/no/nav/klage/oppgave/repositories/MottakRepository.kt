package no.nav.klage.oppgave.repositories

import no.nav.klage.oppgave.domain.mottak.Mottak
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface MottakRepository : JpaRepository<Mottak, UUID> {

    @Query(
        """
            from Mottak
            where sakenGjelder.partId.value = :fnr
            or (sakenGjelder is null and klager.partId.value = :fnr)
        """
    )
    fun findBySakenGjelderOrKlager(fnr: String): List<Mottak>
}
