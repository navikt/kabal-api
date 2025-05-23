package no.nav.klage.oppgave.repositories

import no.nav.klage.oppgave.domain.klage.ForlengetBehandlingstidDraft
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ForlengetBehandlingstidDraftRepository : JpaRepository<ForlengetBehandlingstidDraft, UUID>