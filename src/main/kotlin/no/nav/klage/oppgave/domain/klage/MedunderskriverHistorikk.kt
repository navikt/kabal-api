package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.*
import no.nav.klage.kodeverk.FlowState
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "medunderskriverhistorikk", schema = "klage")
class MedunderskriverHistorikk(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "saksbehandlerident")
    val saksbehandlerident: String?,
    @Column(name = "tidspunkt")
    val tidspunkt: LocalDateTime,
    @Column(name = "utfoerende_ident")
    val utfoerendeIdent: String?,
    @Column(name = "flow_state_id")
    @Convert(converter = FlowStateConverter::class)
    val flowState: FlowState?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MedunderskriverHistorikk

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "MedunderskriverHistorikk(id=$id, saksbehandlerident=$saksbehandlerident, tidspunkt=$tidspunkt, utfoerendeIdent='$utfoerendeIdent', flowState=$flowState)"
    }

}
