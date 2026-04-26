package no.nav.klage.oppgave.domain

import jakarta.persistence.*
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.FagsystemConverter
import java.util.*

@Entity
@Table(
    name = "sak_persongalleri",
    schema = "klage",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uc_sak_persongalleri",
            columnNames = ["sak_fagsystem", "sak_fagsak_id", "foedselsnummer"]
        )
    ]
)
class SakPersongalleri(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "sak_fagsystem", nullable = false)
    @Convert(converter = FagsystemConverter::class)
    val fagsystem: Fagsystem,
    @Column(name = "sak_fagsak_id", nullable = false)
    val fagsakId: String,
    @Column(name = "foedselsnummer", nullable = false)
    val foedselsnummer: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SakPersongalleri

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "SakPersongalleri(id=$id, fagsystem=$fagsystem, fagsakId=$fagsakId, foedselsnummer=$foedselsnummer)"
    }
}