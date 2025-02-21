package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.*
import org.hibernate.annotations.BatchSize
import org.hibernate.annotations.Fetch
import org.hibernate.annotations.FetchMode
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "varslet_behandlingstid_historikk", schema = "klage")
class VarsletBehandlingstidHistorikk(
    @Id
    val id: UUID = UUID.randomUUID(),
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "varslet_behandlingstid_historikk_id", referencedColumnName = "id", nullable = false)
    @Fetch(FetchMode.SELECT)
    @BatchSize(size = 100)
    val mottakerList: List<VarsletBehandlingstidHistorikkMottaker> = listOf(),
    @Column(name = "tidspunkt")
    val tidspunkt: LocalDateTime,
    @Column(name = "utfoerende_ident")
    val utfoerendeIdent: String?,
    @Column(name = "utfoerende_navn")
    val utfoerendeNavn: String?,
    @Embedded
    var varsletBehandlingstid: VarsletBehandlingstid?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VarsletBehandlingstidHistorikk

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "VarsletBehandlingstidHistorikk(id=$id, mottakerList=$mottakerList, tidspunkt=$tidspunkt, utfoerendeIdent=$utfoerendeIdent, utfoerendeNavn=$utfoerendeNavn, varsletBehandlingstid=$varsletBehandlingstid)"
    }
}
