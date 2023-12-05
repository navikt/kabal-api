package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "klagerhistorikk", schema = "klage")
class KlagerHistorikk(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Embedded
    @AttributeOverrides(
        value = [
            AttributeOverride(name = "type", column = Column(name = "klager_type")),
            AttributeOverride(name = "value", column = Column(name = "klager_value"))
        ]
    )
    var partId: PartId,
    @Column(name = "tidspunkt")
    val tidspunkt: LocalDateTime,
    @Column(name = "utfoerende_ident")
    val utfoerendeIdent: String?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KlagerHistorikk

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "KlagerHistorikk(id=$id, partId=$partId, tidspunkt=$tidspunkt, utfoerendeIdent='$utfoerendeIdent')"
    }

}