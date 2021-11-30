package no.nav.klage.oppgave.domain.klage

import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.PartIdTypeConverter
import javax.persistence.Column
import javax.persistence.Convert
import javax.persistence.Embeddable

@Embeddable
data class PartId(
    @Column(name = "type")
    @Convert(converter = PartIdTypeConverter::class)
    val type: PartIdType,
    @Column(name = "value")
    val value: String,
)
