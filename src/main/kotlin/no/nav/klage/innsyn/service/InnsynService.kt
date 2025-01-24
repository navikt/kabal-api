package no.nav.klage.innsyn.service

import no.nav.klage.innsyn.api.view.InnsynResponse
import no.nav.klage.innsyn.api.view.SakView
import no.nav.klage.kodeverk.TimeUnitType
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.innsendingsytelse.Innsendingsytelse
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.repositories.BehandlingRepository
import org.springframework.stereotype.Service
import java.nio.file.Path
import java.time.LocalDate

@Service
class InnsynService(
    private val behandlingRepository: BehandlingRepository,
    private val documentService: DocumentService,
) {

    data class GroupByKey(
        val fagsystemId: String,
        val fagsakId: String,
        val type: Type,
    )

    private fun Behandling.toGroupByKey(): GroupByKey {
        return GroupByKey(
            fagsystemId = fagsystem.id,
            fagsakId = fagsakId,
            type = toBasicType(),
        )
    }

    fun getJournalpostPdf(journalpostId: String): Pair<Path, String> {
        return documentService.getJournalpostPdf(journalpostId = journalpostId)
    }

    private fun Behandling.toBasicType(): Type {
        return when (this) {
            is Klagebehandling -> Type.KLAGE
            is AnkeITrygderettenbehandling -> Type.ANKE
            is Ankebehandling -> Type.ANKE
            is BehandlingEtterTrygderettenOpphevet -> Type.ANKE
            is Omgjoeringskravbehandling -> Type.OMGJOERINGSKRAV
        }
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
            id = "${firstBehandling.fagsystem.id}_${firstBehandling.fagsakId}_${firstBehandling.toBasicType().id}",
            saksnummer = firstBehandling.fagsakId,
            ytelseId = firstBehandling.ytelse.id,
            innsendingsytelseId = firstBehandling.ytelse.mapYtelseToInnsendingsytelse()?.id ?: "ukjent",
            events = this.map {
                when (it) {
                    is Klagebehandling -> it.getEvents()
                    is AnkeITrygderettenbehandling -> it.getEvents()
                    is Ankebehandling -> it.getEvents()
                    is BehandlingEtterTrygderettenOpphevet -> it.getEvents()
                    is Omgjoeringskravbehandling -> it.getEvents()
                }
            }.flatten()
                .sortedBy { it.date }, //Will this always be correct when we for example truncate time?
            varsletBehandlingstid = firstBehandling.getVarsletBehandlingstid(),
            mottattKlageinstans = firstBehandling.mottattKlageinstans.toLocalDate(),
            typeId = firstBehandling.toBasicType().id,
            finishedDate = if (this.none { it.ferdigstilling == null }) {
                this.mapNotNull { it.ferdigstilling?.avsluttetAvSaksbehandler }.maxOrNull()?.toLocalDate()
            } else {
                null
            },
        )
    }

    private fun Klagebehandling.getEvents(): List<SakView.Event> {
        val events = mutableListOf<SakView.Event>()
        events += SakView.Event(
            type = SakView.Event.EventType.KLAGE_MOTTATT_VEDTAKSINSTANS,
            date = mottattVedtaksinstans.atStartOfDay(),
            relevantDocuments = listOf(),
        )

        events += SakView.Event(
            type = SakView.Event.EventType.KLAGE_MOTTATT_KLAGEINSTANS,
            date = mottattKlageinstans,
            relevantDocuments = getRelevantDocuments(SakView.Event.EventType.KLAGE_MOTTATT_KLAGEINSTANS, this),
        )

        if (ferdigstilling != null) {
            events += SakView.Event(
                type = SakView.Event.EventType.KLAGE_AVSLUTTET_I_KLAGEINSTANS,
                date = ferdigstilling!!.avsluttetAvSaksbehandler,
                relevantDocuments = listOf(),
            )
        }

        return events
    }

    private fun Omgjoeringskravbehandling.getEvents(): List<SakView.Event> {
        val events = mutableListOf<SakView.Event>()
        events += SakView.Event(
            type = SakView.Event.EventType.OMGJOERINGSKRAV_MOTTATT_KLAGEINSTANS,
            date = mottattKlageinstans,
            relevantDocuments = getRelevantDocuments(SakView.Event.EventType.OMGJOERINGSKRAV_MOTTATT_KLAGEINSTANS, this),
        )

        if (ferdigstilling != null) {
            events += SakView.Event(
                type = SakView.Event.EventType.OMGJOERINGSKRAV_AVSLUTTET_I_KLAGEINSTANS,
                date = ferdigstilling!!.avsluttetAvSaksbehandler,
                relevantDocuments = listOf(),
            )
        }

        return events
    }

    private fun AnkeITrygderettenbehandling.getEvents(): List<SakView.Event> {
        val events = mutableListOf<SakView.Event>()
        events += SakView.Event(
            type = SakView.Event.EventType.ANKE_SENDT_TRYGDERETTEN,
            date = sendtTilTrygderetten,
            relevantDocuments = listOf(),
        )

        if (kjennelseMottatt != null) {
            events += SakView.Event(
                type = SakView.Event.EventType.ANKE_KJENNELSE_MOTTATT_FRA_TRYGDERETTEN,
                date = kjennelseMottatt!!,
                relevantDocuments = listOf(),
            )
        }

        if (ferdigstilling != null && !shouldCreateNewBehandlingEtterTROpphevet() && !shouldCreateNewAnkebehandling()) {
            events += SakView.Event(
                type = SakView.Event.EventType.ANKE_AVSLUTTET_I_TRYGDERETTEN,
                date = ferdigstilling!!.avsluttetAvSaksbehandler,
                relevantDocuments = listOf(),
            )
        }
        return events
    }

    private fun Ankebehandling.getEvents(): List<SakView.Event> {
        val events = mutableListOf<SakView.Event>()

        //if from Kabin or created in Kabal
        if (mottakId != null) {
            events += SakView.Event(
                type = SakView.Event.EventType.ANKE_MOTTATT_KLAGEINSTANS,
                date = mottattKlageinstans,
                relevantDocuments = getRelevantDocuments(SakView.Event.EventType.ANKE_MOTTATT_KLAGEINSTANS, this),
            )
        } else {
            //If created in Kabal, don't show to user.
        }

        if (ferdigstilling != null && !shouldBeSentToTrygderetten() && !shouldCreateNewAnkebehandling() && !shouldCreateNewBehandlingEtterTROpphevet()) {
            events += SakView.Event(
                type = SakView.Event.EventType.ANKE_AVSLUTTET_I_KLAGEINSTANS,
                date = ferdigstilling!!.avsluttetAvSaksbehandler,
                relevantDocuments = listOf(),
            )
        }
        return events
    }

    private fun BehandlingEtterTrygderettenOpphevet.getEvents(): List<SakView.Event> {
        val events = mutableListOf<SakView.Event>()
        if (ferdigstilling != null) {
            events += SakView.Event(
                type = SakView.Event.EventType.ANKE_AVSLUTTET_I_KLAGEINSTANS,
                date = ferdigstilling!!.avsluttetAvSaksbehandler,
                relevantDocuments = listOf(),
            )
        }
        return events
    }

    private fun getRelevantDocuments(
        eventType: SakView.Event.EventType,
        behandling: Behandling
    ): List<SakView.Event.EventDocument> {
        return when (eventType) {
            SakView.Event.EventType.KLAGE_MOTTATT_KLAGEINSTANS, SakView.Event.EventType.ANKE_MOTTATT_KLAGEINSTANS, SakView.Event.EventType.OMGJOERINGSKRAV_MOTTATT_KLAGEINSTANS  -> {
                val svarbrev = getSvarbrev(behandling)
                return if (svarbrev != null) listOf(svarbrev) else listOf()
            }
            else -> listOf()
        }
    }

    private fun getSvarbrev(behandling: Behandling): SakView.Event.EventDocument? {
        return documentService.getSvarbrev(behandling)
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