package no.nav.klage.oppgave.domain.klage

import org.springframework.data.domain.Persistable
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "saksdokument", schema = "klage")
class Saksdokument(
    @Id
    @JvmField
    val id: UUID = UUID.randomUUID(),
    @Column(name = "journalpost_id")
    val journalpostId: String,
    @Column(name = "dokument_info_id")
    val dokumentInfoId: String
) : Persistable<UUID> {

    override fun getId(): UUID = id
    
    @Transient
    private var isNew = true

    override fun isNew(): Boolean {
        return isNew
    }

    @PrePersist
    @PostLoad
    fun markNotNew() {
        isNew = false
    }

    override fun toString(): String {
        return "Saksdokument(id=$id, journalpostId=$journalpostId, dokumentInfoId=$dokumentInfoId)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Saksdokument

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
