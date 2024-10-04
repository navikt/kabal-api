package no.nav.klage.oppgave.service

import no.nav.klage.dokument.api.view.DokumentView
import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.domain.kafka.EventType
import no.nav.klage.oppgave.domain.kafka.KafkaEvent
import no.nav.klage.oppgave.domain.kafka.UtsendingStatus
import no.nav.klage.oppgave.domain.klage.Behandling
import no.nav.klage.oppgave.domain.klage.Felt
import no.nav.klage.oppgave.repositories.KafkaEventRepository
import no.nav.tms.varsel.action.Sensitivitet
import no.nav.tms.varsel.action.Tekst
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.builder.VarselActionBuilder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

@Service
@Transactional
class BrukervarselService(
    private val kafkaEventRepository: KafkaEventRepository
) {

    fun process(behandlingEndretEvent: BehandlingEndretEvent) {
        if (shouldSendVarsel(behandlingEndretEvent)) {
            val eventId = UUID.randomUUID()

            kafkaEventRepository.save(
                KafkaEvent(
                    id = eventId,
                    behandlingId = behandlingEndretEvent.behandling.id,
                    kilde = behandlingEndretEvent.behandling.fagsystem.navn,
                    kildeReferanse = behandlingEndretEvent.behandling.kildeReferanse,
                    status = UtsendingStatus.IKKE_SENDT,
                    jsonPayload = getBrukervarselPayload(behandlingEndretEvent),
                    type = EventType.BRUKERVARSEL
                )
            )
        }
    }

    private fun shouldSendVarsel(behandlingEndretEvent: BehandlingEndretEvent): Boolean {
        if (behandlingEndretEvent.endringslogginnslag.any { it.felt === Felt.KLAGEBEHANDLING_MOTTATT }) {
            return true
        } else if (behandlingEndretEvent.behandling.type == Type.KLAGE) {
            if (behandlingEndretEvent.endringslogginnslag.any {
                    it.felt === Felt.AVSLUTTET_AV_SAKSBEHANDLER_TIDSPUNKT
                }) {
                return true
            }
        }
        return false
    }

    private fun getBrukervarselPayload(behandlingEndretEvent: BehandlingEndretEvent): String {
        if (behandlingEndretEvent.endringslogginnslag.any { it.felt === Felt.KLAGEBEHANDLING_MOTTATT }) {
            return behandlingOpprettet(behandling = behandlingEndretEvent.behandling)
        } else if (behandlingEndretEvent.behandling.type == Type.KLAGE) {
            if (behandlingEndretEvent.endringslogginnslag.any {
                    it.felt === Felt.AVSLUTTET_AV_SAKSBEHANDLER_TIDSPUNKT
                }) {
                return behandlingInaktivert(behandling = behandlingEndretEvent.behandling)
            }
        }

        throw Error("Invalid state")
    }

    fun behandlingOpprettet(behandling: Behandling): String {
        return VarselActionBuilder.opprett {
            type = Varseltype.Beskjed
            varselId = behandling.id.toString()
            sensitivitet = Sensitivitet.High
            ident = behandling.sakenGjelder.partId.value
            tekster += Tekst(
                spraakkode = DokumentView.Language.NB.name,
                tekst = "${behandling.type.navn} på ${behandling.ytelse.navn.toSpecialCase()} mottatt hos NAV Klageinstans ${behandling.mottattKlageinstans.toLocalDate()}.",
                default = true
            )
            aktivFremTil = ZonedDateTime.now(ZoneId.of("Z")).plusDays(1)
        }
    }

    fun behandlingInaktivert(behandling: Behandling): String {
        return VarselActionBuilder.inaktiver {
            varselId = behandling.id.toString()
        }
    }

    private fun String.toSpecialCase(): String {
        val strings = this.split(" - ")
        return when (strings.size) {
            1 -> {
                this.replaceFirstChar { it.lowercase(Locale.getDefault()) }
            }

            2 -> {
                if (strings[0].equals(other = strings[1], ignoreCase = true)) {
                    strings[0].replaceFirstChar { it.lowercase(Locale.getDefault()) }
                } else {
                    strings[0].replaceFirstChar { it.lowercase(Locale.getDefault()) } + " - " + strings[1].replaceFirstChar {
                        it.lowercase(
                            Locale.getDefault()
                        )
                    }
                }
            }

            else -> this
        }
    }
}