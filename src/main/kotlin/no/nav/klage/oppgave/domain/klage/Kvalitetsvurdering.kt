package no.nav.klage.oppgave.domain.klage

import no.nav.klage.oppgave.domain.kodeverk.Eoes
import no.nav.klage.oppgave.domain.kodeverk.EoesConverter
import no.nav.klage.oppgave.domain.kodeverk.RaadfoertMedLege
import no.nav.klage.oppgave.domain.kodeverk.RaadfoertMedLegeConverter
import org.springframework.data.domain.Persistable
import java.time.LocalDateTime
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "kvalitetsvurdering", schema = "klage")
class Kvalitetsvurdering(
    @Id
    @JvmField
    val id: UUID = UUID.randomUUID(),
    @Column(name = "eoes_id")
    @Convert(converter = EoesConverter::class)
    var eoes: Eoes? = null,
    @Column(name = "raadfoert_med_lege_id")
    @Convert(converter = RaadfoertMedLegeConverter::class)
    var raadfoertMedLege: RaadfoertMedLege? = null,
    @Column(name = "intern_vurdering")
    var internVurdering: String? = null,
    @Column(name = "send_tilbakemelding")
    var sendTilbakemelding: Boolean? = null,
    @Column(name = "tilbakemelding")
    var tilbakemelding: String? = null,
    @Column(name = "mottaker_saksbehandlerident")
    val mottakerSaksbehandlerident: String? = null,
    @Column(name = "mottaker_enhet")
    val mottakerEnhet: String? = null,
    @Column(name = "created")
    val created: LocalDateTime = LocalDateTime.now(),
    @Column(name = "modified")
    var modified: LocalDateTime = LocalDateTime.now()
) : Persistable<UUID> {

    override fun getId(): UUID = id

    @Transient
    private var isNew = true

    override fun isNew(): Boolean {
        return isNew
    }

    @PrePersist
    @PostLoad
    fun markNotNew() {
        isNew = false
    }

    override fun toString(): String {
        return "Tilbakemelding(id=$id, " +
                "modified=$modified, " +
                "created=$created)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Kvalitetsvurdering

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
