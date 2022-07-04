package no.nav.klage.oppgave.api.controller

import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.ytelseTilHjemler
import no.nav.klage.kodeverk.hjemmel.ytelseTilRegistreringshjemler
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.clients.saf.graphql.SafGraphQlClient
import no.nav.klage.oppgave.domain.klage.AnkeITrygderettenbehandlingInput
import no.nav.klage.oppgave.domain.klage.MottakDokumentType
import no.nav.klage.oppgave.service.AnkeITrygderettenbehandlingService
import no.nav.klage.oppgave.service.MottakService
import no.nav.security.token.support.core.api.Unprotected
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Year
import java.util.*

@Profile("dev-gcp")
@RestController
@RequestMapping("mockdata")
class MockDataController(
    private val mottakService: MottakService,
    private val safClient: SafGraphQlClient,
    private val ankeITrygderettenbehandlingService: AnkeITrygderettenbehandlingService
) {

    // Alle syntetiske personer under her er registrert under https://dolly.dev.adeo.no/gruppe/2960

    @Unprotected
    @PostMapping("/kode6")
    fun createKode6Person() {
        createKlagebehandlingForASpecificPerson("15436621822") // ÅPENHJERTIG SAKS
    }

    @Unprotected
    @PostMapping("/kode7")
    fun createKode7Person() {
        createKlagebehandlingForASpecificPerson("28107122119") // GOD STAFFELI
    }

    @Unprotected
    @PostMapping("/egenansatt")
    fun createEgenAnsattBehandling() {
        createKlagebehandlingForASpecificPerson("29468230052") // Gjensidig Strømpebukse)
    }

    fun createKlagebehandlingForASpecificPerson(fnr: String) {
        val journalpostId = "510534809"
        val journalpost = safClient.getJournalpostAsSystembruker(journalpostId)
        val dato = LocalDate.of(2022, 1, 13)

        mottakService.createMottakForKlageAnkeV3(
            OversendtKlageAnkeV3(
                ytelse = Ytelse.OMS_OMP,
                type = Type.KLAGE,
                klager = OversendtKlager(
                    id = OversendtPartId(OversendtPartIdType.PERSON, fnr)
                ),
                fagsak = journalpost?.sak?.let {
                    OversendtSak(
                        fagsakId = it.fagsakId ?: "UKJENT",
                        fagsystem = KildeFagsystem.AO01
                    )
                },
                kildeReferanse = UUID.randomUUID().toString(),
                innsynUrl = "https://nav.no",
                hjemler = listOf(
                    listOf(
                        Hjemmel.FTRL_9_10,
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
                kilde = KildeFagsystem.AO01
            )
        )
    }

    @Unprotected
    @PostMapping("/fullmakt")
    fun createPersonWithFullmakt() {
        val fnr = "17117323862" // SNILL VEPS
        val journalpostId = "510534808"
        val journalpost = safClient.getJournalpostAsSystembruker(journalpostId)
        val dato = LocalDate.of(2020, 1, 13)

        mottakService.createMottakForKlageAnkeV3(
            OversendtKlageAnkeV3(
                ytelse = Ytelse.OMS_OMP,
                type = Type.KLAGE,
                klager = OversendtKlager(
                    id = OversendtPartId(OversendtPartIdType.PERSON, fnr),
                    klagersProsessfullmektig = OversendtProsessfullmektig(
                        id = OversendtPartId(OversendtPartIdType.PERSON, "25017820926"),
                        skalKlagerMottaKopi = true
                    )
                ),
                fagsak = journalpost?.sak?.let {
                    OversendtSak(
                        fagsakId = it.fagsakId ?: "UKJENT",
                        fagsystem = KildeFagsystem.AO01
                    )
                },
                kildeReferanse = UUID.randomUUID().toString(),
                innsynUrl = "https://nav.no",
                hjemler = listOf(
                    listOf(
                        Hjemmel.FTRL_9_10,
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
                kilde = KildeFagsystem.AO01
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

    private fun createKlanke(type: Type, input: MockInput?): MockDataResponse {
        val dollyDoc = listOf(
            SyntheticWithDoc("02446701749", "510534792"),
            SyntheticWithDoc("29437117843", "510534815"),
            SyntheticWithDoc("25438301286", "510534816"),
            SyntheticWithDoc("18496900509", "510534817"),
            SyntheticWithDoc("28416904490", "510534818"),
            SyntheticWithDoc("17457337760", "510534819"),
            SyntheticWithDoc("16498818653", "510534820"),
            SyntheticWithDoc("20467938577", "510534821"),
            SyntheticWithDoc("14437830275", "510534823"),
            SyntheticWithDoc("18418507701", "510534797"),
            SyntheticWithDoc("12518603068", "510534824")
        ).random()

        val fnr = dollyDoc.fnr
        val journalpostId = dollyDoc.journalpost
        val journalpost = safClient.getJournalpostAsSystembruker(journalpostId)

        val dato = LocalDate.of(Year.now().value - 1, (1..12).random(), (1..28).random())

        val randomYtelse = if (input == null) Ytelse.SYK_SYK else input.ytelse ?: ytelseTilHjemler.keys.random()
        val klager = input?.klager ?: OversendtKlager(
            id = OversendtPartId(OversendtPartIdType.PERSON, fnr)
        )

        val sakenGjelder = input?.sakenGjelder

        val behandling = when (type) {
            Type.KLAGE, Type.ANKE -> {
                mottakService.createMottakForKlageAnkeV3ForE2ETests(
                    OversendtKlageAnkeV3(
                        ytelse = randomYtelse,
                        type = type,
                        klager = klager,
                        fagsak = journalpost?.sak?.let {
                            OversendtSak(
                                fagsakId = it.fagsakId ?: "UKJENT",
                                fagsystem = KildeFagsystem.AO01
                            )
                        },
                        sakenGjelder = sakenGjelder,
                        kildeReferanse = input?.kildeReferanse ?: UUID.randomUUID().toString(),
                        dvhReferanse = input?.dvhReferanse,
                        innsynUrl = "https://nav.no",
                        hjemler = listOf(ytelseTilHjemler[randomYtelse]!!.random()),
                        forrigeBehandlendeEnhet = input?.forrigeBehandlendeEnhet ?: "4295", //NAV Klageinstans nord
                        tilknyttedeJournalposter = listOf(
                            OversendtDokumentReferanse(
                                randomMottakDokumentType(),
                                journalpostId
                            )
                        ),
                        brukersHenvendelseMottattNavDato = dato,
                        sakMottattKaDato = dato,
                        innsendtTilNav = dato.minusDays(3),
                        kilde = KildeFagsystem.AO01,
                    )
                )
            }

            Type.ANKE_I_TRYGDERETTEN -> {
                val oversendtSak = journalpost!!.sak?.let {
                    OversendtSak(
                        fagsakId = it.fagsakId ?: "UKJENT",
                        fagsystem = KildeFagsystem.FS36
                    )
                }

                ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(
                    input = AnkeITrygderettenbehandlingInput(
                        klager = klager.toKlagepart(),
                        sakenGjelder = sakenGjelder?.toSakenGjelder(),
                        ytelse = randomYtelse,
                        type = type,
                        kildeReferanse = input?.kildeReferanse ?: UUID.randomUUID().toString(),
                        dvhReferanse = input?.dvhReferanse ?: UUID.randomUUID().toString(),
                        sakFagsystem = Fagsystem.FS36,
                        sakFagsakId = oversendtSak!!.fagsakId,
                        sakMottattKlageinstans = dato.atStartOfDay(),
                        frist = dato.plusWeeks(8L),
                        saksdokumenter = mutableSetOf(),
                        innsendingsHjemler = mutableSetOf(ytelseTilHjemler[randomYtelse]!!.random()),
                        kildesystem = Fagsystem.FS36,
                        sendtTilTrygderetten = LocalDateTime.now(),
                        registreringsHjemmelSet = mutableSetOf(ytelseTilRegistreringshjemler[randomYtelse]!!.random())
                    )
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


    private fun randomMottakDokumentType() = listOf(
        MottakDokumentType.OVERSENDELSESBREV,
        MottakDokumentType.ANNET,
        MottakDokumentType.BRUKERS_KLAGE,
        MottakDokumentType.OPPRINNELIG_VEDTAK
    ).shuffled().first()

    data class SyntheticWithDoc(
        val fnr: String,
        val journalpost: String
    )

    data class MockInput(
        val ytelse: Ytelse?,
        val klager: OversendtKlager?,
        val sakenGjelder: OversendtSakenGjelder?,
        val kildeReferanse: String?,
        val dvhReferanse: String?,
        val forrigeBehandlendeEnhet: String?,
    )
}
