package no.nav.klage.dokument.domain.dokumenterunderarbeid

import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.oppgave.domain.klage.BehandlingRole
import java.time.LocalDateTime
import java.util.*

@Entity
@DiscriminatorValue("journalfoertdokument")
class JournalfoertDokumentUnderArbeidAsVedlegg (
    @Column(name = "opprettet")
    var opprettet: LocalDateTime,
    @Column(name = "journalfoert_dokument_journalpost_id")
    val journalpostId: String,
    @Column(name = "journalfoert_dokument_dokument_info_id")
    val dokumentInfoId: String,

    //Common properties
    id: UUID = UUID.randomUUID(),
    name: String,
    behandlingId: UUID,
    created: LocalDateTime,
    modified: LocalDateTime,
    markertFerdig: LocalDateTime?,
    markertFerdigBy: String?,
    ferdigstilt: LocalDateTime?,
    parentId: UUID?,
    creatorIdent: String,
    creatorRole: BehandlingRole,
    dokumentType: DokumentType?,
) : DokumentUnderArbeidAsVedlegg(
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
    dokumentType = dokumentType,
)