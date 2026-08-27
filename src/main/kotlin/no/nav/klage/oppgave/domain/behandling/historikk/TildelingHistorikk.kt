package no.nav.klage.oppgave.domain.behandling.historikk

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import no.nav.klage.kodeverk.FradelingReason
import no.nav.klage.kodeverk.FradelingReasonConverter
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "tildelinghistorikk", schema = "klage")
class TildelingHistorikk(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "saksbehandlerident")
    val saksbehandlerident: String?,
    @Column(name = "enhet")
    val enhet: String?,
    @Column(name = "hjemmel_id_list")
    val hjemmelIdList: String?,
    @Column(name = "tidspunkt", nullable = false)
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

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String =
        "TildelingHistorikk(id=$id, saksbehandlerident=$saksbehandlerident, enhet=$enhet, hjemmelIdList=$hjemmelIdList, tidspunkt=$tidspunkt, fradelingReason=$fradelingReason, utfoerendeIdent=$utfoerendeIdent, utfoerendeNavn=$utfoerendeNavn)"
}
