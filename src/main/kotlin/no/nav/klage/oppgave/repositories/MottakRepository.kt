package no.nav.klage.oppgave.repositories

import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.oppgave.domain.klage.Mottak
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface MottakRepository : JpaRepository<Mottak, UUID> {

    fun existsByKildesystemAndKildeReferanse(kildesystem: Fagsystem, kildeReferanse: String): Boolean

}
