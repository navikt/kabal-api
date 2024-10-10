package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.*
import no.nav.klage.kodeverk.PartIdType

@Embeddable
data class SakenGjelder(
    @Embedded
    @AttributeOverrides(
        value = [
            AttributeOverride(name = "type", column = Column(name = "saken_gjelder_type")),
            AttributeOverride(name = "value", column = Column(name = "saken_gjelder_value"))
        ]
    )
    val partId: PartId,
) {
    fun erPerson() = partId.type == PartIdType.PERSON

    fun erVirksomhet() = partId.type == PartIdType.VIRKSOMHET
}
