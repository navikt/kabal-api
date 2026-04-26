package no.nav.klage.oppgave.repositories

import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.oppgave.domain.SakPersongalleri
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface SakPersongalleriRepository : JpaRepository<SakPersongalleri, UUID> {

    fun findByFagsystemAndFagsakId(fagsystem: Fagsystem, fagsakId: String): List<SakPersongalleri>

    fun findByFoedselsnummer(foedselsnummer: String): List<SakPersongalleri>

    @Query("SELECT DISTINCT sp.foedselsnummer FROM SakPersongalleri sp")
    fun findDistinctFoedselsnummer(): Set<String>
}