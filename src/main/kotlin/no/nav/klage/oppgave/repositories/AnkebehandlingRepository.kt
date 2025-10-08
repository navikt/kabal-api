package no.nav.klage.oppgave.repositories

import no.nav.klage.kodeverk.Utfall
import no.nav.klage.oppgave.domain.behandling.Ankebehandling
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

@Repository
interface AnkebehandlingRepository : JpaRepository<Ankebehandling, UUID> {

    fun findBySourceBehandlingIdAndFeilregistreringIsNull(sourceBehandlingId: UUID): List<Ankebehandling>

    fun findByFerdigstillingAvsluttetIsNotNullAndFeilregistreringIsNullAndUtfallIn(utfallSet: Set<Utfall>): List<Ankebehandling>

    fun findByKakaKvalitetsvurderingVersionIs(version: Int): List<Ankebehandling>

    @Query("""
        FROM Ankebehandling 
        WHERE sakenGjelder.partId.value = :sakenGjelder 
        AND ferdigstilling.avsluttet IS NOT NULL 
        AND feilregistrering IS NULL 
        AND kildeReferanse = :kildeReferanse
        AND ferdigstilling.avsluttetAvSaksbehandler < :dateLimit
        ORDER BY ferdigstilling.avsluttetAvSaksbehandler DESC
        """
    )
    fun findPreviousAnker(sakenGjelder: String, kildeReferanse: String, dateLimit: LocalDateTime): List<Ankebehandling>
}