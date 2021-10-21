package no.nav.klage.oppgave.clients.kabaldocument.model.response

import java.time.LocalDateTime

data class BrevMottakerDistribusjonOutput(
    val brevMottakerId: String,
    val opplastetDokumentId: String,
    val journalpostId: String,
    val ferdigstiltIJoark: LocalDateTime?,
    val dokdistReferanse: String?
)
