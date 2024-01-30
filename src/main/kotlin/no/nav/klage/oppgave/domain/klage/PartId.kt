package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Embeddable
import no.nav.klage.kodeverk.PartIdType

@Embeddable
data class PartId(
    @Column(name = "type")
    @Convert(converter = PartIdTypeConverter::class)
    val type: PartIdType,
    @Column(name = "value")
    val value: String,
) {
    fun isPerson() = type == PartIdType.PERSON
}

