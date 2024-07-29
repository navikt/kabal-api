package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "varslet_behandlingstid_historikk_mottaker_info", schema = "klage")
class VarsletBehandlingstidHistorikkMottaker(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Embedded
    @AttributeOverrides(
        value = [
            AttributeOverride(name = "type", column = Column(name = "varslet_behandlingstid_historikk_mottaker_type")),
            AttributeOverride(name = "value", column = Column(name = "varslet_behandlingstid_historikk_mottaker_value"))
        ]
    )
    val partId: PartId,
) {

}

fun PartId.toVarsletBehandlingstidHistorikkMottaker(): VarsletBehandlingstidHistorikkMottaker =
    VarsletBehandlingstidHistorikkMottaker(
        partId = PartId(type = type, value = value)
    )