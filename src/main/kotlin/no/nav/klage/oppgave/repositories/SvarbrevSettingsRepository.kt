package no.nav.klage.oppgave.repositories

import no.nav.klage.oppgave.domain.klage.SvarbrevSettings
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface SvarbrevSettingsRepository : JpaRepository<SvarbrevSettings, UUID>