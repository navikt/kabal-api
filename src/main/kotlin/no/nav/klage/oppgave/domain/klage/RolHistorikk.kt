package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.*
import no.nav.klage.kodeverk.FlowState
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "rolhistorikk", schema = "klage")
class RolHistorikk(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "rol_ident")
    val rolIdent: String?,
    @Column(name = "tidspunkt")
    val tidspunkt: LocalDateTime,
    @Column(name = "utfoerende_ident")
    val utfoerendeIdent: String?,
    @Column(name = "flow_state_id")
    @Convert(converter = FlowStateConverter::class)
    val flowState: FlowState,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RolHistorikk

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "RolHistorikk(id=$id, rolIdent=$rolIdent, tidspunkt=$tidspunkt, utfoerendeIdent='$utfoerendeIdent', flowState=$flowState)"
    }


}
