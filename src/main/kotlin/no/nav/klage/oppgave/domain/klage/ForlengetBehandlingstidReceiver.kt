package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.*
import no.nav.klage.dokument.domain.dokumenterunderarbeid.Adresse
import org.hibernate.envers.Audited
import java.util.*

@Entity
@Table(name = "forlenget_behandlingstid_work_area_receiver", schema = "klage")
@Audited
class ForlengetBehandlingstidReceiver(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "identifikator")
    val identifikator: String?,
    @Column(name = "local_print")
    val localPrint: Boolean,
    @Column(name = "force_central_print")
    val forceCentralPrint: Boolean,
    @Embedded
    @AttributeOverrides(
        value = [
            AttributeOverride(name = "adressetype", column = Column(name = "address_adressetype")),
            AttributeOverride(name = "adresselinje1", column = Column(name = "address_adresselinje_1")),
            AttributeOverride(name = "adresselinje2", column = Column(name = "address_adresselinje_2")),
            AttributeOverride(name = "adresselinje3", column = Column(name = "address_adresselinje_3")),
            AttributeOverride(name = "postnummer", column = Column(name = "address_postnummer")),
            AttributeOverride(name = "poststed", column = Column(name = "address_poststed")),
            AttributeOverride(name = "landkode", column = Column(name = "address_landkode")),
        ]
    )
    val address: Adresse?,
    @Column(name = "navn")
    val navn: String?,
)