package no.nav.klage.oppgave.domain.behandling.historikk

import jakarta.persistence.AttributeOverride
import jakarta.persistence.AttributeOverrides
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import no.nav.klage.oppgave.domain.behandling.embedded.SattPaaVent
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "satt_paa_vent_historikk", schema = "klage")
class SattPaaVentHistorikk(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Embedded
    @AttributeOverrides(
        value = [
            AttributeOverride(name = "from", column = Column(name = "satt_paa_vent_from")),
            AttributeOverride(name = "to", column = Column(name = "satt_paa_vent_to")),
            AttributeOverride(name = "reason", column = Column(name = "satt_paa_vent_reason")),
            AttributeOverride(name = "reasonId", column = Column(name = "satt_paa_vent_reason_id")),
        ],
    )
    val sattPaaVent: SattPaaVent?,
    @Column(name = "tidspunkt", nullable = false)
    val tidspunkt: LocalDateTime,
    @Column(name = "utfoerende_ident")
    val utfoerendeIdent: String?,
    @Column(name = "utfoerende_navn")
    val utfoerendeNavn: String?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SattPaaVentHistorikk

        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String =
        "SattPaaVentHistorikk(id=$id, sattPaaVent=$sattPaaVent, tidspunkt=$tidspunkt, utfoerendeIdent=$utfoerendeIdent, utfoerendeNavn=$utfoerendeNavn)"
}
