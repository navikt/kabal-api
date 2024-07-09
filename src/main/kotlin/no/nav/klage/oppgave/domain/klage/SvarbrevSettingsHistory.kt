package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.*
import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.oppgave.domain.klage.SvarbrevSettings.BehandlingstidUnitType
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "svarbrev_settings_history", schema = "klage")
class SvarbrevSettingsHistory(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "ytelse_id")
    @Convert(converter = YtelseConverter::class)
    val ytelse: Ytelse,
    @Column(name = "behandlingstid_units")
    var behandlingstidUnits: Int,
    @Column(name = "behandlingstid_unit_type")
    var behandlingstidUnitType: BehandlingstidUnitType,
    @Column(name = "custom_text")
    val customText: String?,
    @Column(name = "created")
    val created: LocalDateTime,
    @Column(name = "created_by")
    val createdBy: String,
    @Column(name = "should_send")
    var shouldSend: Boolean,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "svarbrev_settings_id")
    val svarbrevSettings: SvarbrevSettings
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SvarbrevSettingsHistory

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "SvarbrevSettingsHistory(id=$id, ytelse=$ytelse, behandlingstidUnits=$behandlingstidUnits, behandlingstidUnitType=$behandlingstidUnitType, customText=$customText, created=$created, createdBy='$createdBy', shouldSend=$shouldSend)"
    }

}