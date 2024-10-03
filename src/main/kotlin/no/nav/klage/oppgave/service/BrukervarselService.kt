package no.nav.klage.oppgave.service

import no.nav.klage.dokument.api.view.DokumentView
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.domain.kafka.EventType
import no.nav.klage.oppgave.domain.kafka.KafkaEvent
import no.nav.klage.oppgave.domain.kafka.UtsendingStatus
import no.nav.klage.oppgave.domain.klage.Behandling
import no.nav.klage.oppgave.repositories.KafkaEventRepository
import no.nav.tms.varsel.action.Sensitivitet
import no.nav.tms.varsel.action.Tekst
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.builder.VarselActionBuilder
import org.springframework.stereotype.Service
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

@Service
class BrukervarselService(
    private val kafkaEventRepository: KafkaEventRepository
) {

    fun process(behandlingEndretEvent: BehandlingEndretEvent) {
        if (shouldSendVarsel(behandlingEndretEvent)) {
            val behandling = behandlingEndretEvent.behandling
            val eventId = UUID.randomUUID()

            kafkaEventRepository.save(
                KafkaEvent(
                    id = eventId,
                    behandlingId = behandlingEndretEvent.behandling.id,
                    kilde = behandlingEndretEvent.behandling.fagsystem.navn,
                    kildeReferanse = behandlingEndretEvent.behandling.kildeReferanse,
                    status = UtsendingStatus.IKKE_SENDT,
                    jsonPayload = behandlingOpprettet(behandling),
                    type = EventType.BRUKERVARSEL
                )
            )
        }
    }

    private fun shouldSendVarsel(behandlingEndretEvent: BehandlingEndretEvent): Boolean {
        return (behandlingEndretEvent.endringslogginnslag.isEmpty())
    }

    fun behandlingOpprettet(behandling: Behandling): String {
        return VarselActionBuilder.opprett {
            type = Varseltype.Beskjed
            varselId = behandling.id.toString()
            sensitivitet = Sensitivitet.High
            ident = behandling.sakenGjelder.partId.value
            tekster += Tekst(
                spraakkode = DokumentView.Language.NB.name,
                tekst = "${behandling.type.name} mottatt hos NAV Klageinstans ${behandling.mottattKlageinstans.toLocalDate()}. Ytelse: ${behandling.ytelse.name}",
                default = true
            )
            aktivFremTil = ZonedDateTime.now(ZoneId.of("Z")).plusDays(1)
        }
    }
}