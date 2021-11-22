package no.nav.klage.oppgave.api.controller

import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.clients.saf.graphql.SafGraphQlClient
import no.nav.klage.oppgave.domain.klage.MottakDokumentType
import no.nav.klage.oppgave.domain.kodeverk.Type
import no.nav.klage.oppgave.domain.kodeverk.Ytelse
import no.nav.klage.oppgave.service.MottakService
import no.nav.security.token.support.core.api.Unprotected
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.*

@Profile("dev-gcp")
@RestController
@RequestMapping("mockdata")
class MockDataController(
    private val mottakService: MottakService,
    private val safClient: SafGraphQlClient
) {

    // Alle syntetiske personer under her er registrert under https://dolly.dev.adeo.no/gruppe/2960

    @Unprotected
    @PostMapping("/randomklage")
    fun sendInnRandomKlage() {
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
        ).shuffled().first()

        val fnr = dollyDoc.fnr
        val journalpostId = dollyDoc.journalpost
        val journalpost = safClient.getJournalpostAsSystembruker(journalpostId)

        val dato = LocalDate.of(2020, (1..12).random(), (1..28).random())

        mottakService.createMottakForKlage(
            OversendtKlage(
                ytelse = listOf(
                    Ytelse.SYK_SYK,
                    Ytelse.OMS_OLP,
                    Ytelse.OMS_OMP,
                    Ytelse.OMS_PLS,
                    Ytelse.OMS_PSB
                ).shuffled().first(),
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
                        HjemmelFraFoersteInstans(Lov.FOLKETRYGDLOVEN, 8, 4),
                        HjemmelFraFoersteInstans(Lov.FOLKETRYGDLOVEN, 8, 21),
                        HjemmelFraFoersteInstans(Lov.FOLKETRYGDLOVEN, 8, 22),
                        HjemmelFraFoersteInstans(Lov.FOLKETRYGDLOVEN, 8, 35)
                    ).shuffled().first()
                ),
                avsenderSaksbehandlerIdent = "Z994674",
                avsenderEnhet = "0104", //NAV Moss
                tilknyttedeJournalposter = listOf(
                    OversendtDokumentReferanse(
                        randomMottakDokumentType(),
                        journalpostId
                    )
                ),
                mottattFoersteinstans = dato,
                innsendtTilNav = dato.minusDays(3),
                kilde = KildeFagsystem.AO01
            )
        )
    }

    @Unprotected
    @PostMapping("/kode6")
    fun createKode6Person() {
        val fnr = "15436621822" // ÅPENHJERTIG SAKS
        val journalpostId = "510534811"
        val journalpost = safClient.getJournalpostAsSystembruker(journalpostId)
        val dato = LocalDate.of(2020, 1, 13)

        mottakService.createMottakForKlage(
            OversendtKlage(
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
                        HjemmelFraFoersteInstans(Lov.FOLKETRYGDLOVEN, 8, 4),
                        HjemmelFraFoersteInstans(Lov.FOLKETRYGDLOVEN, 8, 21),
                        HjemmelFraFoersteInstans(Lov.FOLKETRYGDLOVEN, 8, 22),
                        HjemmelFraFoersteInstans(Lov.FOLKETRYGDLOVEN, 8, 35)
                    ).shuffled().first()
                ),
                avsenderSaksbehandlerIdent = "Z994674",
                avsenderEnhet = "0104", //NAV Moss
                tilknyttedeJournalposter = listOf(
                    OversendtDokumentReferanse(
                        randomMottakDokumentType(),
                        journalpostId
                    )
                ),
                mottattFoersteinstans = dato,
                innsendtTilNav = dato.minusDays(3),
                kilde = KildeFagsystem.AO01
            )
        )
    }

    @Unprotected
    @PostMapping("/kode7")
    fun createKode7Person() {
        val fnr = "28107122119" // GOD STAFFELI
        val journalpostId = "510534809"
        val journalpost = safClient.getJournalpostAsSystembruker(journalpostId)
        val dato = LocalDate.of(2020, 1, 13)

        mottakService.createMottakForKlage(
            OversendtKlage(
                ytelse = Ytelse.OMS_PSB,
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
                        HjemmelFraFoersteInstans(Lov.FOLKETRYGDLOVEN, 8, 4),
                        HjemmelFraFoersteInstans(Lov.FOLKETRYGDLOVEN, 8, 21),
                        HjemmelFraFoersteInstans(Lov.FOLKETRYGDLOVEN, 8, 22),
                        HjemmelFraFoersteInstans(Lov.FOLKETRYGDLOVEN, 8, 35)
                    ).shuffled().first()
                ),
                avsenderSaksbehandlerIdent = "Z994674",
                avsenderEnhet = "0104", //NAV Moss
                tilknyttedeJournalposter = listOf(
                    OversendtDokumentReferanse(
                        randomMottakDokumentType(),
                        journalpostId
                    )
                ),
                mottattFoersteinstans = dato,
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

        mottakService.createMottakForKlage(
            OversendtKlage(
                ytelse = Ytelse.OMS_OLP,
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
                        HjemmelFraFoersteInstans(Lov.FOLKETRYGDLOVEN, 8, 4),
                        HjemmelFraFoersteInstans(Lov.FOLKETRYGDLOVEN, 8, 21),
                        HjemmelFraFoersteInstans(Lov.FOLKETRYGDLOVEN, 8, 22),
                        HjemmelFraFoersteInstans(Lov.FOLKETRYGDLOVEN, 8, 35)
                    ).shuffled().first()
                ),
                avsenderSaksbehandlerIdent = "Z994674",
                avsenderEnhet = "0104", //NAV Moss
                tilknyttedeJournalposter = listOf(
                    OversendtDokumentReferanse(
                        randomMottakDokumentType(),
                        journalpostId
                    )
                ),
                mottattFoersteinstans = dato,
                innsendtTilNav = dato.minusDays(3),
                kilde = KildeFagsystem.AO01
            )
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
}
