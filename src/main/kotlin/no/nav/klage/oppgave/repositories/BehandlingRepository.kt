package no.nav.klage.oppgave.repositories

import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Utfall
import no.nav.klage.oppgave.domain.behandling.Behandling
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
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

    fun findByFeilregistreringIsNull(): List<Behandling>

    fun findByFerdigstillingIsNull(): List<Behandling>

    fun findByFerdigstillingIsNullAndFeilregistreringIsNull(): List<Behandling>

    fun findByTildelingEnhetAndFerdigstillingIsNullAndFeilregistreringIsNull(enhet: String): List<Behandling>

    fun findByIdAndFerdigstillingAvsluttetIsNotNull(id: UUID): Behandling?

    fun findBySakenGjelderPartIdValueAndFerdigstillingIsNullAndFeilregistreringIsNull(partIdValue: String): List<Behandling>

    fun findBySakenGjelderPartIdValueAndFeilregistreringIsNull(partIdValue: String): List<Behandling>

    fun findBySakenGjelderPartIdValue(partIdValue: String): List<Behandling>

    fun findByFagsakId(fagsakId: String): List<Behandling>

    @EntityGraph("Behandling.commonProperties")
    @Query("select b from Behandling b where b.id = :id")
    fun findByIdEager(id: UUID): Behandling

    @EntityGraph(attributePaths = ["hjemler"])
    @Query(
        """
            SELECT b
            FROM Behandling b
            WHERE b.ferdigstilling.avsluttet IS NOT null
            AND b.sakenGjelder.partId.value = :partIdValue
            AND b.utfall NOT IN :utfallWithoutAnkemulighet
            AND b.type NOT IN :excludedTypes
        """
    )
    fun getOmgjoeringskravmuligheter(
        partIdValue: String,
        utfallWithoutAnkemulighet: List<Utfall> = listOf(
            Utfall.INNSTILLING_AVVIST,
            Utfall.INNSTILLING_STADFESTELSE,
        ),
        excludedTypes: List<Type> = listOf(
            Type.ANKE_I_TRYGDERETTEN,
        )
    ): List<Behandling>

    @EntityGraph(attributePaths = ["hjemler"])
    @Query(
        """
            SELECT b
            FROM Behandling b
            WHERE b.ferdigstilling.avsluttet IS NOT null            
            AND b.fagsystem != :infotrygdFagsystem
            AND b.sakenGjelder.partId.value = :partIdValue
            AND b.utfall NOT IN :utfallWithoutAnkemulighet
            AND b.type NOT IN :excludedTypes
        """
    )
    fun getAnkemuligheter(
        partIdValue: String,
        infotrygdFagsystem: Fagsystem = Fagsystem.IT01,
        utfallWithoutAnkemulighet: List<Utfall> = listOf(
            Utfall.INNSTILLING_AVVIST,
            Utfall.INNSTILLING_STADFESTELSE,
        ),
        excludedTypes: List<Type> = listOf(
            Type.ANKE_I_TRYGDERETTEN,
        )
    ): List<Behandling>

    fun findByTilbakekrevingIsFalse(): List<Behandling>

    @Query(
        """
            SELECT b.id
            FROM Behandling b
        """
    )
    fun findAllIdListForKaptein(pageable: Pageable): Slice<UUID>

    @Query(
        """
            SELECT b
            FROM Behandling b
            left join fetch b.hjemler h
            left join fetch b.registreringshjemler rh
            where b.id in :behandlingIdList
        """
    )
    fun findAllForKapteinWithHjemler(behandlingIdList: List<UUID>): List<Behandling>

}