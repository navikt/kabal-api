package no.nav.klage.oppgave.repositories

import no.nav.klage.oppgave.domain.saksbehandler.ValgtEnhet
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ValgtEnhetRepository : JpaRepository<ValgtEnhet, String>
