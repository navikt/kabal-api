package no.nav.klage.oppgave.repositories

import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.oppgave.domain.klage.Klagebehandling
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*


@Repository
interface KlagebehandlingRepository : JpaRepository<Klagebehandling, UUID> {
    @EntityGraph("Behandling.full")
    fun findByMottakId(mottakId: UUID): Klagebehandling?
    @EntityGraph("Behandling.full")
    fun findByKildeReferanseAndYtelseAndFeilregistreringIsNull(kildeReferanse: String, ytelse: Ytelse): Klagebehandling?
    @EntityGraph("Behandling.full")
    fun findByKakaKvalitetsvurderingVersionIs(version: Int): List<Klagebehandling>

    @EntityGraph("Behandling.full")
    @Query(
        """
            SELECT k
            FROM Klagebehandling k
            WHERE k.avsluttet IS NOT null            
            AND k.fagsystem != :infotrygdFagsystem
            AND k.sakenGjelder.partId.value = :partIdValue            
        """
    )
    fun getCompletedKlagebehandlinger(
        partIdValue: String,
        infotrygdFagsystem: Fagsystem = Fagsystem.IT01
    ): List<Klagebehandling>
}
