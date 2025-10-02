package no.nav.klage.innsyn.service

import no.nav.klage.innsyn.api.view.InnsynResponse
import no.nav.klage.innsyn.api.view.SakView
import no.nav.klage.kodeverk.TimeUnitType
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.innsendingsytelse.Innsendingsytelse
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.domain.behandling.*
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
            is Gjenopptaksbehandling -> Type.BEGJAERING_OM_GJENOPPTAK
            is GjenopptakITrygderettenbehandling -> Type.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN
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
                    is Gjenopptaksbehandling -> it.getEvents()
                    is GjenopptakITrygderettenbehandling -> it.getEvents()
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

        if (ferdigstilling != null && shouldNotCreateNewBehandling()) {
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

        if (ferdigstilling != null && !shouldBeSentToTrygderetten()) {
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

    private fun Gjenopptaksbehandling.getEvents(): List<SakView.Event> {
        val events = mutableListOf<SakView.Event>()

        //if from Kabin or created in Kabal
        if (mottakId != null) {
            events += SakView.Event(
                type = SakView.Event.EventType.GJENOPPTAKSBEGJAERING_MOTTATT_KLAGEINSTANS,
                date = mottattKlageinstans,
                relevantDocuments = getRelevantDocuments(SakView.Event.EventType.GJENOPPTAKSBEGJAERING_MOTTATT_KLAGEINSTANS, this),
            )
        } else {
            //If created in Kabal, don't show to user.
        }

        if (ferdigstilling != null && !shouldBeSentToTrygderetten()) {
            events += SakView.Event(
                type = SakView.Event.EventType.GJENOPPTAKSBEGJAERING_AVSLUTTET_I_KLAGEINSTANS,
                date = ferdigstilling!!.avsluttetAvSaksbehandler,
                relevantDocuments = listOf(),
            )
        }
        return events
    }

    private fun GjenopptakITrygderettenbehandling.getEvents(): List<SakView.Event> {
        val events = mutableListOf<SakView.Event>()
        events += SakView.Event(
            type = SakView.Event.EventType.GJENOPPTAKSBEGJAERING_SENDT_TRYGDERETTEN,
            date = sendtTilTrygderetten,
            relevantDocuments = listOf(),
        )

        if (kjennelseMottatt != null) {
            events += SakView.Event(
                type = SakView.Event.EventType.GJENOPPTAKSBEGJAERING_KJENNELSE_MOTTATT_FRA_TRYGDERETTEN,
                date = kjennelseMottatt!!,
                relevantDocuments = listOf(),
            )
        }

        if (ferdigstilling != null && shouldNotCreateNewBehandling()) {
            events += SakView.Event(
                type = SakView.Event.EventType.GJENOPPTAKSBEGJAERING_AVSLUTTET_I_TRYGDERETTEN,
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
            SakView.Event.EventType.KLAGE_MOTTATT_KLAGEINSTANS, SakView.Event.EventType.ANKE_MOTTATT_KLAGEINSTANS, SakView.Event.EventType.OMGJOERINGSKRAV_MOTTATT_KLAGEINSTANS, SakView.Event.EventType.GJENOPPTAKSBEGJAERING_MOTTATT_KLAGEINSTANS -> {
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
        if (varsletFrist == null) {
            return null
        }

        return SakView.VarsletBehandlingstid(
            varsletBehandlingstidUnits = varsletBehandlingstidUnits,
            varsletBehandlingstidUnitTypeId = varsletBehandlingstidUnitType?.id,
            varsletFrist = varsletFrist
        )
    }

    fun Behandling.getVarsletBehandlingstid(): SakView.VarsletBehandlingstid? {
        return when (this) {
            is BehandlingWithVarsletBehandlingstid -> getVarsletBehandlingstidView(
                this.varsletBehandlingstid?.varsletFrist,
                this.varsletBehandlingstid?.varsletBehandlingstidUnits,
                this.varsletBehandlingstid?.varsletBehandlingstidUnitType,
            )
            else -> null
        }
    }
}

/**
 * Used when providing link to ettersendelse. If no matching innsendingsytelse, go to nav.no/klage instead.
 */
fun Ytelse.mapYtelseToInnsendingsytelse(): Innsendingsytelse? {
    return when (this) {
        Ytelse.FOR_FOR -> Innsendingsytelse.FORELDREPENGER
        Ytelse.FOR_SVA -> Innsendingsytelse.SVANGERSKAPSPENGER
        Ytelse.FOR_ENG -> Innsendingsytelse.ENGANGSSTONAD
        Ytelse.OMS_OMP -> Innsendingsytelse.OMSORGSPENGER_HJEMME_MED_SYKT_BARN_DAGER
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
        Ytelse.BID_BBF -> Innsendingsytelse.BARNEBIDRAG
        Ytelse.BID_BII -> Innsendingsytelse.BARNEBIDRAG
        Ytelse.DAG_DAG -> Innsendingsytelse.DAGPENGER
        Ytelse.ENF_ENF -> Innsendingsytelse.ENSLIG_MOR_ELLER_FAR
        Ytelse.GEN_GEN -> Innsendingsytelse.LONNSGARANTI
        Ytelse.GRA_GRA -> Innsendingsytelse.GRAVFERDSSTONAD
        Ytelse.GRU_HJE -> Innsendingsytelse.HJELPESTONAD
        Ytelse.GRU_GRU -> Innsendingsytelse.GRUNNSTONAD
        Ytelse.HJE_HJE -> Innsendingsytelse.HJELPEMIDLER
        Ytelse.KON_KON -> Innsendingsytelse.KONTANTSTOTTE
        Ytelse.MED_MED -> Innsendingsytelse.MEDLEMSKAP
        Ytelse.PEN_ALD -> Innsendingsytelse.ALDERSPENSJON
        Ytelse.PEN_BAR -> Innsendingsytelse.BARNEPENSJON
        Ytelse.PEN_AFP -> null //don't know which to choose
        Ytelse.PEN_KRI -> Innsendingsytelse.KRIGSPENSJON
        Ytelse.PEN_GJE -> Innsendingsytelse.GJENLEVENDE
        Ytelse.PEN_EYO -> Innsendingsytelse.OMSTILLINGSSTONAD
        Ytelse.SUP_PEN -> Innsendingsytelse.SUPPLERENDE_STONAD
        Ytelse.SUP_UFF -> Innsendingsytelse.SUPPLERENDE_STONAD_UFORE_FLYKTNINGER//
        Ytelse.TIL_TIP -> null //? In Klang, tema is IND. Tema TIL is not used.
        Ytelse.TIL_TIL -> null //? In Klang, tema is IND. Tema TIL is not used.
        Ytelse.UFO_UFO -> Innsendingsytelse.UFORETRYGD
        Ytelse.YRK_YRK -> Innsendingsytelse.YRKESSKADE
        Ytelse.YRK_MEN -> Innsendingsytelse.MENERSTATNING_VED_YRKESSKADE_ELLER_YRKESSYKDOM
        Ytelse.YRK_YSY -> Innsendingsytelse.MENERSTATNING_VED_YRKESSKADE_ELLER_YRKESSYKDOM
        Ytelse.UFO_TVF -> Innsendingsytelse.UFORETRYGD
        Ytelse.OPP_OPP -> Innsendingsytelse.OPPFOLGING
        Ytelse.AAR_AAR -> null //no matching innsendingsytelse
        Ytelse.TSR_TSR -> null //could be Innsendingsytelse.STOTTE_TIL_ARBEIDS_OG_UTDANNINGSREISER, but tema does not match with Klang.
        Ytelse.FRI_FRI -> null //no matching innsendingsytelse/tema
        Ytelse.TSO_TSO -> Innsendingsytelse.TILLEGGSSTONADER
        Ytelse.FAR_FAR -> null //no matching innsendingsytelse/tema
        Ytelse.DAG_LKP -> Innsendingsytelse.DAGPENGER
        Ytelse.DAG_FDP -> Innsendingsytelse.DAGPENGER
        Ytelse.BIL_BIL -> Innsendingsytelse.BILSTONAD
        Ytelse.HEL_HEL -> Innsendingsytelse.HJELPEMIDLER_ORTOPEDISKE
        Ytelse.FOS_FOS -> Innsendingsytelse.FORSIKRING
        Ytelse.PAR_PAR -> null //no matching innsendingsytelse/tema
    }
}