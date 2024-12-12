package no.nav.klage.innsyn.service

import no.nav.klage.innsyn.api.view.InnsynResponse
import no.nav.klage.innsyn.api.view.SakView
import no.nav.klage.kodeverk.TimeUnitType
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.repositories.BehandlingRepository
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class InnsynService(
    private val behandlingRepository: BehandlingRepository,
) {

    data class GroupByKey(
        val fagsystemId: String,
        val fagsakId: String,
    )

    private fun Behandling.toGroupByKey(): GroupByKey {
        return GroupByKey(
            fagsystemId = fagsystem.id,
            fagsakId = fagsakId,
        )
    }

    fun getSakerForBruker(fnr: String): InnsynResponse {
        val behandlingerGroupedBySak: Map<GroupByKey, List<Behandling>> =
            behandlingRepository.findBySakenGjelderPartIdValueAndFeilregistreringIsNull(fnr)
                .groupBy { it.toGroupByKey() }

        return InnsynResponse(
            saker = behandlingerGroupedBySak.values.map { behandlinger ->
                behandlinger.toSakView()
            },
        )
    }

    private fun List<Behandling>.toSakView(): SakView {
        val firstBehandling = this.first()

        return SakView(
            id = "${firstBehandling.fagsystem.id}_${firstBehandling.fagsakId}",
            saksnummer = firstBehandling.fagsakId,
            ytelseId = firstBehandling.ytelse.id,
            events = this.map { it.getEvents() }.flatten()
                .sortedBy { it.date }, //Will this always be correct when we for example truncate time?
            varsletBehandlingstid = firstBehandling.getVarsletBehandlingstid(),
            mottattKlageinstans = firstBehandling.mottattKlageinstans.toLocalDate(),
        )
    }

    private fun Behandling.getEvents(): List<SakView.Event> {
        val events = mutableListOf<SakView.Event>()
        return when (this) {
            is Klagebehandling -> {
                events += SakView.Event(
                    type = SakView.Event.EventType.KLAGE_MOTTATT_VEDTAKSINSTANS,
                    date = mottattVedtaksinstans.atStartOfDay(),
                )

                events += SakView.Event(
                    type = SakView.Event.EventType.KLAGE_MOTTATT_KLAGEINSTANS,
                    date = mottattKlageinstans,
                )

                if (ferdigstilling != null) {
                    events += SakView.Event(
                        type = SakView.Event.EventType.KLAGE_AVSLUTTET_I_KLAGEINSTANS,
                        date = ferdigstilling!!.avsluttetAvSaksbehandler,
                    )
                }
                events
            }

            is AnkeITrygderettenbehandling -> {
                events += SakView.Event(
                    type = SakView.Event.EventType.ANKE_SENDT_TRYGDERETTEN,
                    date = sendtTilTrygderetten,
                )

                if (kjennelseMottatt != null) {
                    events += SakView.Event(
                        type = SakView.Event.EventType.ANKE_KJENNELSE_MOTTATT_FRA_TRYGDERETTEN,
                        date = kjennelseMottatt!!,
                    )
                }

                if (ferdigstilling != null && !shouldCreateNewBehandlingEtterTROpphevet() && !shouldCreateNewAnkebehandling()) {
                    events += SakView.Event(
                        type = SakView.Event.EventType.ANKE_AVSLUTTET_I_TRYGDERETTEN,
                        date = ferdigstilling!!.avsluttetAvSaksbehandler,
                    )
                }
                events
            }

            is Ankebehandling -> {
                //if from Kabin or created in Kabal
                if (mottakId != null) {
                    events += SakView.Event(
                        type = SakView.Event.EventType.ANKE_MOTTATT_KLAGEINSTANS,
                        date = mottattKlageinstans,
                    )
                } else {
                    //If created in Kabal. Do we need this?
                }

                if (ferdigstilling != null && !shouldBeSentToTrygderetten() && !shouldCreateNewAnkebehandling() && !shouldCreateNewBehandlingEtterTROpphevet()) {
                    events += SakView.Event(
                        type = SakView.Event.EventType.ANKE_AVSLUTTET_I_KLAGEINSTANS,
                        date = ferdigstilling!!.avsluttetAvSaksbehandler,
                    )
                }
                events
            }

            is BehandlingEtterTrygderettenOpphevet -> {
                if (ferdigstilling != null) {
                    events += SakView.Event(
                        type = SakView.Event.EventType.ANKE_AVSLUTTET_I_KLAGEINSTANS,
                        date = ferdigstilling!!.avsluttetAvSaksbehandler,
                    )
                }
                events
            }

            is Omgjoeringskravbehandling -> {
                if (ferdigstilling != null) {
                    events += SakView.Event(
                        type = SakView.Event.EventType.ANKE_AVSLUTTET_I_KLAGEINSTANS,
                        date = ferdigstilling!!.avsluttetAvSaksbehandler,
                    )
                }
                events
            }
        }
    }

    private fun getVarsletBehandlingstidView(
        varsletFrist: LocalDate?,
        varsletBehandlingstidUnits: Int?,
        varsletBehandlingstidUnitType: TimeUnitType?
    ): SakView.VarsletBehandlingstid? {
        if (varsletFrist == null && varsletBehandlingstidUnits == null && varsletBehandlingstidUnitType == null) {
            return null
        }

        return SakView.VarsletBehandlingstid(
            varsletBehandlingstidUnits = varsletBehandlingstidUnits!!,
            varsletBehandlingstidUnitTypeId = varsletBehandlingstidUnitType!!.id,
            varsletFrist = varsletFrist!!
        )
    }

    fun Behandling.getVarsletBehandlingstid(): SakView.VarsletBehandlingstid? {
        return when (this) {
            is AnkeITrygderettenbehandling -> null
            is Klagebehandling -> getVarsletBehandlingstidView(
                this.varsletFrist,
                this.varsletBehandlingstidUnits,
                this.varsletBehandlingstidUnitType
            )

            is Ankebehandling -> getVarsletBehandlingstidView(
                this.varsletFrist,
                this.varsletBehandlingstidUnits,
                this.varsletBehandlingstidUnitType
            )

            is BehandlingEtterTrygderettenOpphevet -> getVarsletBehandlingstidView(
                this.varsletFrist,
                this.varsletBehandlingstidUnits,
                this.varsletBehandlingstidUnitType
            )

            is Omgjoeringskravbehandling -> getVarsletBehandlingstidView(
                this.varsletFrist,
                this.varsletBehandlingstidUnits,
                this.varsletBehandlingstidUnitType
            )
        }
    }
}
