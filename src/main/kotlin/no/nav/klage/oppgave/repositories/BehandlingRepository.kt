package no.nav.klage.oppgave.repositories

import jakarta.persistence.LockModeType
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.domain.klage.Behandling
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import java.util.*

interface BehandlingRepository : JpaRepository<Behandling, UUID> {

    fun findByFagsystemAndKildeReferanseAndFeilregistreringIsNullAndType(
        fagsystem: Fagsystem,
        kildeReferanse: String,
        type: Type,
    ): List<Behandling>

    fun findByAvsluttetIsNullAndAvsluttetAvSaksbehandlerIsNotNullAndFeilregistreringIsNull(): List<Behandling>

    fun findByAvsluttetAvSaksbehandlerIsNull(): List<Behandling>

    fun findByAvsluttetAvSaksbehandlerIsNullAndFeilregistreringIsNull(): List<Behandling>

    @EntityGraph(attributePaths = ["saksdokumenter", "hjemler", "registreringshjemler", "medunderskriverHistorikk"])
    fun findByTildelingEnhetAndAvsluttetAvSaksbehandlerIsNullAndFeilregistreringIsNull(enhet: String): List<Behandling>

    @Deprecated("See getOne")
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    override fun getOne(id: UUID): Behandling

    fun findByIdAndAvsluttetIsNotNull(id: UUID): Behandling?
}