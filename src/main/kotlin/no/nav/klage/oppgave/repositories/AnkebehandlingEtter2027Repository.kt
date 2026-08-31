package no.nav.klage.oppgave.repositories

import no.nav.klage.oppgave.domain.behandling.AnkebehandlingEtter2027
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.UUID

@Repository
interface AnkebehandlingEtter2027Repository : JpaRepository<AnkebehandlingEtter2027, UUID> {
    fun findByKakaKvalitetsvurderingVersionIs(version: Int): List<AnkebehandlingEtter2027>

    @Query(
        """
        FROM AnkebehandlingEtter2027 
        WHERE sakenGjelder.partId.value = :sakenGjelder 
        AND ferdigstilling.avsluttet IS NOT NULL 
        AND feilregistrering IS NULL 
        AND kildeReferanse = :kildeReferanse
        AND ferdigstilling.avsluttetAvSaksbehandler < :dateLimit
        ORDER BY ferdigstilling.avsluttetAvSaksbehandler DESC
        """,
    )
    fun findPreviousAnker(
        sakenGjelder: String,
        kildeReferanse: String,
        dateLimit: LocalDateTime,
    ): List<AnkebehandlingEtter2027>
}
