package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.*

@Embeddable
data class Prosessfullmektig(
    @Embedded
    @AttributeOverrides(
        value = [
            AttributeOverride(name = "type", column = Column(name = "prosessfullmektig_type")),
            AttributeOverride(name = "value", column = Column(name = "prosessfullmektig_value"))
        ]
    )
    val partId: PartId,
)