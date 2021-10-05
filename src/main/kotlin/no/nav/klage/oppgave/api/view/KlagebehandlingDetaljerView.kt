package no.nav.klage.oppgave.api.view

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class KlagebehandlingDetaljerView(
    val id: UUID,
    val klageInnsendtdato: LocalDate?,
    val fraNAVEnhet: String?,
    val fraNAVEnhetNavn: String?,
    val mottattFoersteinstans: LocalDate? = null,
    val sakenGjelder: SakenGjelderView,
    val klager: KlagerView?,
    val sakenGjelderFoedselsnummer: String?,
    val sakenGjelderNavn: NavnView?,
    val sakenGjelderKjoenn: String?,
    val sakenGjelderVirksomhetsnummer: String?,
    val sakenGjelderVirksomhetsnavn: String?,
    val klagerFoedselsnummer: String?,
    val klagerVirksomhetsnummer: String?,
    val klagerVirksomhetsnavn: String?,
    val klagerNavn: NavnView?,
    val klagerKjoenn: String?,
    val tema: String,
    val type: String,
    val mottatt: LocalDate?,
    val mottattKlageinstans: LocalDate?,
    val tildelt: LocalDate? = null,
    val avsluttetAvSaksbehandler: LocalDate?,
    val frist: LocalDate? = null,
    val tildeltSaksbehandlerident: String? = null,
    val medunderskriverident: String? = null,
    val medunderskriverFlyt: String,
    val datoSendtMedunderskriver: LocalDate?,
    val hjemler: List<String>,
    val modified: LocalDateTime,
    val created: LocalDateTime,
    val fraSaksbehandlerident: String? = null,
    val klagebehandlingVersjon: Long,
    val resultat: VedtakView?,
    val kommentarFraFoersteinstans: String?,
    val tilknyttedeDokumenter: Set<TilknyttetDokument>,
    val egenAnsatt: Boolean,
    val fortrolig: Boolean,
    val strengtFortrolig: Boolean
) {
    data class NavnView(
        val fornavn: String?,
        val mellomnavn: String?,
        val etternavn: String?,
    )

    data class KlagerView(
        val person: PersonView?,
        val virksomhet: VirksomhetView?
    )

    data class SakenGjelderView(
        val person: PersonView?,
        val virksomhet: VirksomhetView?
    )

    data class VirksomhetView(
        val virksomhetsnummer: String?,
        val navn: String?,
    )

    data class PersonView(
        val foedselsnummer: String?,
        val navn: NavnView?,
        val kjoenn: String?,
    )
}
