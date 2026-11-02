package no.nav.klage.innsyn.service

import no.nav.klage.innsyn.api.view.InnsynResponse
import no.nav.klage.innsyn.api.view.SakView
import no.nav.klage.kodeverk.TimeUnitType
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.innsendingsytelse.Innsendingsytelse
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingEtter2027
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.AnkebehandlingEtter2027
import no.nav.klage.oppgave.domain.behandling.AnkebehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.BehandlingEtterTrygderettenOpphevet
import no.nav.klage.oppgave.domain.behandling.BehandlingWithVarsletBehandlingstid
import no.nav.klage.oppgave.domain.behandling.GjenopptakITrygderettenbehandlingEtter2027
import no.nav.klage.oppgave.domain.behandling.GjenopptakITrygderettenbehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.GjenopptaksbehandlingEtter2027
import no.nav.klage.oppgave.domain.behandling.GjenopptaksbehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.Klagebehandling
import no.nav.klage.oppgave.domain.behandling.Omgjoeringskravbehandling
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
        val sakKey: String,
        val type: Type,
    )

    private fun Behandling.toGroupByKey(): GroupByKey =
        GroupByKey(
            fagsystemId = fagsystem.id,
            sakKey = sakKey(),
            type = toBasicType(),
        )

    // Generelle saker har ikke fagsakId. Grupper da på arkivsaksnummer, eller hver behandling for seg.
    private fun Behandling.sakKey(): String = fagsakId ?: arkivsaksnummer?.let { "arkiv-$it" } ?: "behandling-$id"

    fun getJournalpostPdf(journalpostId: String): Pair<Path, String> = documentService.getJournalpostPdf(journalpostId = journalpostId)

    private fun Behandling.toBasicType(): Type =
        when (this) {
            is Klagebehandling -> Type.KLAGE

            is AnkebehandlingFoer2027, is AnkeITrygderettenbehandlingFoer2027 -> Type.ANKE_FOER_2027

            is AnkebehandlingEtter2027, is AnkeITrygderettenbehandlingEtter2027 -> Type.ANKE_ETTER_2027

            is BehandlingEtterTrygderettenOpphevet -> Type.ANKE_FOER_2027

            is Omgjoeringskravbehandling -> Type.OMGJOERINGSKRAV

            is GjenopptaksbehandlingFoer2027, is GjenopptakITrygderettenbehandlingFoer2027 -> Type.BEGJAERING_OM_GJENOPPTAK_FOER_2027

            is GjenopptaksbehandlingEtter2027,
            is GjenopptakITrygderettenbehandlingEtter2027,
            -> Type.BEGJAERING_OM_GJENOPPTAK_ETTER_2027

            else -> error("Unknown Behandling subtype: ${this::class.java.name}")
        }

    fun getSakerForBruker(fnr: String): InnsynResponse {
        val behandlingerGroupedBySak: Map<GroupByKey, List<Behandling>> =
            behandlingRepository
                .findBySakenGjelderPartIdValueAndFeilregistreringIsNull(fnr)
                .groupBy { it.toGroupByKey() }

        return InnsynResponse(
            saker =
                behandlingerGroupedBySak.values.map { behandlinger ->
                    behandlinger.toSakView()
                },
        )
    }

    private fun List<Behandling>.toSakView(): SakView {
        val firstBehandling = this.first()

        return SakView(
            id = "${firstBehandling.fagsystem.id}_${firstBehandling.sakKey()}_${firstBehandling.toBasicType().id}",
            saksnummer = firstBehandling.getSaksnummer(),
            ytelseId = firstBehandling.ytelse.id,
            innsendingsytelseId =
                getIdNullIfMoreThanOne(firstBehandling.ytelse),
            innsendingsytelseIdList = firstBehandling.ytelse.mapYtelseToInnsendingsytelseList().map { it.id },
            events =
                this
                    .map {
                        when (it) {
                            is Klagebehandling -> it.getEvents()
                            is AnkeITrygderettenbehandlingFoer2027 -> it.getEvents()
                            is AnkeITrygderettenbehandlingEtter2027 -> it.getEvents()
                            is AnkebehandlingFoer2027 -> it.getEvents()
                            is AnkebehandlingEtter2027 -> it.getEvents()
                            is BehandlingEtterTrygderettenOpphevet -> it.getEvents()
                            is Omgjoeringskravbehandling -> it.getEvents()
                            is GjenopptaksbehandlingFoer2027 -> it.getEvents()
                            is GjenopptaksbehandlingEtter2027 -> it.getEvents()
                            is GjenopptakITrygderettenbehandlingFoer2027 -> it.getEvents()
                            is GjenopptakITrygderettenbehandlingEtter2027 -> it.getEvents()
                            else -> error("Unknown Behandling subtype: ${it::class.java.name}")
                        }
                    }.flatten()
                    .sortedBy { it.date },
            // Will this always be correct when we for example truncate time?
            varsletBehandlingstid = firstBehandling.getVarsletBehandlingstid(),
            mottattKlageinstans = firstBehandling.mottattKlageinstans.toLocalDate(),
            typeId = firstBehandling.toBasicType().id,
            finishedDate =
                if (this.none { it.ferdigstilling == null }) {
                    this.mapNotNull { it.ferdigstilling?.avsluttetAvSaksbehandler }.maxOrNull()?.toLocalDate()
                } else {
                    null
                },
        )
    }

    private fun Klagebehandling.getEvents(): List<SakView.Event> {
        val events = mutableListOf<SakView.Event>()
        events +=
            SakView.Event(
                type = SakView.Event.EventType.KLAGE_MOTTATT_VEDTAKSINSTANS,
                date = mottattVedtaksinstans.atStartOfDay(),
                relevantDocuments = listOf(),
            )

        events +=
            SakView.Event(
                type = SakView.Event.EventType.KLAGE_MOTTATT_KLAGEINSTANS,
                date = mottattKlageinstans,
                relevantDocuments = getRelevantDocuments(eventType = SakView.Event.EventType.KLAGE_MOTTATT_KLAGEINSTANS, behandling = this),
            )

        if (ferdigstilling != null) {
            events +=
                SakView.Event(
                    type = SakView.Event.EventType.KLAGE_AVSLUTTET_I_KLAGEINSTANS,
                    date = ferdigstilling!!.avsluttetAvSaksbehandler,
                    relevantDocuments = listOf(),
                )
        }

        return events
    }

    private fun Omgjoeringskravbehandling.getEvents(): List<SakView.Event> {
        val events = mutableListOf<SakView.Event>()
        events +=
            SakView.Event(
                type = SakView.Event.EventType.OMGJOERINGSKRAV_MOTTATT_KLAGEINSTANS,
                date = mottattKlageinstans,
                relevantDocuments =
                    getRelevantDocuments(
                        eventType = SakView.Event.EventType.OMGJOERINGSKRAV_MOTTATT_KLAGEINSTANS,
                        behandling = this,
                    ),
            )

        if (ferdigstilling != null) {
            events +=
                SakView.Event(
                    type = SakView.Event.EventType.OMGJOERINGSKRAV_AVSLUTTET_I_KLAGEINSTANS,
                    date = ferdigstilling!!.avsluttetAvSaksbehandler,
                    relevantDocuments = listOf(),
                )
        }

        return events
    }

    private fun AnkeITrygderettenbehandlingFoer2027.getEvents(): List<SakView.Event> {
        val events = mutableListOf<SakView.Event>()
        events +=
            SakView.Event(
                type = SakView.Event.EventType.ANKE_SENDT_TRYGDERETTEN,
                date = sendtTilTrygderetten,
                relevantDocuments = listOf(),
            )

        if (kjennelseMottatt != null) {
            events +=
                SakView.Event(
                    type = SakView.Event.EventType.ANKE_KJENNELSE_MOTTATT_FRA_TRYGDERETTEN,
                    date = kjennelseMottatt!!,
                    relevantDocuments = listOf(),
                )
        }

        if (ferdigstilling != null && shouldNotCreateNewBehandling()) {
            events +=
                SakView.Event(
                    type = SakView.Event.EventType.ANKE_AVSLUTTET_I_TRYGDERETTEN,
                    date = ferdigstilling!!.avsluttetAvSaksbehandler,
                    relevantDocuments = listOf(),
                )
        }
        return events
    }

    private fun AnkeITrygderettenbehandlingEtter2027.getEvents(): List<SakView.Event> {
        val events = mutableListOf<SakView.Event>()
        events +=
            SakView.Event(
                type = SakView.Event.EventType.ANKE_SENDT_TRYGDERETTEN,
                date = sendtTilTrygderetten,
                relevantDocuments = listOf(),
            )

        if (kjennelseMottatt != null) {
            events +=
                SakView.Event(
                    type = SakView.Event.EventType.ANKE_KJENNELSE_MOTTATT_FRA_TRYGDERETTEN,
                    date = kjennelseMottatt!!,
                    relevantDocuments = listOf(),
                )
        }

        if (ferdigstilling != null && shouldNotCreateNewBehandling()) {
            events +=
                SakView.Event(
                    type = SakView.Event.EventType.ANKE_AVSLUTTET_I_TRYGDERETTEN,
                    date = ferdigstilling!!.avsluttetAvSaksbehandler,
                    relevantDocuments = listOf(),
                )
        }
        return events
    }

    private fun AnkebehandlingFoer2027.getEvents(): List<SakView.Event> {
        val events = mutableListOf<SakView.Event>()

        if (initiatingSystem != Behandling.InitiatingSystem.KABAL) {
            // if from Kabin or sent from vedtaksløsning
            events +=
                SakView.Event(
                    type = SakView.Event.EventType.ANKE_MOTTATT_KLAGEINSTANS,
                    date = mottattKlageinstans,
                    relevantDocuments =
                        getRelevantDocuments(
                            eventType = SakView.Event.EventType.ANKE_MOTTATT_KLAGEINSTANS,
                            behandling = this,
                        ),
                )
        } else {
            // If created in Kabal, don't show to user.
        }

        if (ferdigstilling != null && !shouldBeSentToTrygderetten()) {
            events +=
                SakView.Event(
                    type = SakView.Event.EventType.ANKE_AVSLUTTET_I_KLAGEINSTANS,
                    date = ferdigstilling!!.avsluttetAvSaksbehandler,
                    relevantDocuments = listOf(),
                )
        }
        return events
    }

    private fun AnkebehandlingEtter2027.getEvents(): List<SakView.Event> {
        val events = mutableListOf<SakView.Event>()

        if (initiatingSystem != Behandling.InitiatingSystem.KABAL) {
            // if from Kabin or sent from vedtaksløsning
            events +=
                SakView.Event(
                    type = SakView.Event.EventType.ANKE_MOTTATT_KLAGEINSTANS,
                    date = mottattKlageinstans,
                    relevantDocuments =
                        getRelevantDocuments(
                            eventType = SakView.Event.EventType.ANKE_MOTTATT_KLAGEINSTANS,
                            behandling = this,
                        ),
                )
        } else {
            // If created in Kabal, don't show to user.
        }

        if (ferdigstilling != null && !shouldBeSentToTrygderetten()) {
            events +=
                SakView.Event(
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
            events +=
                SakView.Event(
                    type = SakView.Event.EventType.ANKE_AVSLUTTET_I_KLAGEINSTANS,
                    date = ferdigstilling!!.avsluttetAvSaksbehandler,
                    relevantDocuments = listOf(),
                )
        }
        return events
    }

    private fun GjenopptaksbehandlingFoer2027.getEvents(): List<SakView.Event> {
        val events = mutableListOf<SakView.Event>()

        if (initiatingSystem != Behandling.InitiatingSystem.KABAL) {
            // if from Kabin or sent from vedtaksløsning
            events +=
                SakView.Event(
                    type = SakView.Event.EventType.GJENOPPTAKSBEGJAERING_MOTTATT_KLAGEINSTANS,
                    date = mottattKlageinstans,
                    relevantDocuments =
                        getRelevantDocuments(
                            eventType = SakView.Event.EventType.GJENOPPTAKSBEGJAERING_MOTTATT_KLAGEINSTANS,
                            behandling = this,
                        ),
                )
        } else {
            // If created in Kabal, don't show to user.
        }

        if (ferdigstilling != null && !shouldBeSentToTrygderetten()) {
            events +=
                SakView.Event(
                    type = SakView.Event.EventType.GJENOPPTAKSBEGJAERING_AVSLUTTET_I_KLAGEINSTANS,
                    date = ferdigstilling!!.avsluttetAvSaksbehandler,
                    relevantDocuments = listOf(),
                )
        }
        return events
    }

    private fun GjenopptaksbehandlingEtter2027.getEvents(): List<SakView.Event> {
        val events = mutableListOf<SakView.Event>()

        if (initiatingSystem != Behandling.InitiatingSystem.KABAL) {
            // if from Kabin or sent from vedtaksløsning
            events +=
                SakView.Event(
                    type = SakView.Event.EventType.GJENOPPTAKSBEGJAERING_MOTTATT_KLAGEINSTANS,
                    date = mottattKlageinstans,
                    relevantDocuments =
                        getRelevantDocuments(
                            eventType = SakView.Event.EventType.GJENOPPTAKSBEGJAERING_MOTTATT_KLAGEINSTANS,
                            behandling = this,
                        ),
                )
        } else {
            // If created in Kabal, don't show to user.
        }

        if (ferdigstilling != null && !shouldBeSentToTrygderetten()) {
            events +=
                SakView.Event(
                    type = SakView.Event.EventType.GJENOPPTAKSBEGJAERING_AVSLUTTET_I_KLAGEINSTANS,
                    date = ferdigstilling!!.avsluttetAvSaksbehandler,
                    relevantDocuments = listOf(),
                )
        }
        return events
    }

    private fun GjenopptakITrygderettenbehandlingFoer2027.getEvents(): List<SakView.Event> {
        val events = mutableListOf<SakView.Event>()
        events +=
            SakView.Event(
                type = SakView.Event.EventType.GJENOPPTAKSBEGJAERING_SENDT_TRYGDERETTEN,
                date = sendtTilTrygderetten,
                relevantDocuments = listOf(),
            )

        if (kjennelseMottatt != null) {
            events +=
                SakView.Event(
                    type = SakView.Event.EventType.GJENOPPTAKSBEGJAERING_KJENNELSE_MOTTATT_FRA_TRYGDERETTEN,
                    date = kjennelseMottatt!!,
                    relevantDocuments = listOf(),
                )
        }

        if (ferdigstilling != null && shouldNotCreateNewBehandling()) {
            events +=
                SakView.Event(
                    type = SakView.Event.EventType.GJENOPPTAKSBEGJAERING_AVSLUTTET_I_TRYGDERETTEN,
                    date = ferdigstilling!!.avsluttetAvSaksbehandler,
                    relevantDocuments = listOf(),
                )
        }
        return events
    }

    private fun GjenopptakITrygderettenbehandlingEtter2027.getEvents(): List<SakView.Event> {
        val events = mutableListOf<SakView.Event>()
        events +=
            SakView.Event(
                type = SakView.Event.EventType.GJENOPPTAKSBEGJAERING_SENDT_TRYGDERETTEN,
                date = sendtTilTrygderetten,
                relevantDocuments = listOf(),
            )

        if (kjennelseMottatt != null) {
            events +=
                SakView.Event(
                    type = SakView.Event.EventType.GJENOPPTAKSBEGJAERING_KJENNELSE_MOTTATT_FRA_TRYGDERETTEN,
                    date = kjennelseMottatt!!,
                    relevantDocuments = listOf(),
                )
        }

        if (ferdigstilling != null && shouldNotCreateNewBehandling()) {
            events +=
                SakView.Event(
                    type = SakView.Event.EventType.GJENOPPTAKSBEGJAERING_AVSLUTTET_I_TRYGDERETTEN,
                    date = ferdigstilling!!.avsluttetAvSaksbehandler,
                    relevantDocuments = listOf(),
                )
        }
        return events
    }

    private fun getRelevantDocuments(
        eventType: SakView.Event.EventType,
        behandling: Behandling,
    ): List<SakView.Event.EventDocument> {
        return when (eventType) {
            SakView.Event.EventType.KLAGE_MOTTATT_KLAGEINSTANS,
            SakView.Event.EventType.ANKE_MOTTATT_KLAGEINSTANS,
            SakView.Event.EventType.OMGJOERINGSKRAV_MOTTATT_KLAGEINSTANS,
            SakView.Event.EventType.GJENOPPTAKSBEGJAERING_MOTTATT_KLAGEINSTANS,
            -> {
                val svarbrev = getSvarbrev(behandling)
                return if (svarbrev != null) listOf(svarbrev) else listOf()
            }

            else -> {
                listOf()
            }
        }
    }

    private fun getSvarbrev(behandling: Behandling): SakView.Event.EventDocument? = documentService.getSvarbrev(behandling)

    private fun getVarsletBehandlingstidView(
        varsletFrist: LocalDate?,
        varsletBehandlingstidUnits: Int?,
        varsletBehandlingstidUnitType: TimeUnitType?,
    ): SakView.VarsletBehandlingstid? {
        if (varsletFrist == null) {
            return null
        }

        return SakView.VarsletBehandlingstid(
            varsletBehandlingstidUnits = varsletBehandlingstidUnits,
            varsletBehandlingstidUnitTypeId = varsletBehandlingstidUnitType?.id,
            varsletFrist = varsletFrist,
        )
    }

    fun Behandling.getVarsletBehandlingstid(): SakView.VarsletBehandlingstid? =
        when (this) {
            is BehandlingWithVarsletBehandlingstid -> {
                getVarsletBehandlingstidView(
                    varsletFrist = this.varsletBehandlingstid?.varsletFrist,
                    varsletBehandlingstidUnits = this.varsletBehandlingstid?.varsletBehandlingstidUnits,
                    varsletBehandlingstidUnitType = this.varsletBehandlingstid?.varsletBehandlingstidUnitType,
                )
            }

            else -> {
                null
            }
        }
}

/**
 * Used when providing link to ettersendelse. If no matching innsendingsytelse, go to nav.no/klage instead.
 */

fun getIdNullIfMoreThanOne(ytelse: Ytelse): String? {
    val innsendingsytelseList = ytelse.mapYtelseToInnsendingsytelseList()
    return if (innsendingsytelseList.isEmpty()) {
        null
    } else if (innsendingsytelseList.size == 1) {
        innsendingsytelseList.first().id
    } else {
        null
    }
}

fun Ytelse.mapYtelseToInnsendingsytelseList(): List<Innsendingsytelse> =
    when (this) {
        Ytelse.FOR_FOR -> listOf(Innsendingsytelse.FORELDREPENGER)

        Ytelse.FOR_SVA -> listOf(Innsendingsytelse.SVANGERSKAPSPENGER)

        Ytelse.FOR_ENG -> listOf(Innsendingsytelse.ENGANGSSTONAD)

        Ytelse.OMS_OMP -> listOf(Innsendingsytelse.OMSORGSPENGER_HJEMME_MED_SYKT_BARN_DAGER)

        Ytelse.OMS_OLP -> listOf(Innsendingsytelse.OPPLARINGSPENGER)

        Ytelse.OMS_PSB -> listOf(Innsendingsytelse.PLEIEPENGER_FOR_SYKT_BARN)

        Ytelse.OMS_PLS -> listOf(Innsendingsytelse.PLEIEPENGER_I_LIVETS_SLUTTFASE)

        Ytelse.SYK_SYK -> listOf(Innsendingsytelse.SYKEPENGER)

        Ytelse.AAP_AAP -> listOf(Innsendingsytelse.ARBEIDSAVKLARINGSPENGER)

        Ytelse.BAR_BAR -> listOf(Innsendingsytelse.BARNETRYGD)

        Ytelse.BID_BAB -> listOf(Innsendingsytelse.BARNEBIDRAG)

        Ytelse.BID_BIF -> listOf(Innsendingsytelse.BIDRAGSFORSKUDD)

        Ytelse.BID_OPI -> listOf(Innsendingsytelse.OPPFOSTRINGSBIDRAG)

        Ytelse.BID_EKB -> listOf(Innsendingsytelse.EKTEFELLEBIDRAG)

        Ytelse.BID_BBF -> listOf(Innsendingsytelse.BARNEBIDRAG)

        Ytelse.BID_BII -> listOf(Innsendingsytelse.BARNEBIDRAG)

        Ytelse.DAG_DAG -> listOf(Innsendingsytelse.DAGPENGER)

        Ytelse.ENF_ENF -> listOf(Innsendingsytelse.ENSLIG_MOR_ELLER_FAR)

        Ytelse.GEN_GEN -> listOf(Innsendingsytelse.LONNSGARANTI)

        Ytelse.GRA_GRA -> listOf(Innsendingsytelse.GRAVFERDSSTONAD)

        Ytelse.GRU_HJE -> listOf(Innsendingsytelse.HJELPESTONAD)

        Ytelse.GRU_GRU -> listOf(Innsendingsytelse.GRUNNSTONAD)

        Ytelse.HJE_HJE -> listOf(Innsendingsytelse.HJELPEMIDLER)

        Ytelse.KON_KON -> listOf(Innsendingsytelse.KONTANTSTOTTE)

        Ytelse.MED_MED -> listOf(Innsendingsytelse.MEDLEMSKAP)

        Ytelse.PEN_ALD -> listOf(Innsendingsytelse.ALDERSPENSJON)

        Ytelse.PEN_BAR -> listOf(Innsendingsytelse.BARNEPENSJON)

        Ytelse.PEN_AFP -> listOf(Innsendingsytelse.AVTALEFESTET_PENSJON_PRIVAT, Innsendingsytelse.AVTALEFESTET_PENSJON_SPK)

        Ytelse.PEN_KRI -> listOf(Innsendingsytelse.KRIGSPENSJON)

        Ytelse.PEN_GJE -> listOf(Innsendingsytelse.GJENLEVENDE)

        Ytelse.PEN_EYO -> listOf(Innsendingsytelse.OMSTILLINGSSTONAD)

        Ytelse.SUP_PEN -> listOf(Innsendingsytelse.SUPPLERENDE_STONAD)

        Ytelse.SUP_UFF -> listOf(Innsendingsytelse.SUPPLERENDE_STONAD_UFORE_FLYKTNINGER)

        Ytelse.TIL_TIP -> listOf(Innsendingsytelse.TILTAKSPENGER)

        Ytelse.TIL_TIL -> emptyList()

        // ? In Klang, tema is IND. Tema TIL is not used.
        Ytelse.UFO_UFO -> listOf(Innsendingsytelse.UFORETRYGD)

        Ytelse.YRK_YRK -> listOf(Innsendingsytelse.YRKESSKADE)

        Ytelse.YRK_MEN -> listOf(Innsendingsytelse.MENERSTATNING_VED_YRKESSKADE_ELLER_YRKESSYKDOM)

        Ytelse.YRK_YSY -> listOf(Innsendingsytelse.MENERSTATNING_VED_YRKESSKADE_ELLER_YRKESSYKDOM)

        Ytelse.UFO_TVF -> listOf(Innsendingsytelse.UFORETRYGD)

        Ytelse.OPP_OPP -> listOf(Innsendingsytelse.OPPFOLGING)

        Ytelse.AAR_AAR -> emptyList()

        // no matching innsendingsytelse
        Ytelse.HJE_AUR -> listOf(Innsendingsytelse.STOTTE_TIL_ARBEIDS_OG_UTDANNINGSREISER)

        Ytelse.TSR_ASO -> listOf(Innsendingsytelse.TILLEGGSSTONADER)

        Ytelse.FRI_FRI -> emptyList()

        // no matching innsendingsytelse/tema
        Ytelse.TSO_TSO -> listOf(Innsendingsytelse.TILLEGGSSTONADER)

        Ytelse.FAR_FAR -> emptyList()

        // no matching innsendingsytelse/tema
        Ytelse.DAG_LKP -> listOf(Innsendingsytelse.DAGPENGER)

        Ytelse.DAG_FDP -> listOf(Innsendingsytelse.DAGPENGER)

        Ytelse.BIL_BIL -> listOf(Innsendingsytelse.BILSTONAD)

        Ytelse.HEL_HEL -> listOf(Innsendingsytelse.HJELPEMIDLER_ORTOPEDISKE)

        Ytelse.FOS_FOS -> listOf(Innsendingsytelse.FORSIKRING)

        Ytelse.PAR_PAR -> emptyList()

        // no matching innsendingsytelse/tema
        Ytelse.UNG_UNG -> listOf(Innsendingsytelse.UNGDOMSPROGRAMMET)

        Ytelse.PEN_GYS -> emptyList()
    }
