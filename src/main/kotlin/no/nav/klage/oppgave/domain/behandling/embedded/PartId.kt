package no.nav.klage.oppgave.domain.behandling.embedded

import jakarta.persistence.Convert
import jakarta.persistence.Embeddable
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.PartIdTypeConverter

@Embeddable
data class PartId(
    @Convert(converter = PartIdTypeConverter::class)
    val type: PartIdType,
    val value: String,
) {
    fun isPerson() = type == PartIdType.PERSON
}

sealed interface Mottaker {
    val value: Any
}

class MottakerPartId(
    override val value: PartId
) : Mottaker

class MottakerNavn(
    override val value: String
) : Mottaker