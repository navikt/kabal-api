package no.nav.klage.dokument.domain.dokumenterunderarbeid

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.*

@Entity
@Table(name = "dokument_under_arbeid_dokarkiv_reference", schema = "klage")
class DokumentUnderArbeidDokarkivReference(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "journalpost_id")
    val journalpostId: String,
    @Column(name = "dokument_info_id")
    val dokumentInfoId: String?,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DokumentUnderArbeidDokarkivReference

        if (id != other.id) return false
        if (journalpostId != other.journalpostId) return false
        if (dokumentInfoId != other.dokumentInfoId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + journalpostId.hashCode()
        result = 31 * result + dokumentInfoId.hashCode()
        return result
    }

    override fun toString(): String {
        return "DokumentUnderArbeidJournalpostId(id=$id, journalpostId='$journalpostId', dokumentInfoId='$dokumentInfoId')"
    }
}