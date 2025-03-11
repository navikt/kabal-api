package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.*
import no.nav.klage.kodeverk.TimeUnitType
import no.nav.klage.kodeverk.TimeUnitTypeConverter
import org.hibernate.annotations.BatchSize
import org.hibernate.annotations.Fetch
import org.hibernate.annotations.FetchMode
import org.hibernate.envers.NotAudited
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "forlenget_behandlingstid_draft", schema = "klage")
class ForlengetBehandlingstidDraft(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "created")
    val created: LocalDateTime = LocalDateTime.now(),
    @Column(name = "title")
    var title: String? = null,
    @Column(name = "fullmektig_fritekst")
    var fullmektigFritekst: String? = null,
    @Column(name = "custom_text")
    var customText: String? = null,
    @Column(name = "reason")
    var reason: String? = null,
    @Column(name = "previous_behandlingstid_info")
    var previousBehandlingstidInfo: String? = null,
    @Column(name = "behandlingstid_date")
    var varsletFrist: LocalDate? = null,
    @Column(name = "behandlingstid_units")
    var varsletBehandlingstidUnits: Int? = null,
    @Column(name = "behandlingstid_unit_type_id")
    @Convert(converter = TimeUnitTypeConverter::class)
    var varsletBehandlingstidUnitType: TimeUnitType = TimeUnitType.WEEKS,
    @Column(name = "begrunnelse")
    var begrunnelse: String? = null,
    @Column(name = "do_not_send_letter")
    var doNotSendLetter: Boolean = false,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "forlenget_behandlingstid_draft_id", referencedColumnName = "id", nullable = false)
    @Fetch(FetchMode.SELECT)
    @BatchSize(size = 10)
    @NotAudited
    val receivers: MutableSet<ForlengetBehandlingstidDraftReceiver> = mutableSetOf(),

    )