package no.nav.klage.oppgave.repositories

import jakarta.persistence.QueryHint
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Utfall
import no.nav.klage.oppgave.domain.behandling.Behandling
import org.hibernate.jpa.HibernateHints.HINT_FETCH_SIZE
import org.springframework.data.jpa.repository.*
import java.util.*
import java.util.stream.Stream

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

    @Query(
        """
            select b
            from Behandling b
            left join fetch b.hjemler h
            where b.ferdigstilling is null
            and b.feilregistrering is null
        """
    )
    fun findByFerdigstillingIsNullAndFeilregistreringIsNullWithHjemler(): List<Behandling>

    fun findByTildelingEnhetAndFerdigstillingIsNullAndFeilregistreringIsNull(enhet: String): List<Behandling>

    fun findByTildelingIsNotNullAndFerdigstillingIsNullAndFeilregistreringIsNull(): List<Behandling>

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
            AND b.sakenGjelder.partId.value = :partIdValue            
            AND b.type IN :includedTypes
        """
    )
    fun getGjenopptaksmuligheter(
        partIdValue: String,
        includedTypes: List<Type> = listOf(
            Type.ANKE_I_TRYGDERETTEN,
            Type.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN,
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

    @QueryHints(QueryHint(name = HINT_FETCH_SIZE, value = "2000"))
    @Query(
        """
            select b
            from Behandling b
            left join fetch b.hjemler h
            left join fetch b.registreringshjemler rh
            order by b.id
        """
    )
    fun findAllForKapteinStreamed(): Stream<Behandling>

    @QueryHints(QueryHint(name = HINT_FETCH_SIZE, value = "2000"))
    @Query(
        """
            select b
            from Behandling b
            where b.feilregistrering is null
        """
    )
    fun findAllForAdminStreamed(): Stream<Behandling>

    @EntityGraph("Behandling.oppgaveProperties")
    @Query("select b from Behandling b where b.id = :id")
    fun findByIdForOppgave(id: UUID): Behandling

    @EntityGraph("Behandling.kapteinProperties")
    @Query("select b from Behandling b where b.id = :id")
    fun findByIdForKaptein(id: UUID): Behandling

}