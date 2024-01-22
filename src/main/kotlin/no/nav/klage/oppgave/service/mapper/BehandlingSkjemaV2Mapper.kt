package no.nav.klage.oppgave.service.mapper

import no.nav.klage.kodeverk.Kode
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.oppgave.domain.klage.*
import java.time.LocalDate
import java.time.LocalDateTime

private fun Klager.mapToSkjemaV2(): BehandlingSkjemaV2.PersonEllerOrganisasjon {
    return if (this.isPerson()) {
        BehandlingSkjemaV2.PersonEllerOrganisasjon(
            BehandlingSkjemaV2.Person(fnr = this.partId.value)
        )
    } else {
        BehandlingSkjemaV2.PersonEllerOrganisasjon(
            BehandlingSkjemaV2.Organisasjon(orgnr = this.partId.value)
        )
    }
}

private fun Prosessfullmektig.mapToSkjemaV2(): BehandlingSkjemaV2.PersonEllerOrganisasjon {
    return if (this.isPerson()) {
        BehandlingSkjemaV2.PersonEllerOrganisasjon(
            BehandlingSkjemaV2.Person(fnr = this.partId.value)
        )
    } else {
        BehandlingSkjemaV2.PersonEllerOrganisasjon(
            BehandlingSkjemaV2.Organisasjon(orgnr = this.partId.value)
        )
    }
}

private fun SakenGjelder.mapToSkjemaV2(): BehandlingSkjemaV2.PersonEllerOrganisasjon {
    return if (this.erPerson()) {
        BehandlingSkjemaV2.PersonEllerOrganisasjon(
            BehandlingSkjemaV2.Person(fnr = this.partId.value)
        )
    } else {
        BehandlingSkjemaV2.PersonEllerOrganisasjon(
            BehandlingSkjemaV2.Organisasjon(orgnr = this.partId.value)
        )
    }
}

private fun Tildeling.mapToSkjemaV2(): BehandlingSkjemaV2.TildeltSaksbehandler {
    return BehandlingSkjemaV2.TildeltSaksbehandler(
        tidspunkt = this.tidspunkt,
        saksbehandler = this.saksbehandlerident?.let {
            BehandlingSkjemaV2.Saksbehandler(
                ident = it,
            )
        },
        enhet = this.enhet?.let {
            BehandlingSkjemaV2.Enhet(
                nr = it,
            )
        }
    )
}

private fun MedunderskriverTildeling.mapToSkjemaV2(): BehandlingSkjemaV2.TildeltMedunderskriver {
    return BehandlingSkjemaV2.TildeltMedunderskriver(
        tidspunkt = this.tidspunkt,
        saksbehandler = this.saksbehandlerident?.let { BehandlingSkjemaV2.Saksbehandler(it) }
    )
}

private fun Kode.mapToSkjemaV2(): BehandlingSkjemaV2.Kode {
    return BehandlingSkjemaV2.Kode(
        id = this.id,
        navn = this.navn,
        beskrivelse = this.beskrivelse
    )
}

private fun Hjemmel.mapToSkjemaV2(): BehandlingSkjemaV2.Kode {
    return BehandlingSkjemaV2.Kode(
        id = id,
        navn = lovKilde.beskrivelse + " - " + spesifikasjon,
        beskrivelse = lovKilde.navn + " - " + spesifikasjon,
    )
}

private fun Registreringshjemmel.mapToSkjemaV2(): BehandlingSkjemaV2.Kode {
    return BehandlingSkjemaV2.Kode(
        id = id,
        navn = lovKilde.beskrivelse + " - " + spesifikasjon,
        beskrivelse = lovKilde.navn + " - " + spesifikasjon,
    )
}

fun Klagebehandling.mapToSkjemaV2(): BehandlingSkjemaV2 {

    return BehandlingSkjemaV2(
        id = id.toString(),
        klager = klager.mapToSkjemaV2(),
        klagersProsessfullmektig = klager.prosessfullmektig?.mapToSkjemaV2(),
        sakenGjelder = sakenGjelder.mapToSkjemaV2(),
        tema = ytelse.toTema().mapToSkjemaV2(),
        ytelse = ytelse.mapToSkjemaV2(),
        type = type.mapToSkjemaV2(),
        kildeReferanse = kildeReferanse,
        sakFagsystem = fagsystem.mapToSkjemaV2(),
        sakFagsakId = fagsakId,
        innsendtDato = innsendt,
        forrigeSaksbehandler = previousSaksbehandlerident?.let {
            BehandlingSkjemaV2.Saksbehandler(it)
        },
        forrigeBehandlendeEnhet = avsenderEnhetFoersteinstans.let { BehandlingSkjemaV2.Enhet(it) },
        sakMottattKaDato = mottattKlageinstans,
        avsluttetAvSaksbehandlerTidspunkt = avsluttetAvSaksbehandler,
        avsluttetTidspunkt = avsluttet,
        fristDato = frist,
        gjeldendeTildeling = tildeling?.mapToSkjemaV2(),
        medunderskriver = medunderskriver?.mapToSkjemaV2(),
        medunderskriverFlowStateId = medunderskriverFlowState.id,
        hjemler = hjemler.map { it.mapToSkjemaV2() },
        opprettetTidspunkt = created,
        sistEndretTidspunkt = modified,
        kildesystem = fagsystem.mapToSkjemaV2(),
        saksdokumenter = saksdokumenter.mapToSkjemaV2(),
        vedtak = BehandlingSkjemaV2.Vedtak(
            utfall = utfall?.mapToSkjemaV2(),
            hjemler = registreringshjemler.map { it.mapToSkjemaV2() },
        ),
        sattPaaVent = sattPaaVent?.from,
        sattPaaVentExpires = sattPaaVent?.to,
        sattPaaVentReason = sattPaaVent?.reason,
        status = BehandlingSkjemaV2.StatusType.valueOf(getStatus().name),
        feilregistrert = feilregistrering?.registered,
        rolIdent = rolIdent,
        rolFlowStateId = rolFlowState.id,
        returnertFraROLTidspunkt = rolReturnedDate,
    )
}

private fun Set<Saksdokument>.mapToSkjemaV2(): List<BehandlingSkjemaV2.Dokument> =
    map {
        BehandlingSkjemaV2.Dokument(
            journalpostId = it.journalpostId,
            dokumentInfoId = it.dokumentInfoId
        )
    }

fun Ankebehandling.mapToSkjemaV2(): BehandlingSkjemaV2 {

    return BehandlingSkjemaV2(
        id = id.toString(),
        klager = klager.mapToSkjemaV2(),
        klagersProsessfullmektig = klager.prosessfullmektig?.mapToSkjemaV2(),
        sakenGjelder = sakenGjelder.mapToSkjemaV2(),
        tema = ytelse.toTema().mapToSkjemaV2(),
        ytelse = ytelse.mapToSkjemaV2(),
        type = type.mapToSkjemaV2(),
        kildeReferanse = kildeReferanse,
        sakFagsystem = fagsystem.mapToSkjemaV2(),
        sakFagsakId = fagsakId,
        innsendtDato = innsendt,
        forrigeVedtaksDato = klageVedtaksDato,
        forrigeBehandlendeEnhet = klageBehandlendeEnhet.let { BehandlingSkjemaV2.Enhet(it) },
        sakMottattKaDato = mottattKlageinstans,
        avsluttetAvSaksbehandlerTidspunkt = avsluttetAvSaksbehandler,
        avsluttetTidspunkt = avsluttet,
        fristDato = frist,
        gjeldendeTildeling = tildeling?.mapToSkjemaV2(),
        medunderskriver = medunderskriver?.mapToSkjemaV2(),
        medunderskriverFlowStateId = medunderskriverFlowState.id,
        hjemler = hjemler.map { it.mapToSkjemaV2() },
        opprettetTidspunkt = created,
        sistEndretTidspunkt = modified,
        kildesystem = fagsystem.mapToSkjemaV2(),
        saksdokumenter = saksdokumenter.mapToSkjemaV2(),
        vedtak =
        BehandlingSkjemaV2.Vedtak(
            utfall = utfall?.mapToSkjemaV2(),
            hjemler = registreringshjemler.map { it.mapToSkjemaV2() },
        ),
        sattPaaVent = sattPaaVent?.from,
        sattPaaVentExpires = sattPaaVent?.to,
        sattPaaVentReason = sattPaaVent?.reason,
        status = BehandlingSkjemaV2.StatusType.valueOf(getStatus().name),
        feilregistrert = feilregistrering?.registered,
        rolIdent = rolIdent,
        rolFlowStateId = rolFlowState.id,
        returnertFraROLTidspunkt = rolReturnedDate,
    )
}

fun AnkeITrygderettenbehandling.mapToSkjemaV2(): BehandlingSkjemaV2 {

    return BehandlingSkjemaV2(
        id = id.toString(),
        klager = klager.mapToSkjemaV2(),
        klagersProsessfullmektig = klager.prosessfullmektig?.mapToSkjemaV2(),
        sakenGjelder = sakenGjelder.mapToSkjemaV2(),
        tema = ytelse.toTema().mapToSkjemaV2(),
        ytelse = ytelse.mapToSkjemaV2(),
        type = type.mapToSkjemaV2(),
        kildeReferanse = kildeReferanse,
        sakFagsystem = fagsystem.mapToSkjemaV2(),
        sakFagsakId = fagsakId,
        sakMottattKaDato = mottattKlageinstans,
        avsluttetAvSaksbehandlerTidspunkt = avsluttetAvSaksbehandler,
        avsluttetTidspunkt = avsluttet,
        fristDato = frist,
        gjeldendeTildeling = tildeling?.mapToSkjemaV2(),
        medunderskriver = medunderskriver?.mapToSkjemaV2(),
        medunderskriverFlowStateId = medunderskriverFlowState.id,
        hjemler = hjemler.map { it.mapToSkjemaV2() },
        opprettetTidspunkt = created,
        sistEndretTidspunkt = modified,
        kildesystem = fagsystem.mapToSkjemaV2(),
        saksdokumenter = saksdokumenter.mapToSkjemaV2(),
        vedtak =
        BehandlingSkjemaV2.Vedtak(
            utfall = utfall?.mapToSkjemaV2(),
            hjemler = registreringshjemler.map { it.mapToSkjemaV2() },
        ),
        status = BehandlingSkjemaV2.StatusType.valueOf(getStatus().name),
        feilregistrert = feilregistrering?.registered,
        sattPaaVent = sattPaaVent?.from,
        sattPaaVentExpires = sattPaaVent?.to,
        sattPaaVentReason = sattPaaVent?.reason,
        rolIdent = rolIdent,
        rolFlowStateId = rolFlowState.id,
        returnertFraROLTidspunkt = rolReturnedDate,

    )
}

data class BehandlingSkjemaV2(
    val id: String,
    val klager: PersonEllerOrganisasjon,
    val klagersProsessfullmektig: PersonEllerOrganisasjon?,
    val sakenGjelder: PersonEllerOrganisasjon,
    val tema: Kode,
    val ytelse: Kode,
    val type: Kode,
    val kildeReferanse: String,
    val sakFagsystem: Kode,
    val sakFagsakId: String,
    val innsendtDato: LocalDate? = null,
    val mottattFoersteinstansDato: LocalDate? = null,
    //Nytt navn på avsenderSaksbehandlerFoersteinstans, legger til null som default. Brukes kun til klagebehandling.
    val forrigeSaksbehandler: Saksbehandler? = null,
    //Nytt navn på avsenderEnhetFoersteinstans
    val forrigeBehandlendeEnhet: Enhet? = null,
    //Nytt felt, brukes kun til ankebehandling.
    val forrigeVedtaksDato: LocalDate? = null,
    //Nytt navn på mottattKlageinstansTidspunkt
    val sakMottattKaDato: LocalDateTime,
    val avsluttetAvSaksbehandlerTidspunkt: LocalDateTime?,
    val returnertFraROLTidspunkt: LocalDateTime?,
    val avsluttetTidspunkt: LocalDateTime?,
    val fristDato: LocalDate?,
    val gjeldendeTildeling: TildeltSaksbehandler?,
    val medunderskriver: TildeltMedunderskriver?,
    val medunderskriverFlowStateId: String,
    val hjemler: List<Kode>,
    val opprettetTidspunkt: LocalDateTime,
    val sistEndretTidspunkt: LocalDateTime,
    val kildesystem: Kode,

    val saksdokumenter: List<Dokument>,
    val vedtak: Vedtak?,
    val sattPaaVent: LocalDate?,
    val sattPaaVentExpires: LocalDate?,
    val sattPaaVentReason: String?,
    val status: StatusType,
    val feilregistrert: LocalDateTime?,
    val rolIdent: String?,
    val rolFlowStateId: String,
) {

    data class Vedtak(
        val utfall: Kode?,
        val hjemler: List<Kode>,
    )

    enum class StatusType {
        IKKE_TILDELT, TILDELT, MEDUNDERSKRIVER_VALGT, SENDT_TIL_MEDUNDERSKRIVER, RETURNERT_TIL_SAKSBEHANDLER, AVSLUTTET_AV_SAKSBEHANDLER, FULLFOERT, UKJENT, SATT_PAA_VENT, FEILREGISTRERT
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