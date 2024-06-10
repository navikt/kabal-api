package no.nav.klage.dokument.domain.dokumenterunderarbeid

import jakarta.persistence.*
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.oppgave.domain.klage.BehandlingRole
import java.time.LocalDateTime
import java.util.*


@Entity
@DiscriminatorValue("smartdokument")
class SmartdokumentUnderArbeidAsHoveddokument(
    @Column(name = "size")
    override var size: Long?,
    @Column(name = "smarteditor_id")
    override val smartEditorId: UUID,
    @Column(name = "smarteditor_template_id")
    override var smartEditorTemplateId: String,
    @Column(name = "mellomlager_id")
    override var mellomlagerId: String?,
    @Column(name = "mellomlagret_date")
    override var mellomlagretDate: LocalDateTime?,
    @Enumerated(EnumType.STRING)
    @Column(name = "language")
    override var language: Language,
    @Column(name = "mellomlagret_version")
    override var mellomlagretVersion: Int?,

    //Common properties
    id: UUID = UUID.randomUUID(),
    name: String,
    behandlingId: UUID,
    created: LocalDateTime,
    modified: LocalDateTime,
    markertFerdig: LocalDateTime? = null,
    markertFerdigBy: String? = null,
    ferdigstilt: LocalDateTime? = null,
    creatorIdent: String,
    creatorRole: BehandlingRole,
    dokumentType: DokumentType,
    dokumentEnhetId: UUID? = null,
    avsenderMottakerInfoSet: MutableSet<DokumentUnderArbeidAvsenderMottakerInfo> = mutableSetOf(),
    dokarkivReferences: MutableSet<DokumentUnderArbeidDokarkivReference> = mutableSetOf(),
    journalfoerendeEnhetId: String?,
) : DokumentUnderArbeidAsMellomlagret, DokumentUnderArbeidAsSmartdokument, DokumentUnderArbeidAsHoveddokument(
    id = id,
    name = name,
    behandlingId = behandlingId,
    created = created,
    modified = modified,
    markertFerdig = markertFerdig,
    markertFerdigBy = markertFerdigBy,
    ferdigstilt = ferdigstilt,
    creatorIdent = creatorIdent,
    creatorRole = creatorRole,
    dokumentType = dokumentType,
    dokumentEnhetId = dokumentEnhetId,
    dokarkivReferences = dokarkivReferences,
    journalfoerendeEnhetId = journalfoerendeEnhetId,
    avsenderMottakerInfoSet = avsenderMottakerInfoSet,
) {
    fun asVedlegg(parentId: UUID): SmartdokumentUnderArbeidAsVedlegg {
        return SmartdokumentUnderArbeidAsVedlegg(
            size = size,
            smartEditorId = smartEditorId,
            smartEditorTemplateId = smartEditorTemplateId,
            mellomlagerId = mellomlagerId,
            mellomlagretDate = mellomlagretDate,
            id = id,
            name = name,
            behandlingId = behandlingId,
            created = created,
            modified = modified,
            markertFerdig = markertFerdig,
            markertFerdigBy = markertFerdigBy,
            ferdigstilt = ferdigstilt,
            creatorIdent = creatorIdent,
            creatorRole = creatorRole,
            parentId = parentId,
            language = language,
            mellomlagretVersion = mellomlagretVersion,
        )
    }
}
