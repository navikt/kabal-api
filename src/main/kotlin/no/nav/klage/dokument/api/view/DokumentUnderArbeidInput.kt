package no.nav.klage.dokument.api.view

import java.util.*

data class HovedDokumentInput(val eksternReferanse: UUID)

data class SmartHovedDokumentInput(
    val json: String,
    val tittel: String?
)

data class PersistentDokumentIdInput(val dokumentId: UUID)

data class OptionalPersistentDokumentIdInput(val dokumentId: UUID?)

data class DokumentTitleInput(val title: String)

data class DokumentTypeInput(val dokumentTypeId: String)