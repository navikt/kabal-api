package no.nav.klage.oppgave.domain.klage

import java.util.*
import javax.persistence.*

@Entity
@Table(name = "mottak_dokument", schema = "klage")
class MottakDokument(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "type")
    @Enumerated(EnumType.STRING)
    var type: MottakDokumentType,
    @Column(name = "journalpost_id")
    var journalpostId: String
) {
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
    BRUKERS_SOEKNAD,
    OPPRINNELIG_VEDTAK,
    BRUKERS_KLAGE,
    BRUKERS_ANKE,
    OVERSENDELSESBREV,
    KLAGE_VEDTAK,
    ANNET
}
