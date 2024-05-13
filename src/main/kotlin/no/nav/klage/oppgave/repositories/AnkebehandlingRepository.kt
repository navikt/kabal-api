package no.nav.klage.oppgave.repositories

import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.Utfall
import no.nav.klage.oppgave.domain.klage.Ankebehandling
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface AnkebehandlingRepository : JpaRepository<Ankebehandling, UUID> {
    @EntityGraph("Behandling.full")
    fun findBySourceBehandlingIdAndFeilregistreringIsNull(sourceBehandlingId: UUID): List<Ankebehandling>
    @EntityGraph("Behandling.full")
    fun findByAvsluttetIsNotNullAndFeilregistreringIsNullAndUtfallIn(utfallSet: Set<Utfall>): List<Ankebehandling>
    @EntityGraph("Behandling.full")
    fun findByMottakId(mottakId: UUID): Ankebehandling?
    @EntityGraph("Behandling.full")
    fun findByKakaKvalitetsvurderingVersionIs(version: Int): List<Ankebehandling>

    @EntityGraph("Behandling.full")
    @Query(
        """
            SELECT a
            FROM Ankebehandling a
            WHERE a.avsluttet IS NOT null            
            AND a.fagsystem != :infotrygdFagsystem
            AND a.sakenGjelder.partId.value = :partIdValue
            AND a.utfall NOT IN :utfallWithoutAnkemulighet
        """
    )
    fun getCompletedAnkebehandlinger(
        partIdValue: String,
        infotrygdFagsystem: Fagsystem = Fagsystem.IT01,
        utfallWithoutAnkemulighet: List<Utfall> = listOf(
            Utfall.INNSTILLING_AVVIST,
            Utfall.INNSTILLING_STADFESTELSE,
        )
    ): List<Ankebehandling>
}