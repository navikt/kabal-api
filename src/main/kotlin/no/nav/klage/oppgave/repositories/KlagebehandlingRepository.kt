package no.nav.klage.oppgave.repositories

import no.nav.klage.oppgave.domain.klage.Klagebehandling
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.stereotype.Repository
import java.util.*
import javax.persistence.LockModeType


@Repository
interface KlagebehandlingRepository : JpaRepository<Klagebehandling, UUID> {

    fun findByMottakId(mottakId: UUID): Klagebehandling?

    fun findByAvsluttetIsNullAndAvsluttetAvSaksbehandlerIsNotNull(): List<Klagebehandling>

    fun findByIdAndAvsluttetIsNotNull(id: UUID): Klagebehandling

    fun findByAvsluttetIsNotNull(): List<Klagebehandling>

    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    override fun getOne(id: UUID): Klagebehandling

}
