package no.nav.klage.oppgave.repositories

import no.nav.klage.oppgave.domain.document.MergedDocument
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

@Repository
interface MergedDocumentRepository : JpaRepository<MergedDocument, UUID> {

    fun deleteByCreatedBefore(date: LocalDateTime)

    fun findByHash(hash: String): MergedDocument?

}