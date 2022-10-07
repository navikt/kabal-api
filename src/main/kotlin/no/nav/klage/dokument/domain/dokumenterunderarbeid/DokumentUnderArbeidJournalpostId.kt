package no.nav.klage.dokument.domain.dokumenterunderarbeid

import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "dokument_under_arbeid_journalpost_id", schema = "klage")
class DokumentUnderArbeidJournalpostId(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "journalpost_id")
    val journalpostId: String,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DokumentUnderArbeidJournalpostId

        if (id != other.id) return false
        if (journalpostId != other.journalpostId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + journalpostId.hashCode()
        return result
    }

    override fun toString(): String {
        return "DokumentUnderArbeidJournalpostId(id=$id, journalpostId='$journalpostId')"
    }
}