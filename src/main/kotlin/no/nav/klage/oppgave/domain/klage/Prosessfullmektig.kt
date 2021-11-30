package no.nav.klage.oppgave.domain.klage

import no.nav.klage.kodeverk.PartIdType
import javax.persistence.*

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
    @Column(name = "skal_parten_motta_kopi")
    val skalPartenMottaKopi: Boolean
) {
    fun erPerson() = partId.type == PartIdType.PERSON
}
