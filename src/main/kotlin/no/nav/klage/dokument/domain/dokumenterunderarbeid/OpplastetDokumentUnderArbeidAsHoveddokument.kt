package no.nav.klage.dokument.domain.dokumenterunderarbeid

import jakarta.persistence.*
import no.nav.klage.dokument.api.view.InngaaendeKanal
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.oppgave.domain.klage.BehandlingRole
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*


@Entity
@DiscriminatorValue("opplastetdokument")
class OpplastetDokumentUnderArbeidAsHoveddokument(
    @Column(name = "size")
    var size: Long?,
    @Column(name = "mellomlager_id")
    override var mellomlagerId: String?,
    @Column(name = "mellomlagret_date")
    override var mellomlagretDate: LocalDateTime?,
    @Column(name = "dato_mottatt")
    var datoMottatt: LocalDate?,
    @Enumerated(EnumType.STRING)
    @Column(name = "inngaaende_kanal")
    var inngaaendeKanal: InngaaendeKanal?,

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
) : DokumentUnderArbeidAsMellomlagret, DokumentUnderArbeidAsHoveddokument(
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
    avsenderMottakerInfoSet = avsenderMottakerInfoSet,
    dokarkivReferences = dokarkivReferences,
    journalfoerendeEnhetId = journalfoerendeEnhetId,
){
    fun asVedlegg(parentId: UUID): OpplastetDokumentUnderArbeidAsVedlegg {
        return OpplastetDokumentUnderArbeidAsVedlegg(
            size = size,
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
        )
    }
}