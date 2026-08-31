package no.nav.klage.oppgave.repositories

import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingEtter2027
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface AnkeITrygderettenbehandlingEtter2027Repository : JpaRepository<AnkeITrygderettenbehandlingEtter2027, UUID>
