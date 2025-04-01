package no.nav.klage.dokument.domain.dokumenterunderarbeid

import jakarta.persistence.*
import org.hibernate.envers.Audited
import java.util.*

@Entity
@Table(name = "brevmottaker", schema = "klage")
@Audited
class Brevmottaker(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "technical_part_id")
    val technicalPartId: UUID,
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
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Brevmottaker

        if (localPrint != other.localPrint) return false
        if (forceCentralPrint != other.forceCentralPrint) return false
        if (id != other.id) return false
        if (technicalPartId != other.technicalPartId) return false
        if (identifikator != other.identifikator) return false
        if (address != other.address) return false
        if (navn != other.navn) return false

        return true
    }

    override fun hashCode(): Int {
        var result = localPrint.hashCode()
        result = 31 * result + forceCentralPrint.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + (identifikator?.hashCode() ?: 0)
        result = 31 * result + (technicalPartId?.hashCode() ?: 0)
        result = 31 * result + (address?.hashCode() ?: 0)
        result = 31 * result + (navn?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "Brevmottaker(id=$id, technicalPartId=$technicalPartId, identifikator=$identifikator, localPrint=$localPrint, forceCentralPrint=$forceCentralPrint, address=$address, navn=$navn)"
    }

}