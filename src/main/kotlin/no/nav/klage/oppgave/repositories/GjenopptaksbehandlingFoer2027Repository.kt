package no.nav.klage.oppgave.repositories

import no.nav.klage.oppgave.domain.behandling.GjenopptaksbehandlingFoer2027
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface GjenopptaksbehandlingFoer2027Repository : JpaRepository<GjenopptaksbehandlingFoer2027, UUID>
