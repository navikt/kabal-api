package no.nav.klage.oppgave.clients.kabaldocument.model.response

import no.nav.klage.oppgave.clients.kabaldocument.model.Rolle
import no.nav.klage.oppgave.domain.klage.PartId
import java.util.*

data class DokumentEnhetFullfoerOutput(
    val brevMottakerWithJoarkAndDokDistInfoList: List<BrevMottakerWithJoarkAndDokDistInfo>
)

data class BrevMottakerWithJoarkAndDokDistInfo(
    val partId: PartId,
    val navn: String?,
    val rolle: Rolle,
    val journalpostId: JournalpostId,
    val dokdistReferanse: UUID?
)

data class JournalpostId(val value: String)
