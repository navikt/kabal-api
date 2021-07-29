package no.nav.klage.oppgave.domain.klage

import org.springframework.data.domain.Persistable
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "mottak_dokument", schema = "klage")
class MottakDokument(
    @Id
    @JvmField
    val id: UUID = UUID.randomUUID(),
    @Column(name = "type")
    @Enumerated(EnumType.STRING)
    var type: MottakDokumentType,
    @Column(name = "journalpost_id")
    var journalpostId: String
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MottakDokument

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

enum class MottakDokumentType {
    BRUKERS_KLAGE,
    OPPRINNELIG_VEDTAK,
    OVERSENDELSESBREV,
    ANNET
}
