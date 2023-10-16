package no.nav.klage.dokument.domain.dokumenterunderarbeid

import java.time.LocalDateTime
import java.util.*

interface DokumentUnderArbeidAsSmartdokument: DokumentUnderArbeidAsMellomlagret {
    var size: Long?
    val id: UUID
    val smartEditorId: UUID
    var smartEditorTemplateId: String
    var modified: LocalDateTime
}

interface DokumentUnderArbeidAsMellomlagret {
    var mellomlagerId: String?
    var mellomlagretDate: LocalDateTime?
}
