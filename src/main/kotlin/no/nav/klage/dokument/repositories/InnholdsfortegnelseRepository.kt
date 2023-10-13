package no.nav.klage.dokument.repositories

import no.nav.klage.dokument.domain.dokumenterunderarbeid.Innholdsfortegnelse
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Transactional
interface InnholdsfortegnelseRepository : JpaRepository<Innholdsfortegnelse, UUID> {

    fun findByHoveddokumentId(hoveddokumentId: UUID): Innholdsfortegnelse?
}