package no.nav.klage.oppgave.domain.behandling.embedded

import jakarta.persistence.*
import no.nav.klage.kodeverk.PartIdType
import java.util.*

@Embeddable
data class SakenGjelder(
    @Column(name = "saken_gjelder_id")
    var id: UUID,
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