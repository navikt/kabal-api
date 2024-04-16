package no.nav.klage.oppgave.repositories

import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.domain.klage.Behandling
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.util.*

interface BehandlingRepository : JpaRepository<Behandling, UUID>, JpaSpecificationExecutor<Behandling> {

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

    fun findByIdAndAvsluttetIsNotNull(id: UUID): Behandling?

    fun findBySakenGjelderPartIdValueAndAvsluttetAvSaksbehandlerIsNullAndFeilregistreringIsNull(partIdValue: String): List<Behandling>

    @EntityGraph(attributePaths = ["saksdokumenter", "hjemler", "registreringshjemler", "medunderskriverHistorikk"])
    override fun findAll(
        specification: Specification<Behandling>,
        sort: Sort,
    ): List<Behandling>

}