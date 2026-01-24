package no.nav.klage.oppgave.domain.document

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.*

@Entity
@Table(name = "document_to_merge", schema = "klage")
class DocumentToMerge(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "journalpost_id", nullable = false)
    val journalpostId: String,
    @Column(name = "dokument_info_id", nullable = false)
    val dokumentInfoId: String,
    @Column(name = "index", nullable = false)
    val index: Int,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DocumentToMerge

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "DocumentToMerge(id=$id, journalpostId='$journalpostId', dokumentInfoId='$dokumentInfoId', index=$index)"
    }

}