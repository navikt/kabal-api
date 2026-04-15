package no.nav.klage.oppgave.repositories

import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.oppgave.domain.SakPersongalleri
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface SakPersongalleriRepository : JpaRepository<SakPersongalleri, UUID> {

    fun findByFagsystemAndFagsakId(fagsystem: Fagsystem, fagsakId: String): List<SakPersongalleri>
}