package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.*
import no.nav.klage.kodeverk.TimeUnitType
import no.nav.klage.kodeverk.TimeUnitTypeConverter
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "varslet_frist_historikk", schema = "klage")
class VarsletFristHistorikk(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Embedded
    @AttributeOverrides(
        value = [
            AttributeOverride(name = "type", column = Column(name = "mottaker_type")),
            AttributeOverride(name = "value", column = Column(name = "mottaker_value"))
        ]
    )
    var mottaker: PartId?,
    @Column(name = "tidspunkt")
    val tidspunkt: LocalDateTime,
    @Column(name = "utfoerende_ident")
    val utfoerendeIdent: String?,
    @Column(name = "varslet_frist")
    val varsletFrist: LocalDate?,
    @Column(name = "varslet_frist_units")
    val varsletFristUnits: Int?,
    @Column(name = "varslet_frist_id")
    @Convert(converter = TimeUnitTypeConverter::class)
    val varsletFristUnitType: TimeUnitType?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VarsletFristHistorikk

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "VarsletFristHistorikk(id=$id, mottaker=$mottaker, tidspunkt=$tidspunkt, utfoerendeIdent='$utfoerendeIdent', varsletFrist=$varsletFrist, varsletFristUnits=$varsletFristUnits, varsletFristUnitType=$varsletFristUnitType)"
    }
}
