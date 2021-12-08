package no.nav.klage.oppgave.domain.klage

import no.nav.klage.kodeverk.hjemmel.LovKilde
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "mottak_hjemmel", schema = "klage")
class MottakHjemmel(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "lov")
    @Enumerated(EnumType.STRING)
    val lov: LovKilde,
    @Column(name = "kapittel")
    val kapittel: Int?,
    @Column(name = "paragraf")
    val paragraf: Int?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MottakHjemmel

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
