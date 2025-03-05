package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Embeddable
import no.nav.klage.kodeverk.TimeUnitType
import no.nav.klage.kodeverk.TimeUnitTypeConverter
import java.time.LocalDate

@Embeddable
data class VarsletBehandlingstid(
    @Column(name = "varslet_frist")
    var varsletFrist: LocalDate? = null,
    @Column(name = "varslet_behandlingstid_units")
    var varsletBehandlingstidUnits: Int? = 12,
    @Column(name = "varslet_behandlingstid_unit_type_id")
    @Convert(converter = TimeUnitTypeConverter::class)
    var varsletBehandlingstidUnitType: TimeUnitType = TimeUnitType.WEEKS,
)