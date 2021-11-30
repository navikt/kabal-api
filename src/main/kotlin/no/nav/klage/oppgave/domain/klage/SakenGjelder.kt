package no.nav.klage.oppgave.domain.klage

import no.nav.klage.kodeverk.PartIdType
import javax.persistence.*

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
    @Column(name = "saken_gjelder_skal_motta_kopi")
    val skalMottaKopi: Boolean
) {
    fun erPerson() = partId.type == PartIdType.PERSON

    fun erVirksomhet() = partId.type == PartIdType.VIRKSOMHET
}
