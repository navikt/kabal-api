package no.nav.klage.oppgave.util

import no.nav.klage.oppgave.clients.saf.graphql.Journalpost
import java.time.format.DateTimeFormatter

fun getSortKey(journalpost: Journalpost, dokumentInfoId: String): String {
    val bigNumber = 99_999
    return journalpost.datoOpprettet.format(DateTimeFormatter.ISO_DATE_TIME) +
            journalpost.journalpostId +
            (bigNumber - (journalpost.dokumenter?.indexOfFirst { it.dokumentInfoId == dokumentInfoId } ?: -1))
}