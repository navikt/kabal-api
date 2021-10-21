package no.nav.klage.oppgave.repositories

import no.nav.klage.oppgave.domain.saksbehandler.Innstillinger
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface InnstillingerRepository : JpaRepository<Innstillinger, String>
