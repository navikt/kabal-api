package no.nav.klage.oppgave.repositories

import no.nav.klage.oppgave.domain.behandling.GjenopptakITrygderettenbehandlingEtter2027
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface GjenopptakITrygderettenbehandlingEtter2027Repository : JpaRepository<GjenopptakITrygderettenbehandlingEtter2027, UUID>
