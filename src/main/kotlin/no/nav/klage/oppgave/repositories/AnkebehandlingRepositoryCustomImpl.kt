package no.nav.klage.oppgave.repositories

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.oppgave.domain.klage.Ankebehandling
import org.springframework.stereotype.Repository

@Repository
class AnkebehandlingRepositoryCustomImpl : AnkebehandlingRepositoryCustom {

    @PersistenceContext
    lateinit var entityManager: EntityManager

    override fun getCompletedAnkebehandlinger(partIdValue: String): List<Ankebehandling> {
        return entityManager.createQuery(
            """
            SELECT a
            FROM Ankebehandling a
            WHERE a.avsluttet != null            
            AND a.fagsystem != :infotrygdFagsystem
            AND a.sakenGjelder.partId.value = :sakenGjelder            
        """,
            Ankebehandling::class.java
        )
            .setParameter("infotrygdFagsystem", Fagsystem.IT01)
            .setParameter("sakenGjelder", partIdValue)
            .resultList
    }
}