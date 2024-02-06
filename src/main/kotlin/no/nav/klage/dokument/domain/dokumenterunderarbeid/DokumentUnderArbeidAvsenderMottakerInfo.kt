package no.nav.klage.dokument.domain.dokumenterunderarbeid

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
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