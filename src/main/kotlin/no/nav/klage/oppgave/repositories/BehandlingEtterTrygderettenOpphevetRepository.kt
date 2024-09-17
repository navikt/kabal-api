package no.nav.klage.oppgave.repositories

import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.oppgave.domain.klage.BehandlingEtterTrygderettenOpphevet
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface BehandlingEtterTrygderettenOpphevetRepository : JpaRepository<BehandlingEtterTrygderettenOpphevet, UUID> {

    @EntityGraph(attributePaths = ["hjemler"])
    @Query(
        """
            SELECT b
            FROM BehandlingEtterTrygderettenOpphevet b
            WHERE b.ferdigstilling.avsluttet IS NOT null            
            AND b.fagsystem != :infotrygdFagsystem
            AND b.sakenGjelder.partId.value = :partIdValue
        """
    )
    fun getCompletedBehandlinger(
        partIdValue: String,
        infotrygdFagsystem: Fagsystem = Fagsystem.IT01,
    ): List<BehandlingEtterTrygderettenOpphevet>
}