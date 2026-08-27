package no.nav.klage.oppgave.domain.behandling.historikk

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import no.nav.klage.kodeverk.FlowState
import no.nav.klage.kodeverk.FlowStateConverter
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "rolhistorikk", schema = "klage")
class RolHistorikk(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "rol_ident")
    val rolIdent: String?,
    @Column(name = "tidspunkt", nullable = false)
    val tidspunkt: LocalDateTime,
    @Column(name = "utfoerende_ident")
    val utfoerendeIdent: String?,
    @Column(name = "utfoerende_navn")
    val utfoerendeNavn: String?,
    @Column(name = "flow_state_id", nullable = false)
    @Convert(converter = FlowStateConverter::class)
    val flowState: FlowState,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RolHistorikk

        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String =
        "RolHistorikk(id=$id, rolIdent=$rolIdent, tidspunkt=$tidspunkt, utfoerendeIdent=$utfoerendeIdent, utfoerendeNavn=$utfoerendeNavn, flowState=$flowState)"
}
