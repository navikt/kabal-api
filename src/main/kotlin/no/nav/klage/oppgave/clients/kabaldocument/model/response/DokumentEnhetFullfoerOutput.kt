package no.nav.klage.oppgave.clients.kabaldocument.model.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
data class DokumentEnhetFullfoerOutput(
    val sourceReferenceWithJoarkReferencesList: List<SourceReferenceWithJoarkReferences>,
)

data class SourceReferenceWithJoarkReferences(
    val sourceReference: UUID?,
    val joarkReferenceList: List<JoarkReference>
)

data class JoarkReference(
    val journalpostId: String,
    val dokumentInfoId: String,
)