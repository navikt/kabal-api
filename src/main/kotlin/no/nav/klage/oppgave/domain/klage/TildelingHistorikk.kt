package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.*
import no.nav.klage.kodeverk.FradelingReason
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "tildelinghistorikk", schema = "klage")
class TildelingHistorikk(
    @Id
    val id: UUID = UUID.randomUUID(),
    val saksbehandlerident: String?,
    val enhet: String?,
    val tidspunkt: LocalDateTime,
    @Convert(converter = FradelingReasonConverter::class)
    @Column(name = "fradeling_reason_id")
    val fradelingReason: FradelingReason?,
    @Column(name = "utfoerende_ident")
    val utfoerendeIdent: String?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TildelingHistorikk

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

}
