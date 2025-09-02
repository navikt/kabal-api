package no.nav.klage.oppgave.domain.behandling.embedded

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
    @Column(name = "varslet_reason_no_letter")
    var reasonNoLetter: String?,
    @Column(name = "varslet_varsel_type")
    @Enumerated(EnumType.STRING)
    val varselType: VarselType,
    @Column(name = "varslet_do_not_send_letter")
    var doNotSendLetter: Boolean,
) {
    enum class VarselType {
        OPPRINNELIG,
        FORLENGET,
    }
}