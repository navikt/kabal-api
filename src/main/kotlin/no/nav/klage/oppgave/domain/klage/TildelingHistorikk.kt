package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.*
import no.nav.klage.kodeverk.FradelingReason
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "tildelinghistorikk", schema = "klage")
class TildelingHistorikk(
    @Id
    val id: UUID = UUID.randomUUID(),
    val saksbehandlerident: String?,
    val enhet: String?,
    @Column(name = "hjemmel_id_list")
    val hjemmelIdList: String?,
    val tidspunkt: LocalDateTime,
    @Convert(converter = FradelingReasonConverter::class)
    @Column(name = "fradeling_reason_id")
    val fradelingReason: FradelingReason?,
    @Column(name = "utfoerende_ident")
    val utfoerendeIdent: String?,
    @Column(name = "utfoerende_navn")
    val utfoerendeNavn: String?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TildelingHistorikk

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "TildelingHistorikk(id=$id, saksbehandlerident=$saksbehandlerident, enhet=$enhet, hjemmelIdList=$hjemmelIdList, tidspunkt=$tidspunkt, fradelingReason=$fradelingReason, utfoerendeIdent=$utfoerendeIdent, utfoerendeNavn=$utfoerendeNavn)"
    }
}
