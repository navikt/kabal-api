package no.nav.klage.oppgave.repositories

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.oppgave.domain.klage.AnkeITrygderettenbehandling
import org.springframework.stereotype.Repository

@Repository
class AnkeITrygderettenbehandlingRepositoryCustomImpl : AnkeITrygderettenbehandlingRepositoryCustom {

    @PersistenceContext
    lateinit var entityManager: EntityManager

    override fun getCompletedAnkeITrygderettenbehandlinger(partIdValue: String): List<AnkeITrygderettenbehandling> {
        return entityManager.createQuery(
            """
            SELECT ait
            FROM AnkeITrygderettenbehandling ait
            WHERE ait.avsluttet != null            
            AND ait.fagsystem != :infotrygdFagsystem
            AND ait.sakenGjelder.partId.value = :sakenGjelder            
        """,
            AnkeITrygderettenbehandling::class.java
        )
            .setParameter("infotrygdFagsystem", Fagsystem.IT01)
            .setParameter("sakenGjelder", partIdValue)
            .resultList
    }
}