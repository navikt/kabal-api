package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.*
import org.hibernate.annotations.BatchSize
import org.hibernate.annotations.Fetch
import org.hibernate.annotations.FetchMode
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "forlenget_behandlingstid_work_area", schema = "klage")
class ForlengetBehandlingstid(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "created")
    val created: LocalDateTime = LocalDateTime.now(),
    @Column(name = "title")
    var title: String?,
    @Column(name = "fullmektig_fritekst")
    var fullmektigFritekst: String?,
    @Column(name = "custom_text")
    var customText: String?,
    @Column(name = "reason")
    var reason: String?,
    @Embedded
    @AttributeOverrides(
        value = [
            AttributeOverride(name = "varsletFrist", column = Column(name = "behandlingstid_date")),
            AttributeOverride(name = "varsletBehandlingstidUnits", column = Column(name = "behandlingstid_units")),
            AttributeOverride(name = "varsletBehandlingstidUnitType", column = Column(name = "behandlingstid_unit_type_id")),
        ]
    )
    var behandlingstid: VarsletBehandlingstid?,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "forlenget_behandlingstid_work_area_id", referencedColumnName = "id", nullable = false)
    @Fetch(FetchMode.SELECT)
    @BatchSize(size = 10)
    val receivers: MutableSet<ForlengetBehandlingstidReceiver> = mutableSetOf(),
)