package no.nav.klage.oppgave.repositories

import no.nav.klage.oppgave.domain.PersonProtection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface PersonProtectionRepository : JpaRepository<PersonProtection, UUID> {

    fun findByFoedselsnummer(foedselsnummer: String): PersonProtection?
}