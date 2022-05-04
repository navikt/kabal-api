package no.nav.klage.dokument.domain.dokumenterunderarbeid

import no.nav.klage.oppgave.domain.kodeverk.Kode
import javax.persistence.AttributeConverter
import javax.persistence.Converter


enum class DokumentType(
    override val id: String,
    override val navn: String,
    override val beskrivelse: String,
    val defaultFilnavn: String
) : Kode {

    BREV("1", "Brev", "Brev", "brev.pdf"),
    NOTAT("2", "Notat", "Notat", "notat.pdf"),

    //VEDLEGG("3", "Vedlegg", "Vedlegg", "vedlegg.pdf"),
    VEDTAK("4", "Vedtak", "Vedtak", "vedtaksbrev.pdf"),
    BESLUTNING("5", "Beslutning", "Beslutning", "beslutningsbrev.pdf"),
    ;

    override fun toString(): String {
        return "DokumentType(id=$id, " +
                "navn=$navn)"
    }

    companion object {
        fun of(id: String): DokumentType {
            return DokumentType.values().firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("No DokumentType with $id exists")
        }

        fun fromNavn(navn: String): DokumentType {
            return DokumentType.values().firstOrNull { it.navn == navn }
                ?: throw IllegalArgumentException("No DokumentType with $navn exists")
        }
    }
}


@Converter
class DokumentTypeConverter : AttributeConverter<DokumentType, String?> {

    override fun convertToDatabaseColumn(entity: DokumentType?): String? =
        entity?.id

    override fun convertToEntityAttribute(id: String?): DokumentType? =
        id?.let { DokumentType.of(it) }
}