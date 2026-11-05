package no.nav.klage.oppgave.repositories

import no.nav.klage.oppgave.domain.behandling.GjenopptakITrygderettenbehandlingFoer2027
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface GjenopptakITrygderettenbehandlingFoer2027Repository : JpaRepository<GjenopptakITrygderettenbehandlingFoer2027, UUID>
