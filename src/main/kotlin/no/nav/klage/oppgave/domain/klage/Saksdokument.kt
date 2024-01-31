package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.*

@Entity
@Table(name = "saksdokument", schema = "klage")
class Saksdokument(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "journalpost_id")
    val journalpostId: String,
    @Column(name = "dokument_info_id")
    val dokumentInfoId: String
) {
    override fun toString(): String {
        return "Saksdokument(id=$id, journalpostId=$journalpostId, dokumentInfoId=$dokumentInfoId)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Saksdokument

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
