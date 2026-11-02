package no.nav.klage.oppgave.repositories

import no.nav.klage.oppgave.domain.behandling.GjenopptaksbehandlingEtter2027
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface GjenopptaksbehandlingEtter2027Repository : JpaRepository<GjenopptaksbehandlingEtter2027, UUID>
