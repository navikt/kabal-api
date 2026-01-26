package no.nav.klage.oppgave.domain.behandling.subentities

import jakarta.persistence.*
import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.domain.behandling.Behandling
import java.util.*

@Entity
@Table(name = "mottak_dokument", schema = "klage")
class MottakDokument(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    var type: MottakDokumentType,
    @Column(name = "journalpost_id", nullable = false)
    var journalpostId: String,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "behandling_id", nullable = false)
    var behandling: Behandling,
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

fun Type.getMottakDokumentType(): MottakDokumentType {
    return when (this) {
        Type.KLAGE -> MottakDokumentType.BRUKERS_KLAGE
        Type.ANKE -> MottakDokumentType.BRUKERS_ANKE
        Type.OMGJOERINGSKRAV -> MottakDokumentType.BRUKERS_OMGJOERINGSKRAV
        Type.BEGJAERING_OM_GJENOPPTAK -> MottakDokumentType.BRUKERS_BEGJAERING_OM_GJENOPPTAK
        else -> throw IllegalArgumentException("Type $this has no MottakDokumentType.")
    }
}

data class MottakDokumentDTO(
    val type: MottakDokumentType,
    val journalpostId: String,
)