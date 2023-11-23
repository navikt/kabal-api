package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Embeddable
import no.nav.klage.kodeverk.FlowState
import java.time.LocalDateTime

@Embeddable
data class MedunderskriverTildeling(
    @Column(name = "saksbehandlerident")
    val saksbehandlerident: String?,
    @Column(name = "tidspunkt")
    val tidspunkt: LocalDateTime
)

@Embeddable
data class MedunderskriverTildelingForHistory(
    @Column(name = "saksbehandlerident")
    val saksbehandlerident: String?,
    @Column(name = "tidspunkt")
    val tidspunkt: LocalDateTime,
    @Column(name = "utfoerende_ident")
    val utfoerendeIdent: String,
    @Column(name = "medunderskriver_flow_state_id")
    @Convert(converter = FlowStateConverter::class)
    val medunderskriverFlowState: FlowState,
)
