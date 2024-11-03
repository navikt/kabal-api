package no.nav.klage.oppgave.repositories

import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.domain.klage.Behandling
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import java.util.*

interface BehandlingRepository : JpaRepository<Behandling, UUID>, JpaSpecificationExecutor<Behandling> {

    fun findByFagsystemAndKildeReferanseAndFeilregistreringIsNullAndType(
        fagsystem: Fagsystem,
        kildeReferanse: String,
        type: Type,
    ): List<Behandling>

    fun findByGosysOppgaveIdAndFeilregistreringIsNullAndFerdigstillingIsNull(
        gosysOppgaveId: Long,
    ): List<Behandling>

    fun findByFerdigstillingAvsluttetIsNullAndFerdigstillingAvsluttetAvSaksbehandlerIsNotNullAndFeilregistreringIsNull(): List<Behandling>

    fun findByFerdigstillingIsNull(): List<Behandling>

    fun findByFerdigstillingIsNullAndFeilregistreringIsNull(): List<Behandling>

    fun findByTildelingEnhetAndFerdigstillingIsNullAndFeilregistreringIsNull(enhet: String): List<Behandling>

    fun findByIdAndFerdigstillingAvsluttetIsNotNull(id: UUID): Behandling?

    fun findBySakenGjelderPartIdValueAndFerdigstillingIsNullAndFeilregistreringIsNull(partIdValue: String): List<Behandling>

    fun findByFagsakId(fagsakId: String): List<Behandling>

    @EntityGraph("Behandling.commonProperties")
    @Query("select b from Behandling b where b.id = :id")
    fun findByIdEager(id: UUID): Behandling

    fun findByTilbakekrevingIsFalse(): List<Behandling>

}