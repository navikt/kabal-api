package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.*
import no.nav.klage.kodeverk.TimeUnitType
import no.nav.klage.kodeverk.TimeUnitTypeConverter
import java.time.LocalDate

@Embeddable
data class VarsletBehandlingstid(
    @Column(name = "varslet_frist")
    var varsletFrist: LocalDate? = null,
    @Column(name = "varslet_behandlingstid_units")
    var varsletBehandlingstidUnits: Int? = null,
    @Column(name = "varslet_behandlingstid_unit_type_id")
    @Convert(converter = TimeUnitTypeConverter::class)
    var varsletBehandlingstidUnitType: TimeUnitType? = null,
    @Column(name = "begrunnelse")
    var begrunnelse: String? = null,
    @Column(name = "varsel_type")
    @Enumerated(EnumType.STRING)
    val varselType: VarselType,
) {
    enum class VarselType {
        OPPRINNELIG,
        FORLENGET,
    }
}