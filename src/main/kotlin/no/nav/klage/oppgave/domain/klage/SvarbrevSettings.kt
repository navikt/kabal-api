package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.*
import no.nav.klage.kodeverk.Ytelse
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "svarbrev_settings", schema = "klage")
class SvarbrevSettings(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "ytelse_id")
    @Convert(converter = YtelseConverter::class)
    val ytelse: Ytelse,
    @Column(name = "behandlingstid_weeks")
    var behandlingstidWeeks: Int,
    @Column(name = "custom_text")
    var customText: String?,
    @Column(name = "should_send")
    var shouldSend: Boolean,
    @Column(name = "created")
    val created: LocalDateTime,
    @Column(name = "modified")
    var modified: LocalDateTime,
    @Column(name = "created_by")
    var createdBy: String,
    @OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
    @JoinColumn(name = "svarbrev_settings_id")
    val history: MutableSet<SvarbrevSettingsHistory> = mutableSetOf()
) {

    fun toHistory(): SvarbrevSettingsHistory {
        return SvarbrevSettingsHistory(
            svarbrevSettings = this,
            ytelse = ytelse,
            behandlingstidWeeks = behandlingstidWeeks,
            customText = customText,
            shouldSend = shouldSend,
            created = modified,
            createdBy = createdBy,
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
        return "SvarbrevSettings(id=$id, ytelse=$ytelse, behandlingstidWeeks=$behandlingstidWeeks, customText=$customText, shouldSend=$shouldSend, created=$created, modified=$modified, createdBy='$createdBy')"
    }

}