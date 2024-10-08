package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.*

@Embeddable
data class Klager(
    @Embedded
    @AttributeOverrides(
        value = [
            AttributeOverride(name = "type", column = Column(name = "klager_type")),
            AttributeOverride(name = "value", column = Column(name = "klager_value"))
        ]
    )
    var partId: PartId,
    @Embedded
    @AttributeOverrides(
        value = [
            AttributeOverride(name = "partId.type", column = Column(name = "klager_prosessfullmektig_type")),
            AttributeOverride(name = "partId.value", column = Column(name = "klager_prosessfullmektig_value")),
        ]
    )
    var prosessfullmektig: Prosessfullmektig? = null
) {
    fun toSakenGjelder() = SakenGjelder(
        partId = this.partId.copy(),
    )
}
