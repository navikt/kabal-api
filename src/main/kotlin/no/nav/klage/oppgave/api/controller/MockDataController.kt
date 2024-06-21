package no.nav.klage.oppgave.api.controller

import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.kodeverk.hjemmel.Hjemmel.*
import no.nav.klage.kodeverk.hjemmel.ytelseTilHjemler
import no.nav.klage.kodeverk.hjemmel.ytelseTilRegistreringshjemlerV1
import no.nav.klage.kodeverk.hjemmel.ytelseTilRegistreringshjemlerV2
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.clients.saf.SafFacade
import no.nav.klage.oppgave.domain.kafka.ExternalUtfall
import no.nav.klage.oppgave.domain.klage.AnkeITrygderettenbehandlingInput
import no.nav.klage.oppgave.domain.klage.MottakDokumentType
import no.nav.klage.oppgave.domain.klage.utfallToTrygderetten
import no.nav.klage.oppgave.service.AnkeITrygderettenbehandlingService
import no.nav.klage.oppgave.service.MottakService
import no.nav.security.token.support.core.api.Unprotected
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import kotlin.random.Random

@Profile("dev-gcp")
@RestController
@RequestMapping("mockdata")
class MockDataController(
    private val mottakService: MottakService,
    private val ankeITrygderettenbehandlingService: AnkeITrygderettenbehandlingService,
    @Value("#{T(java.time.LocalDate).parse('\${KAKA_VERSION_2_DATE}')}")
    private val kakaVersion2Date: LocalDate,
    private val safFacade: SafFacade
) {

    //https://dolly.ekstern.dev.nav.no/gruppe/6336
    @Unprotected
    @PostMapping("/kode6")
    fun createKode6Person() {
        createKlagebehandlingForASpecificPerson("26876597755")
    }

    //https://dolly.ekstern.dev.nav.no/gruppe/6335
    @Unprotected
    @PostMapping("/kode7")
    fun createKode7Person() {
        createKlagebehandlingForASpecificPerson("17855999285")
    }

    //https://dolly.ekstern.dev.nav.no/gruppe/6334
    @Unprotected
    @PostMapping("/egenansatt")
    fun createEgenAnsattBehandling() {
        createKlagebehandlingForASpecificPerson("12518812945")
    }

    fun createKlagebehandlingForASpecificPerson(fnr: String) {
        val journalpostId = "510534809"
        val journalpost = safFacade.getJournalposter(
            journalpostIdSet = setOf(journalpostId),
            fnr = null,
            saksbehandlerContext = false,
        ).first()

        val dato = LocalDate.of(2022, 1, 13)

        mottakService.createMottakForKlageAnkeV3(
            OversendtKlageAnkeV3(
                ytelse = Ytelse.OMS_OMP,
                type = Type.KLAGE,
                klager = OversendtKlager(
                    id = OversendtPartId(OversendtPartIdType.PERSON, fnr)
                ),
                fagsak = OversendtSak(
                    fagsakId = journalpost.sak?.fagsakId ?: "UKJENT",
                    fagsystem = journalpost.sak?.fagsaksystem?.let {
                        try {
                            Fagsystem.valueOf(it)
                        } catch (e: Exception) {
                            Fagsystem.AO01
                        }
                    }
                        ?: Fagsystem.AO01
                ),
                kildeReferanse = UUID.randomUUID().toString(),
                innsynUrl = "https://nav.no",
                hjemler = listOf(
                    listOf(
                        FTRL_9_10,
                    ).shuffled().first()
                ),
                forrigeBehandlendeEnhet = "0104", //NAV Moss
                tilknyttedeJournalposter = listOf(
                    OversendtDokumentReferanse(
                        randomMottakDokumentType(),
                        journalpostId
                    )
                ),
                brukersHenvendelseMottattNavDato = dato,
                innsendtTilNav = dato.minusDays(3),
                kilde = Fagsystem.AO01,
                kommentar = """
                    Lorem ipsum dolor sit amet, consectetur adipiscing elit, 
                    sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. 
                    
                    Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. 
                    
                    Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident.
                """.trimIndent()
            )
        )
    }

    @Unprotected
    @PostMapping("/fullmakt")
    fun createPersonWithFullmakt() {
        val fnr = "28497037273"
        val journalpostId = "510534808"
        val journalpost = safFacade.getJournalposter(
            journalpostIdSet = setOf(journalpostId),
            fnr = null,
            saksbehandlerContext = false,
        ).first()

        val dato = LocalDate.of(2020, 1, 13)

        mottakService.createMottakForKlageAnkeV3(
            OversendtKlageAnkeV3(
                ytelse = Ytelse.OMS_OMP,
                type = Type.KLAGE,
                klager = OversendtKlager(
                    id = OversendtPartId(OversendtPartIdType.PERSON, fnr),
                    klagersProsessfullmektig = OversendtProsessfullmektig(
                        id = OversendtPartId(OversendtPartIdType.PERSON, "07467517958"),
                        skalKlagerMottaKopi = true
                    )
                ),
                fagsak = OversendtSak(
                    fagsakId = journalpost.sak?.fagsakId ?: "UKJENT",
                    fagsystem = journalpost.sak?.fagsaksystem?.let {
                        try {
                            Fagsystem.valueOf(it)
                        } catch (e: Exception) {
                            Fagsystem.AO01
                        }
                    }
                        ?: Fagsystem.AO01
                ),
                kildeReferanse = UUID.randomUUID().toString(),
                innsynUrl = "https://nav.no",
                hjemler = listOf(
                    listOf(
                        FTRL_9_10,
                    ).shuffled().first()
                ),
                forrigeBehandlendeEnhet = "0104", //NAV Moss
                tilknyttedeJournalposter = listOf(
                    OversendtDokumentReferanse(
                        randomMottakDokumentType(),
                        journalpostId
                    )
                ),
                brukersHenvendelseMottattNavDato = dato,
                innsendtTilNav = dato.minusDays(3),
                kilde = Fagsystem.AO01
            )
        )
    }

    @Unprotected
    @PostMapping("/randomklage")
    fun sendInnRandomKlage(
        @RequestBody(required = false) input: MockInput? = null
    ): MockDataResponse {
        return createKlanke(Type.KLAGE, input)
    }

    @Unprotected
    @PostMapping("/randomanke")
    fun sendInnRandomAnke(
        @RequestBody(required = false) input: MockInput? = null
    ): MockDataResponse {
        return createKlanke(Type.ANKE, input)
    }

    @Unprotected
    @PostMapping("/randomankeitrygderetten")
    fun sendInnRandomAnkeITrygderetten(
        @RequestBody(required = false) input: MockInput? = null
    ): MockDataResponse {
        return createKlanke(Type.ANKE_I_TRYGDERETTEN, input)
    }

    data class MockDataResponse(
        val id: UUID,
        val typeId: String,
        val ytelseId: String,
        val hjemmelId: String,
    )

    //https://dolly.ekstern.dev.nav.no/gruppe/6332
    private fun getFnrAndJournalpostId(ytelse: Ytelse): Fnr {
        return when (ytelse) {
            Ytelse.ENF_ENF -> Fnr(
                fnr = "17887799784"
            )

            Ytelse.BAR_BAR -> Fnr(
                fnr = "50884800363"
            )

            Ytelse.KON_KON -> Fnr(
                fnr = "06049939084"
            )

            Ytelse.OMS_OLP, Ytelse.OMS_OMP, Ytelse.OMS_PLS, Ytelse.OMS_PSB -> Fnr(
                fnr = "25056321171"
            )

            Ytelse.SYK_SYK -> Fnr(
                fnr = "25046846764"
            )

            Ytelse.SUP_UFF -> Fnr(
                fnr = "01046813711"
            )

            Ytelse.FOR_ENG, Ytelse.FOR_FOR, Ytelse.FOR_SVA -> Fnr(
                fnr = "14828897927"
            )

            else -> Fnr(
                fnr = "17887799784"
            )
        }
    }

    data class Fnr(
        val fnr: String,
    )

    private fun createKlanke(type: Type, mockInput: MockInput?): MockDataResponse {
        val ytelse = if (mockInput == null) Ytelse.SYK_SYK else mockInput.ytelse ?: ytelseTilHjemler.keys.random()

        val fnrAndJournalpostId = getFnrAndJournalpostId(ytelse)

        val fnr = fnrAndJournalpostId.fnr
        val lastMonth = LocalDate.now().minusMonths(1).toEpochDay()
        val now = LocalDate.now().toEpochDay()
        val dato = LocalDate.ofEpochDay(ThreadLocalRandom.current().nextLong(lastMonth, now))

        val klager = mockInput?.klager ?: OversendtKlager(
            id = OversendtPartId(OversendtPartIdType.PERSON, fnr)
        )

        val sakenGjelder = mockInput?.sakenGjelder

        val oversendtSak = OversendtSak(
            fagsakId = Random.nextInt(from = 1, until = 9999).toString(),
            fagsystem = Fagsystem.AO01

        )

        val behandling = when (type) {
            Type.KLAGE, Type.ANKE -> {
                mottakService.createMottakForKlageAnkeV3ForE2ETests(
                    OversendtKlageAnkeV3(
                        ytelse = ytelse,
                        type = type,
                        klager = klager,
                        fagsak = oversendtSak,
                        sakenGjelder = sakenGjelder,
                        kildeReferanse = mockInput?.kildeReferanse ?: UUID.randomUUID().toString(),
                        dvhReferanse = mockInput?.dvhReferanse,
                        innsynUrl = "https://nav.no",
                        hjemler = listOf(ytelseTilHjemlerForMock[ytelse]!!.random()),
                        forrigeBehandlendeEnhet = mockInput?.forrigeBehandlendeEnhet ?: "4295", //NAV Klageinstans nord
                        brukersHenvendelseMottattNavDato = dato,
                        sakMottattKaDato = dato,
                        innsendtTilNav = dato.minusDays(3),
                        kilde = Fagsystem.AO01,
                        kommentar = mockInput?.kommentar,
                    )
                )
            }

            Type.ANKE_I_TRYGDERETTEN -> {
                val registreringsHjemmelSet = when (getKakaVersion()) {
                    1 -> {
                        mutableSetOf(ytelseTilRegistreringshjemlerV1[ytelse]!!.random())
                    }

                    2 -> {
                        mutableSetOf(ytelseTilRegistreringshjemlerV2[ytelse]!!.random())
                    }

                    else ->
                        throw error("wrong version")
                }

                val input = AnkeITrygderettenbehandlingInput(
                    klager = klager.toKlagepart(),
                    sakenGjelder = sakenGjelder?.toSakenGjelder(),
                    ytelse = ytelse,
                    type = type,
                    kildeReferanse = mockInput?.kildeReferanse ?: UUID.randomUUID().toString(),
                    dvhReferanse = mockInput?.dvhReferanse ?: UUID.randomUUID().toString(),
                    fagsystem = Fagsystem.fromNavn(oversendtSak.fagsystem.name),
                    fagsakId = oversendtSak.fagsakId,
                    sakMottattKlageinstans = dato.atStartOfDay(),
                    saksdokumenter = mutableSetOf(),
                    innsendingsHjemler = mutableSetOf(ytelseTilHjemler[ytelse]!!.random()),
                    sendtTilTrygderetten = LocalDateTime.now(),
                    registreringsHjemmelSet = registreringsHjemmelSet,
                    ankebehandlingUtfall = ExternalUtfall.valueOf(utfallToTrygderetten.random().name),
                    previousSaksbehandlerident = null,
                    oppgaveId = null,
                )

                ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(
                    input = input
                )
            }
        }

        return MockDataResponse(
            id = behandling.id,
            typeId = behandling.type.id,
            ytelseId = behandling.ytelse.id,
            hjemmelId = behandling.hjemler.first().id
        )
    }

    private fun getKakaVersion(): Int {
        val kvalitetsvurderingVersion = if (LocalDate.now() >= kakaVersion2Date) {
            2
        } else {
            1
        }
        return kvalitetsvurderingVersion
    }

    private fun randomMottakDokumentType() = listOf(
        MottakDokumentType.OVERSENDELSESBREV,
        MottakDokumentType.ANNET,
        MottakDokumentType.BRUKERS_KLAGE,
        MottakDokumentType.OPPRINNELIG_VEDTAK
    ).shuffled().first()

    data class MockInput(
        val ytelse: Ytelse?,
        val klager: OversendtKlager?,
        val sakenGjelder: OversendtSakenGjelder?,
        val kildeReferanse: String?,
        val dvhReferanse: String?,
        val forrigeBehandlendeEnhet: String?,
        val kommentar: String?,
    )

    val hjemlerHJE_HJE = listOf(
        FTRL_10_3,
        FTRL_10_7A,
        FTRL_10_7B,
        FTRL_10_7C,
        FTRL_10_7D,
        FTRL_10_7E,
        FTRL_10_7F,
        FTRL_10_7G,
        FTRL_10_7H,
        FTRL_10_7I,
        FTRL_10_7_3A,
        FTRL_21_12,
        FTRL_22_13,
    )

    val hjemlerYRK_YRK = listOf(
        FTRL_13_3_1,
        FTRL_13_3_2,
        FTRL_13_3_FS,
        FTRL_13_4,
        FTRL_13_6,
        FTRL_13_8,
        FTRL_13_10,
        FTRL_13_17,
        FTRL_21_12,

        TRRL_9,
    )

    val ytelseTilHjemlerForMock = mapOf(
        Ytelse.YRK_YRK to hjemlerYRK_YRK,
        Ytelse.YRK_YSY to hjemlerYRK_YRK,
        Ytelse.YRK_MEN to hjemlerYRK_YRK,

        Ytelse.ENF_ENF to listOf(
            FTRL_15_2,
            FTRL_15_3,
            FTRL_15_4,
            FTRL_15_5,
            FTRL_15_6,
            FTRL_15_8,
            FTRL_15_9,
            FTRL_15_10,
            FTRL_15_11,
            FTRL_15_12,
            FTRL_15_13,
            FTRL_22_12,
            FTRL_22_13,
            FTRL_22_15,
        ),
        Ytelse.BAR_BAR to listOf(
            BTRL_2,
            BTRL_4,
            BTRL_5,
            BTRL_9,
            BTRL_10,
            BTRL_11,
            BTRL_12,
            BTRL_13,
            BTRL_17,
            BTRL_18,
            EOES_AVTALEN,
            NORDISK_KONVENSJON,
            ANDRE_TRYGDEAVTALER,
        ),

        Ytelse.KON_KON to listOf(
            KONTSL_2,
            KONTSL_3,
            KONTSL_6,
            KONTSL_7,
            KONTSL_8,
            KONTSL_9,
            KONTSL_10,
            KONTSL_11,
            KONTSL_12,
            KONTSL_13,
            KONTSL_16,
            EOES_AVTALEN,
            NORDISK_KONVENSJON,
            ANDRE_TRYGDEAVTALER,
        ),

        Ytelse.OMS_OLP to listOf(
            FTRL_9_2,
            FTRL_9_3,
            FTRL_9_5,
            FTRL_9_6,
            FTRL_9_8,
            FTRL_9_9,
            FTRL_9_10,
            FTRL_9_11,
            FTRL_9_13,
            FTRL_9_14,
            FTRL_9_15,
            FTRL_9_16,
            FTRL_22_13,
            FTRL_22_15,
        ),
        Ytelse.OMS_OMP to listOf(
            FTRL_9_2,
            FTRL_9_3,
            FTRL_9_5,
            FTRL_9_6,
            FTRL_9_8,
            FTRL_9_9,
            FTRL_9_10,
            FTRL_9_11,
            FTRL_9_13,
            FTRL_9_14,
            FTRL_9_15,
            FTRL_9_16,
            FTRL_22_13,
            FTRL_22_15,
        ),
        Ytelse.OMS_PLS to listOf(
            FTRL_9_2,
            FTRL_9_3,
            FTRL_9_5,
            FTRL_9_6,
            FTRL_9_8,
            FTRL_9_9,
            FTRL_9_10,
            FTRL_9_11,
            FTRL_9_13,
            FTRL_9_14,
            FTRL_9_15,
            FTRL_9_16,
            FTRL_22_13,
            FTRL_22_15,
        ),
        Ytelse.OMS_PSB to listOf(
            FTRL_9_2,
            FTRL_9_3,
            FTRL_9_5,
            FTRL_9_6,
            FTRL_9_8,
            FTRL_9_9,
            FTRL_9_10,
            FTRL_9_11,
            FTRL_9_13,
            FTRL_9_14,
            FTRL_9_15,
            FTRL_9_16,
            FTRL_22_13,
            FTRL_22_15,
        ),

        Ytelse.SYK_SYK to listOf(
            FTRL_8_1,
            FTRL_8_2,
            FTRL_8_3,
            FTRL_8_4,
            FTRL_8_5,
            FTRL_8_6,
            FTRL_8_7,
            FTRL_8_8,
            FTRL_8_9,
            FTRL_8_10,
            FTRL_8_11,
            FTRL_8_12,
            FTRL_8_13,
            FTRL_8_14,
            FTRL_8_15,
            FTRL_8_16,
            FTRL_8_17,
            FTRL_8_18,
            FTRL_8_19,
            FTRL_8_20,
            FTRL_8_21,
            FTRL_8_22,
            FTRL_8_23,
            FTRL_8_24,
            FTRL_8_25,
            FTRL_8_26,
            FTRL_8_27,
            FTRL_8_28,
            FTRL_8_29,
            FTRL_8_30,
            FTRL_8_31,
            FTRL_8_32,
            FTRL_8_33,
            FTRL_8_34,
            FTRL_8_35,
            FTRL_8_36,
            FTRL_8_37,
            FTRL_8_38,
            FTRL_8_39,
            FTRL_8_40,
            FTRL_8_41,
            FTRL_8_42,
            FTRL_8_43,
            FTRL_8_44,
            FTRL_8_45,
            FTRL_8_46,
            FTRL_8_47,
            FTRL_8_48,
            FTRL_8_49,
            FTRL_8_50,
            FTRL_8_51,
            FTRL_8_52,
            FTRL_8_53,
            FTRL_8_54,
            FTRL_8_55,

            FTRL_21_3,
            FTRL_21_7,
            FTRL_21_12,

            FTRL_22_3,
            FTRL_22_13,
            FTRL_22_15,
            FTRL_22_17,
            FTRL_22_17A,
        ),
        Ytelse.SUP_UFF to listOf(
            SUP_ST_L_3,
            SUP_ST_L_4,
            SUP_ST_L_5,
            SUP_ST_L_6,
            SUP_ST_L_7,
            SUP_ST_L_8,
            SUP_ST_L_9,
            SUP_ST_L_10,
            SUP_ST_L_11,
            SUP_ST_L_12,
            SUP_ST_L_13,
            SUP_ST_L_17,
            SUP_ST_L_18,
            SUP_ST_L_21,
        ),
        Ytelse.FOR_ENG to listOf(
            FTRL_14_2,
            FTRL_14_17,
            FTRL_21_3,
            FTRL_22_13,
            FTRL_22_15,
        ),
        Ytelse.FOR_FOR to listOf(
            FTRL_14_2,
            FTRL_14_5,
            FTRL_14_6,
            FTRL_14_7,
            FTRL_14_9,
            FTRL_14_10,
            FTRL_14_11,
            FTRL_14_12,
            FTRL_14_13,
            FTRL_14_14,
            FTRL_14_15,
            FTRL_14_16,
            FTRL_8_2,
            FTRL_21_3,
            FTRL_22_13,
            FTRL_22_15,
            EOES_883_2004_5,
            EOES_883_2004_6,
        ),
        Ytelse.FOR_SVA to listOf(
            FTRL_14_2,
            FTRL_14_4,
            FTRL_14_6,
            FTRL_14_7,
            FTRL_8_2,
            FTRL_21_3,
            FTRL_22_13,
            FTRL_22_15,
            EOES_883_2004_6,
        ),
        Ytelse.HJE_HJE to hjemlerHJE_HJE,
        Ytelse.BIL_BIL to hjemlerHJE_HJE,
        Ytelse.HEL_HEL to hjemlerHJE_HJE,
    )
}
