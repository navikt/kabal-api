package no.nav.klage.oppgave.repositories

import no.nav.klage.kodeverk.Utfall
import no.nav.klage.oppgave.domain.klage.Ankebehandling
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface AnkebehandlingRepository : JpaRepository<Ankebehandling, UUID> {
    fun findByKlagebehandlingIdAndFeilregistreringIsNull(klagebehandlingId: UUID): Ankebehandling?
    fun findByAvsluttetIsNotNullAndFeilregistreringIsNullAndUtfallIn(utfallSet: Set<Utfall>): List<Ankebehandling>
    fun findByMottakId(mottakId: UUID): Ankebehandling?
    fun findByKakaKvalitetsvurderingVersionIs(version: Int): List<Ankebehandling>
}