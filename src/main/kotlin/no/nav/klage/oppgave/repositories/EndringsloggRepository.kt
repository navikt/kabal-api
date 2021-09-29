package no.nav.klage.oppgave.repositories

import no.nav.klage.oppgave.domain.klage.Endringslogginnslag
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface EndringsloggRepository : JpaRepository<Endringslogginnslag, UUID> {

    fun findByKlagebehandlingIdOrderByTidspunktDesc(klagebehandlingId: UUID): List<Endringslogginnslag>
}