package no.nav.klage.oppgave.domain.svarbrevsettings

import jakarta.persistence.*
import no.nav.klage.kodeverk.TimeUnitType
import no.nav.klage.kodeverk.TimeUnitTypeConverter
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.TypeConverter
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.kodeverk.ytelse.YtelseConverter
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "svarbrev_settings", schema = "klage")
class SvarbrevSettings(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "ytelse_id", nullable = false)
    @Convert(converter = YtelseConverter::class)
    val ytelse: Ytelse,
    @Column(name = "behandlingstid_units", nullable = false)
    var behandlingstidUnits: Int,
    @Column(name = "behandlingstid_unit_type_id", nullable = false)
    @Convert(converter = TimeUnitTypeConverter::class)
    var behandlingstidUnitType: TimeUnitType,
    @Column(name = "custom_text")
    var customText: String?,
    @Column(name = "should_send", nullable = false)
    var shouldSend: Boolean,
    @Column(name = "created", nullable = false)
    val created: LocalDateTime,
    @Column(name = "modified", nullable = false)
    var modified: LocalDateTime,
    @Column(name = "created_by", nullable = false)
    var createdBy: String,
    @Column(name = "type_id", nullable = false)
    @Convert(converter = TypeConverter::class)
    val type: Type,
    @OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
    @JoinColumn(name = "svarbrev_settings_id")
    val history: MutableSet<SvarbrevSettingsHistory> = mutableSetOf()
) {

    fun toHistory(): SvarbrevSettingsHistory {
        return SvarbrevSettingsHistory(
            svarbrevSettings = this,
            ytelse = ytelse,
            behandlingstidUnits = behandlingstidUnits,
            behandlingstidUnitType = behandlingstidUnitType,
            customText = customText,
            shouldSend = shouldSend,
            created = modified,
            createdBy = createdBy,
            type = type,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SvarbrevSettings

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "SvarbrevSettings(id=$id, ytelse=$ytelse, behandlingstidUnits=$behandlingstidUnits, behandlingstidUnitType=$behandlingstidUnitType, customText=$customText, shouldSend=$shouldSend, created=$created, modified=$modified, createdBy='$createdBy', type=$type)"
    }
}