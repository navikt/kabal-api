package no.nav.klage.dokument.domain.dokumenterunderarbeid

import java.time.LocalDateTime
import java.util.*

interface DokumentUnderArbeidAsSmartdokument: DokumentUnderArbeidAsMellomlagret {
    var size: Long?
    val id: UUID
    val smartEditorId: UUID
    var smartEditorTemplateId: String
    var modified: LocalDateTime
    var language: Language
    var mellomlagretVersion: Int?
}

interface DokumentUnderArbeidAsMellomlagret {
    var mellomlagerId: String?
    var mellomlagretDate: LocalDateTime?
}

interface DokumentUnderArbeidAsOpplastet : DokumentUnderArbeidAsMellomlagret {
    val id: UUID
    var name: String
    var size: Long?
    var modified: LocalDateTime
    var status: DokumentStatus
    var scannedGeneration: Long?

    val isDone: Boolean
        get() = status == DokumentStatus.DONE
}