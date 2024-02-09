package no.nav.klage.dokument.domain.dokumenterunderarbeid

import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "dokument_under_arbeid_avsender_mottaker_info", schema = "klage")
class DokumentUnderArbeidAvsenderMottakerInfo(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "identifikator")
    val identifikator: String,
    @Column(name = "local_print")
    val localPrint: Boolean,
    @Column(name = "force_central_print")
    val forceCentralPrint: Boolean,
    @Embedded
    @AttributeOverrides(
        value = [
            AttributeOverride(name = "adressetype", column = Column(name = "adress_adressetype")),
            AttributeOverride(name = "adresselinje1", column = Column(name = "adress_adresselinje_1")),
            AttributeOverride(name = "adresselinje2", column = Column(name = "adress_adresselinje_2")),
            AttributeOverride(name = "adresselinje3", column = Column(name = "adress_adresselinje_3")),
            AttributeOverride(name = "postnummer", column = Column(name = "adress_postnummer")),
            AttributeOverride(name = "poststed", column = Column(name = "adress_poststed")),
            AttributeOverride(name = "landkode", column = Column(name = "adress_landkode")),
        ]
    )
    val address: DokumentUnderArbeidAdresse?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DokumentUnderArbeidAvsenderMottakerInfo

        if (id != other.id) return false
        if (identifikator != other.identifikator) return false
        if (localPrint != other.localPrint) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + identifikator.hashCode()
        result = 31 * result + localPrint.hashCode()
        return result
    }

    override fun toString(): String {
        return "DokumentUnderArbeidAvsenderMottakerInfo(id=$id, identifikator='$identifikator', localPrint=$localPrint)"
    }
}