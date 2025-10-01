package no.nav.klage.oppgave.domain.mottak

import jakarta.persistence.*
import java.util.*

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

        return id == other.id
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
    BRUKERS_OMGJOERINGSKRAV,
    BRUKERS_BEGJAERING_OM_GJENOPPTAK,
    OVERSENDELSESBREV,
    KLAGE_VEDTAK,
    ANNET
}
