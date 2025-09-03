package no.nav.klage.oppgave.domain.behandling.historikk

import jakarta.persistence.*
import no.nav.klage.oppgave.domain.behandling.embedded.SattPaaVent
import java.time.LocalDateTime
import java.util.*

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
        ]
    )
    val sattPaaVent: SattPaaVent?,
    @Column(name = "tidspunkt")
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

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "SattPaaVentHistorikk(id=$id, sattPaaVent=$sattPaaVent, tidspunkt=$tidspunkt, utfoerendeIdent=$utfoerendeIdent, utfoerendeNavn=$utfoerendeNavn)"
    }

}