package no.nav.klage.oppgave.domain.behandling.embedded

import jakarta.persistence.*
import java.util.*

@Embeddable
data class Klager(
    @Column(name = "klager_id", nullable = false)
    var id: UUID,
    @Embedded
    @AttributeOverrides(
        value = [
            AttributeOverride(name = "type", column = Column(name = "klager_type", nullable = false)),
            AttributeOverride(name = "value", column = Column(name = "klager_value", nullable = false))
        ]
    )
    var partId: PartId,
) {
    fun toSakenGjelder() = SakenGjelder(
        id = this.id,
        partId = this.partId.copy(),
    )
}