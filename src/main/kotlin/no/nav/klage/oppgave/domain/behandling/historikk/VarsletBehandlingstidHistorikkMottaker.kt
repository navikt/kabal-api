package no.nav.klage.oppgave.domain.behandling.historikk

import jakarta.persistence.AttributeOverride
import jakarta.persistence.AttributeOverrides
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import no.nav.klage.oppgave.domain.behandling.embedded.Mottaker
import no.nav.klage.oppgave.domain.behandling.embedded.MottakerNavn
import no.nav.klage.oppgave.domain.behandling.embedded.MottakerPartId
import no.nav.klage.oppgave.domain.behandling.embedded.PartId
import java.util.UUID

@Entity
@Table(name = "varslet_behandlingstid_historikk_mottaker_info", schema = "klage")
class VarsletBehandlingstidHistorikkMottaker(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Embedded
    @AttributeOverrides(
        value = [
            AttributeOverride(name = "type", column = Column(name = "varslet_behandlingstid_historikk_mottaker_type")),
            AttributeOverride(name = "value", column = Column(name = "varslet_behandlingstid_historikk_mottaker_value")),
        ],
    )
    val partId: PartId?,
    @Column(name = "varslet_behandlingstid_historikk_mottaker_navn")
    val navn: String?,
)

fun Mottaker.toVarsletBehandlingstidHistorikkMottaker(): VarsletBehandlingstidHistorikkMottaker =
    when (this) {
        is MottakerPartId -> {
            VarsletBehandlingstidHistorikkMottaker(
                partId = PartId(type = value.type, value = value.value),
                navn = null,
            )
        }

        is MottakerNavn -> {
            VarsletBehandlingstidHistorikkMottaker(
                partId = null,
                navn = value,
            )
        }
    }
