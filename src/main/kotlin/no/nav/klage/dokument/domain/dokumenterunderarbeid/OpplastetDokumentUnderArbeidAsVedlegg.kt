package no.nav.klage.dokument.domain.dokumenterunderarbeid

import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import no.nav.klage.oppgave.domain.behandling.BehandlingRole
import org.hibernate.envers.Audited
import java.time.LocalDateTime
import java.util.*


@Entity
@DiscriminatorValue("opplastetdokument_vedlegg")
@Audited
class OpplastetDokumentUnderArbeidAsVedlegg(
    @Column(name = "size")
    var size: Long?,
    @Column(name = "mellomlager_id")
    override var mellomlagerId: String?,
    @Column(name = "mellomlagret_date")
    override var mellomlagretDate: LocalDateTime?,

    //Common properties
    id: UUID = UUID.randomUUID(),
    name: String,
    behandlingId: UUID,
    created: LocalDateTime,
    modified: LocalDateTime,
    markertFerdig: LocalDateTime? = null,
    markertFerdigBy: String? = null,
    ferdigstilt: LocalDateTime? = null,
    parentId: UUID,
    creatorIdent: String,
    creatorRole: BehandlingRole,
) : DokumentUnderArbeidAsMellomlagret, DokumentUnderArbeidAsVedlegg(
    id = id,
    name = name,
    behandlingId = behandlingId,
    created = created,
    modified = modified,
    markertFerdig = markertFerdig,
    markertFerdigBy = markertFerdigBy,
    ferdigstilt = ferdigstilt,
    parentId = parentId,
    creatorIdent = creatorIdent,
    creatorRole = creatorRole,
)