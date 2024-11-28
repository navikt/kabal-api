package no.nav.klage.innsyn.service

import no.nav.klage.innsyn.api.view.InnsynResponse
import no.nav.klage.innsyn.api.view.SakView
import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.kodeverk.innsendingsytelse.Innsendingsytelse
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.repositories.BehandlingRepository
import org.springframework.stereotype.Service

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
            innsendingsytelseId = firstBehandling.ytelse.mapYtelseToInnsendingsytelse()?.id,
            events = this.map { it.getEvents() }.flatten().sortedBy { it.date }, //Will this always be correct when we for example truncate time?
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
}

fun Ytelse.mapYtelseToInnsendingsytelse(): Innsendingsytelse? {
    return when (this) {
        Ytelse.FOR_FOR -> Innsendingsytelse.FORELDREPENGER
        Ytelse.FOR_SVA -> Innsendingsytelse.SVANGERSKAPSPENGER
        Ytelse.FOR_ENG -> Innsendingsytelse.ENGANGSSTONAD
        Ytelse.OMS_OMP -> Innsendingsytelse.SYKDOM_I_FAMILIEN
        Ytelse.OMS_OLP -> Innsendingsytelse.OPPLARINGSPENGER
        Ytelse.OMS_PSB -> Innsendingsytelse.PLEIEPENGER_FOR_SYKT_BARN
        Ytelse.OMS_PLS -> Innsendingsytelse.PLEIEPENGER_I_LIVETS_SLUTTFASE
        Ytelse.SYK_SYK -> Innsendingsytelse.SYKEPENGER
        Ytelse.AAP_AAP -> Innsendingsytelse.ARBEIDSAVKLARINGSPENGER
        Ytelse.BAR_BAR -> Innsendingsytelse.BARNETRYGD
        Ytelse.BID_BAB -> Innsendingsytelse.BARNEBIDRAG
        Ytelse.BID_BIF -> Innsendingsytelse.BIDRAGSFORSKUDD
        Ytelse.BID_OPI -> Innsendingsytelse.OPPFOSTRINGSBIDRAG
        Ytelse.BID_EKB -> Innsendingsytelse.EKTEFELLEBIDRAG
        Ytelse.BID_BII -> null
        Ytelse.DAG_DAG -> Innsendingsytelse.DAGPENGER
        Ytelse.ENF_ENF -> Innsendingsytelse.ENSLIG_MOR_ELLER_FAR
        Ytelse.GEN_GEN -> Innsendingsytelse.LONNSGARANTI
        Ytelse.GRA_GRA -> Innsendingsytelse.GRAVFERDSSTONAD
        Ytelse.GRU_HJE -> Innsendingsytelse.HJELPESTONAD
        Ytelse.GRU_GRU -> Innsendingsytelse.GRUNNSTONAD
        Ytelse.HJE_HJE -> Innsendingsytelse.HJELPEMIDLER
        Ytelse.KON_KON -> Innsendingsytelse.KONTANTSTOTTE
        Ytelse.MED_MED -> null
        Ytelse.PEN_ALD -> Innsendingsytelse.ALDERSPENSJON
        Ytelse.PEN_BAR -> Innsendingsytelse.BARNEPENSJON
        Ytelse.PEN_AFP -> null
        Ytelse.PEN_KRI -> Innsendingsytelse.KRIGSPENSJON
        Ytelse.PEN_GJE -> Innsendingsytelse.GJENLEVENDE
        Ytelse.PEN_EYO -> Innsendingsytelse.OMSTILLINGSSTONAD
        Ytelse.SUP_PEN -> Innsendingsytelse.SUPPLERENDE_STONAD
        Ytelse.SUP_UFF -> Innsendingsytelse.SUPPLERENDE_STONAD_UFORE_FLYKTNINGER
        Ytelse.TIL_TIP -> Innsendingsytelse.TILTAKSPENGER
        Ytelse.TIL_TIL -> null
        Ytelse.UFO_UFO -> Innsendingsytelse.UFORETRYGD
        Ytelse.YRK_YRK -> Innsendingsytelse.YRKESSKADE
        Ytelse.YRK_MEN -> Innsendingsytelse.MENERSTATNING_VED_YRKESSKADE_ELLER_YRKESSYKDOM
        Ytelse.YRK_YSY -> Innsendingsytelse.MENERSTATNING_VED_YRKESSKADE_ELLER_YRKESSYKDOM
        Ytelse.UFO_TVF -> null
        Ytelse.OPP_OPP -> null
        Ytelse.AAR_AAR -> null
        Ytelse.TSR_TSR -> Innsendingsytelse.STOTTE_TIL_ARBEIDS_OG_UTDANNINGSREISER
        Ytelse.FRI_FRI -> null
        Ytelse.TSO_TSO -> Innsendingsytelse.TILLEGGSSTONADER
        Ytelse.FAR_FAR -> null
        Ytelse.BID_BBF -> null
        Ytelse.DAG_LKP -> null
        Ytelse.DAG_FDP -> null
        Ytelse.BIL_BIL -> null
        Ytelse.HEL_HEL -> null
        Ytelse.FOS_FOS -> null
    }
}