package no.nav.klage.oppgave.clients.kabaldocument.model.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
data class DokumentEnhetFullfoerOutput(
    //Tidligere journalførte dokumenter og vedleggsoversikt er ikke del av denne lista, kun nye smartdokumenter og opplastede dokumenter
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