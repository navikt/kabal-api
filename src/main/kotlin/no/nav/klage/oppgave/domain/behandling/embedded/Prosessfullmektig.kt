package no.nav.klage.oppgave.domain.behandling.embedded

import jakarta.persistence.*
import no.nav.klage.dokument.domain.dokumenterunderarbeid.Adresse
import java.util.*

@Embeddable
data class Prosessfullmektig(
    @Column(name = "prosessfullmektig_id")
    var id: UUID,
    @Embedded
    @AttributeOverrides(
        value = [
            AttributeOverride(name = "type", column = Column(name = "prosessfullmektig_type")),
            AttributeOverride(name = "value", column = Column(name = "prosessfullmektig_value"))
        ]
    )
    val partId: PartId?,
    @Embedded
    @AttributeOverrides(
        value = [
            AttributeOverride(name = "adressetype", column = Column(name = "prosessfullmektig_address_adressetype")),
            AttributeOverride(
                name = "adresselinje1",
                column = Column(name = "prosessfullmektig_address_adresselinje_1")
            ),
            AttributeOverride(
                name = "adresselinje2",
                column = Column(name = "prosessfullmektig_address_adresselinje_2")
            ),
            AttributeOverride(
                name = "adresselinje3",
                column = Column(name = "prosessfullmektig_address_adresselinje_3")
            ),
            AttributeOverride(name = "postnummer", column = Column(name = "prosessfullmektig_address_postnummer")),
            AttributeOverride(name = "poststed", column = Column(name = "prosessfullmektig_address_poststed")),
            AttributeOverride(name = "landkode", column = Column(name = "prosessfullmektig_address_landkode")),
        ]
    )
    val address: Adresse?,
    val navn: String?,
)