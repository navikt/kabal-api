package no.nav.klage.oppgave.service.mapper

import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.domain.kodeverk.Kode
import java.time.LocalDate
import java.time.LocalDateTime

private fun Klager.mapToSkjemaV1(): KlagebehandlingSkjemaV1.PersonEllerOrganisasjon {
    return if (this.erPerson()) {
        KlagebehandlingSkjemaV1.PersonEllerOrganisasjon(
            KlagebehandlingSkjemaV1.Person(fnr = this.partId.value)
        )
    } else {
        KlagebehandlingSkjemaV1.PersonEllerOrganisasjon(
            KlagebehandlingSkjemaV1.Organisasjon(orgnr = this.partId.value)
        )
    }
}

private fun Prosessfullmektig.mapToSkjemaV1(): KlagebehandlingSkjemaV1.PersonEllerOrganisasjon {
    return if (this.erPerson()) {
        KlagebehandlingSkjemaV1.PersonEllerOrganisasjon(
            KlagebehandlingSkjemaV1.Person(fnr = this.partId.value)
        )
    } else {
        KlagebehandlingSkjemaV1.PersonEllerOrganisasjon(
            KlagebehandlingSkjemaV1.Organisasjon(orgnr = this.partId.value)
        )
    }
}

private fun SakenGjelder.mapToSkjemaV1(): KlagebehandlingSkjemaV1.PersonEllerOrganisasjon {
    return if (this.erPerson()) {
        KlagebehandlingSkjemaV1.PersonEllerOrganisasjon(
            KlagebehandlingSkjemaV1.Person(fnr = this.partId.value)
        )
    } else {
        KlagebehandlingSkjemaV1.PersonEllerOrganisasjon(
            KlagebehandlingSkjemaV1.Organisasjon(orgnr = this.partId.value)
        )
    }
}

private fun Tildeling.mapToSkjemaV1(): KlagebehandlingSkjemaV1.TildeltSaksbehandler {
    return KlagebehandlingSkjemaV1.TildeltSaksbehandler(
        tidspunkt = this.tidspunkt,
        saksbehandler = this.saksbehandlerident?.let {
            KlagebehandlingSkjemaV1.Saksbehandler(
                ident = it,
            )
        },
        enhet = this.enhet?.let {
            KlagebehandlingSkjemaV1.Enhet(
                nr = it,
            )
        }
    )
}

private fun MedunderskriverTildeling.mapToSkjemaV1(): KlagebehandlingSkjemaV1.TildeltMedunderskriver {
    return KlagebehandlingSkjemaV1.TildeltMedunderskriver(
        tidspunkt = this.tidspunkt,
        saksbehandler = this.saksbehandlerident?.let { KlagebehandlingSkjemaV1.Saksbehandler(it) }
    )
}

private fun Kode.mapToSkjemaV1(): KlagebehandlingSkjemaV1.Kode {
    return KlagebehandlingSkjemaV1.Kode(
        id = this.id,
        navn = this.navn,
        beskrivelse = this.beskrivelse
    )
}

fun Klagebehandling.mapToSkjemaV1(): KlagebehandlingSkjemaV1 {

    return KlagebehandlingSkjemaV1(
        id = this.id.toString(),
        klager = this.klager.mapToSkjemaV1(),
        klagersProsessfullmektig = this.klager.prosessfullmektig?.mapToSkjemaV1(),
        sakenGjelder = this.sakenGjelder.mapToSkjemaV1(),
        tema = this.ytelse.toTema().mapToSkjemaV1(),
        type = this.type.mapToSkjemaV1(),
        kildeReferanse = this.kildeReferanse,
        sakFagsystem = this.sakFagsystem?.let { it.mapToSkjemaV1() },
        sakFagsakId = this.sakFagsakId,
        innsendtDato = this.innsendt,
        mottattFoersteinstansDato = this.mottattFoersteinstans,
        avsenderSaksbehandlerFoersteinstans = this.avsenderSaksbehandleridentFoersteinstans?.let {
            KlagebehandlingSkjemaV1.Saksbehandler(it)
        },
        avsenderEnhetFoersteinstans = this.avsenderEnhetFoersteinstans.let { KlagebehandlingSkjemaV1.Enhet(it) },
        mottattKlageinstansTidspunkt = this.mottattKlageinstans,
        avsluttetAvSaksbehandlerTidspunkt = this.avsluttetAvSaksbehandler,
        avsluttetTidspunkt = this.avsluttet,
        fristDato = this.frist,
        gjeldendeTildeling = this.tildeling?.mapToSkjemaV1(),
        medunderskriver = this.medunderskriver?.mapToSkjemaV1(),
        medunderskriverFlytStatus = this.medunderskriverFlyt.mapToSkjemaV1(),
        hjemler = this.hjemler.map { it.mapToSkjemaV1() },
        opprettetTidspunkt = this.created,
        sistEndretTidspunkt = this.modified,
        kildesystem = this.kildesystem.mapToSkjemaV1(),
        saksdokumenter = listOf(),
        vedtak = this.vedtak.let { vedtak ->
            KlagebehandlingSkjemaV1.Vedtak(
                utfall = vedtak.utfall?.mapToSkjemaV1(),
                hjemler = vedtak.hjemler.map { it.mapToSkjemaV1() },
            )
        },
        status = KlagebehandlingSkjemaV1.StatusType.valueOf(this.getStatus().name)
    )
}

data class KlagebehandlingSkjemaV1(
    val id: String,
    val klager: PersonEllerOrganisasjon,
    val klagersProsessfullmektig: PersonEllerOrganisasjon?,
    val sakenGjelder: PersonEllerOrganisasjon,
    val tema: Kode,
    val type: Kode,
    val kildeReferanse: String,
    val sakFagsystem: Kode?,
    val sakFagsakId: String?,
    val innsendtDato: LocalDate?,
    val mottattFoersteinstansDato: LocalDate?,
    val avsenderSaksbehandlerFoersteinstans: Saksbehandler?,
    val avsenderEnhetFoersteinstans: Enhet?,
    val mottattKlageinstansTidspunkt: LocalDateTime,
    val avsluttetAvSaksbehandlerTidspunkt: LocalDateTime?,
    val avsluttetTidspunkt: LocalDateTime?,
    val fristDato: LocalDate?,

    val gjeldendeTildeling: TildeltSaksbehandler?,
    val medunderskriver: TildeltMedunderskriver?,
    val medunderskriverFlytStatus: Kode,
    val hjemler: List<Kode>,
    val opprettetTidspunkt: LocalDateTime,
    val sistEndretTidspunkt: LocalDateTime,
    val kildesystem: Kode,

    val saksdokumenter: List<Dokument>,
    val vedtak: Vedtak?,

    val status: StatusType,
) {

    data class Vedtak(
        val utfall: Kode?,
        val hjemler: List<Kode>,
    )

    enum class StatusType {
        IKKE_TILDELT, TILDELT, MEDUNDERSKRIVER_VALGT, SENDT_TIL_MEDUNDERSKRIVER, RETURNERT_TIL_SAKSBEHANDLER, AVSLUTTET_AV_SAKSBEHANDLER, FULLFOERT, UKJENT
    }

    data class Person(
        val fnr: String,
    )

    data class Organisasjon(
        val orgnr: String,
    )

    data class PersonEllerOrganisasjon private constructor(val person: Person?, val organisasjon: Organisasjon?) {
        constructor(person: Person) : this(person, null)
        constructor(organisasjon: Organisasjon) : this(null, organisasjon)
    }

    data class Kode(
        val id: String,
        val navn: String,
        val beskrivelse: String,
    )

    data class Enhet(
        val nr: String,
    )

    data class Saksbehandler(
        val ident: String,
    )

    data class TildeltSaksbehandler(
        val tidspunkt: LocalDateTime,
        val saksbehandler: Saksbehandler?,
        val enhet: Enhet?,
    )

    data class TildeltMedunderskriver(
        val tidspunkt: LocalDateTime,
        val saksbehandler: Saksbehandler?,
    )

    data class Dokument(
        val journalpostId: String,
        val dokumentInfoId: String,
    )
}
