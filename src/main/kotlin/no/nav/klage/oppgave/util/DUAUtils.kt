package no.nav.klage.oppgave.util

import no.nav.klage.oppgave.clients.saf.graphql.Journalpost

fun getSortKey(journalpost: Journalpost, dokumentInfoId: String): String {
        val bigNumber = 99_999
        return journalpost.datoOpprettet.toString() +
                journalpost.journalpostId +
                (bigNumber - (journalpost.dokumenter?.indexOfFirst { it.dokumentInfoId == dokumentInfoId } ?: -1))
    }