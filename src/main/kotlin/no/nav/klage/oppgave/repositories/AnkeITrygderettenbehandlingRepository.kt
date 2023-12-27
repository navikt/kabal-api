package no.nav.klage.oppgave.repositories

import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.oppgave.domain.klage.AnkeITrygderettenbehandling
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface AnkeITrygderettenbehandlingRepository : JpaRepository<AnkeITrygderettenbehandling, UUID> {
    @Query(
        """
            SELECT ait
            FROM AnkeITrygderettenbehandling ait
            WHERE ait.avsluttet IS NOT null            
            AND ait.fagsystem != :infotrygdFagsystem
            AND ait.sakenGjelder.partId.value = :partIdValue            
        """
    )
    fun getCompletedAnkeITrygderettenbehandlinger(
        partIdValue: String,
        infotrygdFagsystem: Fagsystem = Fagsystem.IT01
    ): List<AnkeITrygderettenbehandling>
}

