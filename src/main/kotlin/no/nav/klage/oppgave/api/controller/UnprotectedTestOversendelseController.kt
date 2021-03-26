package no.nav.klage.oppgave.api.controller

import no.nav.klage.oppgave.api.view.OversendtKlage
import no.nav.klage.oppgave.domain.kodeverk.Kilde
import no.nav.klage.oppgave.domain.kodeverk.Sakstype
import no.nav.klage.oppgave.domain.kodeverk.Tema
import no.nav.klage.oppgave.service.MottakService
import no.nav.klage.oppgave.util.getLogger
import no.nav.security.token.support.core.api.Unprotected
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.*
import javax.validation.Valid

@Profile("dev-gcp")
@RestController
@Unprotected
@RequestMapping("testoversendelse")
class UnprotectedTestOversendelseController(
    private val mottakService: MottakService
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @PostMapping("/klage")
    fun sendInnKlage(@Valid @RequestBody oversendtKlage: OversendtKlage) {
        mottakService.createMottakForKlage(oversendtKlage)
    }

    @PostMapping("/predefklage")
    fun sendInnKlage() {
        val klager = listOf(
            OversendtKlage(
                uuid = UUID.randomUUID(),
                tema = Tema.SYK,
                eksternReferanse = "SYK_27458422236",
                innsynUrl = "https://vg.no",
                foedselsnummer = "27458422236",
                beskrivelse = "ORDINÆR",
                avsenderSaksbehandlerIdent = "Z994674",
                avsenderEnhet = "0104", //NAV Moss
                hjemler = listOf("8-4", "8-21"),
                mottattFoersteinstans = LocalDate.of(2021, 2, 2),
                innsendtTilNav = LocalDate.of(2021, 2, 1),
                sakstype = Sakstype.KLAGE,
                oversendtEnhet = "4291",
                oversendelsesbrevJournalpostId = null,
                brukersKlageJournalpostId = null,
                frist = LocalDate.of(2021, 8, 2),
                kilde = Kilde.OPPGAVE
            ),
            OversendtKlage(
                uuid = UUID.randomUUID(),
                tema = Tema.SYK,
                eksternReferanse = "SYK_28488425473",
                innsynUrl = "https://vg.no",
                foedselsnummer = "28488425473",
                beskrivelse = "EGEN ANSATT",
                avsenderSaksbehandlerIdent = "Z994674",
                avsenderEnhet = "0104", //NAV Moss
                hjemler = listOf("8-4", "8-21"),
                mottattFoersteinstans = LocalDate.of(2021, 2, 3),
                innsendtTilNav = LocalDate.of(2021, 2, 2),
                sakstype = Sakstype.KLAGE,
                oversendtEnhet = "4291",
                oversendelsesbrevJournalpostId = null,
                brukersKlageJournalpostId = null,
                frist = LocalDate.of(2021, 8, 3),
                kilde = Kilde.OPPGAVE
            ),
            OversendtKlage(
                uuid = UUID.randomUUID(),
                tema = Tema.SYK,
                eksternReferanse = "SYK_02518418680",
                innsynUrl = "https://vg.no",
                foedselsnummer = "02518418680",
                beskrivelse = "KODE 6",
                avsenderSaksbehandlerIdent = "Z994674",
                avsenderEnhet = "0104", //NAV Moss
                hjemler = listOf("8-4", "8-21"),
                mottattFoersteinstans = LocalDate.of(2021, 2, 4),
                innsendtTilNav = LocalDate.of(2021, 2, 3),
                sakstype = Sakstype.KLAGE,
                oversendtEnhet = "4291",
                oversendelsesbrevJournalpostId = null,
                brukersKlageJournalpostId = null,
                frist = LocalDate.of(2021, 8, 4),
                kilde = Kilde.OPPGAVE
            ),
            OversendtKlage(
                uuid = UUID.randomUUID(),
                tema = Tema.SYK,
                eksternReferanse = "SYK_02508425425",
                innsynUrl = "https://vg.no",
                foedselsnummer = "02508425425",
                beskrivelse = "KODE 7",
                avsenderSaksbehandlerIdent = "Z994674",
                avsenderEnhet = "0104", //NAV Moss
                hjemler = listOf("8-4", "8-21"),
                mottattFoersteinstans = LocalDate.of(2021, 2, 5),
                innsendtTilNav = LocalDate.of(2021, 2, 4),
                sakstype = Sakstype.KLAGE,
                oversendtEnhet = "4291",
                oversendelsesbrevJournalpostId = null,
                brukersKlageJournalpostId = null,
                frist = LocalDate.of(2021, 8, 5),
                kilde = Kilde.OPPGAVE
            ),
            OversendtKlage(
                uuid = UUID.randomUUID(),
                tema = Tema.SYK,
                eksternReferanse = "SYK_23528406688",
                innsynUrl = "https://vg.no",
                foedselsnummer = "23528406688",
                beskrivelse = "KODE 19",
                avsenderSaksbehandlerIdent = "Z994674",
                avsenderEnhet = "0104", //NAV Moss
                hjemler = listOf("8-4", "8-21"),
                mottattFoersteinstans = LocalDate.of(2021, 2, 6),
                innsendtTilNav = LocalDate.of(2021, 2, 5),
                sakstype = Sakstype.KLAGE,
                oversendtEnhet = "4291",
                oversendelsesbrevJournalpostId = null,
                brukersKlageJournalpostId = null,
                frist = LocalDate.of(2021, 8, 6),
                kilde = Kilde.OPPGAVE
            )
        )
        klager.forEach {
            mottakService.createMottakForKlage(it)
        }
    }
}
