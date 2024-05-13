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

    @EntityGraph("Behandling.full")
    fun findByFagsystemAndKildeReferanseAndFeilregistreringIsNullAndType(
        fagsystem: Fagsystem,
        kildeReferanse: String,
        type: Type,
    ): List<Behandling>

    @EntityGraph("Behandling.full")
    fun findByAvsluttetIsNullAndAvsluttetAvSaksbehandlerIsNotNullAndFeilregistreringIsNull(): List<Behandling>

    @EntityGraph("Behandling.full")
    fun findByAvsluttetAvSaksbehandlerIsNull(): List<Behandling>

    @EntityGraph("Behandling.full")
    fun findByAvsluttetAvSaksbehandlerIsNullAndFeilregistreringIsNull(): List<Behandling>

    @EntityGraph("Behandling.full")
    fun findByTildelingEnhetAndAvsluttetAvSaksbehandlerIsNullAndFeilregistreringIsNull(enhet: String): List<Behandling>

    @EntityGraph("Behandling.full")
    fun findByIdAndAvsluttetIsNotNull(id: UUID): Behandling?

    @EntityGraph("Behandling.full")
    fun findBySakenGjelderPartIdValueAndAvsluttetAvSaksbehandlerIsNullAndFeilregistreringIsNull(partIdValue: String): List<Behandling>

    override fun findAll(
        specification: Specification<Behandling>,
        sort: Sort,
    ): List<Behandling>

    @EntityGraph("Behandling.full")
    override fun findById(id: UUID): Optional<Behandling>

}