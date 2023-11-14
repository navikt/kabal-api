package no.nav.klage.oppgave.repositories

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.oppgave.domain.klage.Klagebehandling
import org.springframework.stereotype.Repository

@Repository
class KlagebehandlingRepositoryCustomImpl : KlagebehandlingRepositoryCustom {

    @PersistenceContext
    lateinit var entityManager: EntityManager

    //TODO: Vurder sjekken fra Ankebehandling.
    override fun getCompletedKlagebehandlinger(partIdValue: String): List<Klagebehandling> {
        return entityManager.createQuery(
            """
            SELECT k
            FROM Klagebehandling k
            WHERE k.avsluttet != null            
            AND k.fagsystem != :infotrygdFagsystem
            AND k.sakenGjelder.partId.value = :sakenGjelder            
        """,
            Klagebehandling::class.java
        )
            .setParameter("infotrygdFagsystem", Fagsystem.IT01)
            .setParameter("sakenGjelder", partIdValue)
            .resultList
    }

}