package no.nav.klage.oppgave.domain.behandling.embedded

import jakarta.persistence.*
import java.util.*

@Embeddable
data class Klager(
    @Column(name = "klager_id")
    var id: UUID,
    @Embedded
    @AttributeOverrides(
        value = [
            AttributeOverride(name = "type", column = Column(name = "klager_type")),
            AttributeOverride(name = "value", column = Column(name = "klager_value"))
        ]
    )
    var partId: PartId,
) {
    fun toSakenGjelder() = SakenGjelder(
        id = this.id,
        partId = this.partId.copy(),
    )
}