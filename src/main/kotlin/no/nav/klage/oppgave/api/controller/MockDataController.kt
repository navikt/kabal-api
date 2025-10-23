package no.nav.klage.oppgave.api.controller

import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.hjemmel.Hjemmel.FTRL_9_10
import no.nav.klage.kodeverk.hjemmel.ytelseToHjemler
import no.nav.klage.kodeverk.hjemmel.ytelseToRegistreringshjemlerV1
import no.nav.klage.kodeverk.hjemmel.ytelseToRegistreringshjemlerV2
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.clients.saf.SafFacade
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingInput
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.embedded.Klager
import no.nav.klage.oppgave.domain.behandling.embedded.SakenGjelder
import no.nav.klage.oppgave.domain.behandling.subentities.MottakDokumentType
import no.nav.klage.oppgave.domain.behandling.utfallToTrygderetten
import no.nav.klage.oppgave.domain.kafka.ExternalUtfall
import no.nav.klage.oppgave.service.AnkeITrygderettenbehandlingService
import no.nav.klage.oppgave.service.ExternalMottakFacade
import no.nav.klage.oppgave.util.KakaVersionUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.security.token.support.core.api.Unprotected
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ThreadLocalRandom

@Profile("dev-gcp")
@RestController
@RequestMapping("mockdata")
class MockDataController(
    private val mottakFacade: ExternalMottakFacade,
    private val ankeITrygderettenbehandlingService: AnkeITrygderettenbehandlingService,
    private val safFacade: SafFacade,
    private val kakaVersionUtil: KakaVersionUtil,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @Unprotected
    @PostMapping("/doed")
    fun createDoedPerson() {
        createKlagebehandlingForASpecificPerson("14506507686")
    }

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
        val dato = LocalDate.of(2022, 1, 13)

        mottakFacade.createMottakForKlageAnkeV3(
            OversendtKlageAnkeV3(
                ytelse = Ytelse.OMS_OMP,
                type = Type.KLAGE,
                klager = OversendtKlagerLegacy(
                    id = OversendtPartId(OversendtPartIdType.PERSON, fnr)
                ),
                fagsak = OversendtSak(
                    fagsakId = UUID.randomUUID().toString(),
                    fagsystem = Fagsystem.AO01
                ),
                kildeReferanse = UUID.randomUUID().toString(),
                innsynUrl = "https://nav.no",
                hjemler = listOf(
                    listOf(
                        FTRL_9_10,
                    ).shuffled().first()
                ),
                forrigeBehandlendeEnhet = "0104", //NAV Moss
                tilknyttedeJournalposter = listOf(),
                brukersHenvendelseMottattNavDato = dato,
                innsendtTilNav = dato.minusDays(3),
                kilde = Fagsystem.AO01,
                hindreAutomatiskSvarbrev = null,
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

        mottakFacade.createMottakForKlageAnkeV3(
            OversendtKlageAnkeV3(
                ytelse = Ytelse.OMS_OMP,
                type = Type.KLAGE,
                klager = OversendtKlagerLegacy(
                    id = OversendtPartId(OversendtPartIdType.PERSON, fnr),
                    klagersProsessfullmektig = OversendtProsessfullmektigLegacy(
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
                        MottakDokumentType.BRUKERS_KLAGE,
                        journalpostId
                    )
                ),
                brukersHenvendelseMottattNavDato = dato,
                innsendtTilNav = dato.minusDays(3),
                kilde = Fagsystem.AO01,
                hindreAutomatiskSvarbrev = null,
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
                fnr = "26457524896"
            )

            Ytelse.BAR_BAR -> Fnr(
                fnr = "06457216678"
            )

            Ytelse.KON_KON -> Fnr(
                fnr = "02507412559"
            )

            Ytelse.OMS_OLP, Ytelse.OMS_OMP, Ytelse.OMS_PLS, Ytelse.OMS_PSB -> Fnr(
                fnr = "20498222634"
            )

            Ytelse.SYK_SYK -> Fnr(
                fnr = "08509328251"
            )

            Ytelse.SUP_UFF -> Fnr(
                fnr = "02516714908"
            )

            Ytelse.FOR_ENG, Ytelse.FOR_FOR, Ytelse.FOR_SVA -> Fnr(
                fnr = "05489333998"
            )

            else -> Fnr(
                fnr = "01427637081"
            )
        }
    }

    data class Fnr(
        val fnr: String,
    )

    private fun createKlanke(type: Type, mockInput: MockInput?): MockDataResponse {
        val start = System.currentTimeMillis()
        logger.debug("Creating klage/anke of type {} for testing", type)
        val ytelse = if (mockInput == null) {
            logger.debug("Null input/body, using SYK as ytelse.")
            Ytelse.SYK_SYK
        } else {
            logger.debug("Mock input not null, but ytelse was, using random ytelse.")
            mockInput.ytelse ?: ytelseToHjemler.keys.random()
        }

        val fnrAndJournalpostId = getFnrAndJournalpostId(ytelse)

        val fnr = fnrAndJournalpostId.fnr
        val lastMonth = LocalDate.now().minusMonths(1).toEpochDay()
        val now = LocalDate.now().toEpochDay()
        val dato = (mockInput?.sakMottattKaTidspunkt ?: LocalDate.ofEpochDay(ThreadLocalRandom.current().nextLong(lastMonth, now))).atStartOfDay()

        val sakenGjelder = mockInput?.sakenGjelder ?: OversendtPart(
            id = OversendtPartId(OversendtPartIdType.PERSON, fnr)
        )

        val klager = mockInput?.klager

        val oversendtSak = mockInput?.fagsak ?: OversendtSak(
            fagsakId = "1234",
            fagsystem = Fagsystem.AO01
        )

        logger.debug("Will create mottak/behandling for klage/anke of type {} for ytelse {}", type, ytelse)
        val behandling = when (type) {
            Type.KLAGE, Type.ANKE, Type.BEHANDLING_ETTER_TRYGDERETTEN_OPPHEVET, Type.OMGJOERINGSKRAV -> {
                mottakFacade.createMottakForKlageAnkeV4(
                    OversendtKlageAnkeV4(
                        ytelse = ytelse,
                        type = OversendtType.valueOf(type.name),
                        sakenGjelder = sakenGjelder,
                        klager = klager,
                        prosessfullmektig = mockInput?.prosessfullmektig,
                        fagsak = oversendtSak,
                        kildeReferanse = mockInput?.kildeReferanse ?: UUID.randomUUID().toString(),
                        dvhReferanse = mockInput?.dvhReferanse,
                        hjemler = listOf(ytelseToHjemler[ytelse]!!.random()),
                        forrigeBehandlendeEnhet = mockInput?.forrigeBehandlendeEnhet ?: "4295", //NAV Klageinstans nord
                        sakMottattKaTidspunkt = dato,
                        kommentar = mockInput?.kommentar,
                        hindreAutomatiskSvarbrev = mockInput?.hindreAutomatiskSvarbrev,
                        saksbehandlerIdentForTildeling = mockInput?.saksbehandlerIdent,
                        tilknyttedeJournalposter = emptyList(),
                        brukersKlageMottattVedtaksinstans = dato.minusDays(2).toLocalDate(),
                        frist = null,
                    )
                )
            }

            Type.ANKE_I_TRYGDERETTEN -> {
                val registreringsHjemmelSet = when (kakaVersionUtil.getKakaVersion()) {
                    1 -> {
                        mutableSetOf(ytelseToRegistreringshjemlerV1[ytelse]!!.random())
                    }

                    2, 3 -> {
                        mutableSetOf(ytelseToRegistreringshjemlerV2[ytelse]!!.random())
                    }

                    else ->
                        error("wrong version")
                }

                val sakenGjelderPart = SakenGjelder(
                    id = UUID.randomUUID(),
                    partId = sakenGjelder.id.toPartId(),
                )

                val klagePart = if (sakenGjelder.id.verdi == klager?.id?.verdi || klager == null) {
                    Klager(
                        id = sakenGjelderPart.id,
                        partId = sakenGjelderPart.partId,
                    )
                } else {
                    Klager(
                        id = UUID.randomUUID(),
                        partId = klager.id.toPartId(),
                    )
                }

                val input = AnkeITrygderettenbehandlingInput(
                    klager = klagePart,
                    sakenGjelder = sakenGjelderPart,
                    prosessfullmektig = null,
                    ytelse = ytelse,
                    type = type,
                    kildeReferanse = mockInput?.kildeReferanse ?: UUID.randomUUID().toString(),
                    dvhReferanse = mockInput?.dvhReferanse ?: UUID.randomUUID().toString(),
                    fagsystem = Fagsystem.fromNavn(oversendtSak.fagsystem.name),
                    fagsakId = oversendtSak.fagsakId,
                    sakMottattKlageinstans = dato,
                    saksdokumenter = mutableSetOf(),
                    innsendingsHjemler = mutableSetOf(ytelseToHjemler[ytelse]!!.random()),
                    sendtTilTrygderetten = LocalDateTime.now(),
                    registreringsHjemmelSet = registreringsHjemmelSet,
                    ankebehandlingUtfall = ExternalUtfall.valueOf(utfallToTrygderetten.random().name),
                    previousSaksbehandlerident = null,
                    gosysOppgaveId = null,
                    tilbakekreving = false,
                    gosysOppgaveRequired = false,
                    initiatingSystem = Behandling.InitiatingSystem.FAGSYSTEM,
                    previousBehandlingId = null,
                )

                ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(
                    input = input
                )
            }
            Type.BEGJAERING_OM_GJENOPPTAK -> TODO()
            Type.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN -> TODO()
        }
        logger.debug("Behandling with id {} was returned", behandling.id)

        logger.debug("{} created for testing. It took {} millis", type, System.currentTimeMillis() - start)

        return MockDataResponse(
            id = behandling.id,
            typeId = behandling.type.id,
            ytelseId = behandling.ytelse.id,
            hjemmelId = behandling.hjemler.first().id
        )
    }

    data class MockInput(
        val ytelse: Ytelse?,
        val sakenGjelder: OversendtPart,
        val klager: OversendtPart?,
        val prosessfullmektig: OversendtProsessfullmektig?,
        val kildeReferanse: String?,
        val dvhReferanse: String?,
        val forrigeBehandlendeEnhet: String?,
        val kommentar: String?,
        val sakMottattKaTidspunkt: LocalDate?,
        val hindreAutomatiskSvarbrev: Boolean?,
        val saksbehandlerIdent: String?,
        val fagsak: OversendtSak?,
    )
}