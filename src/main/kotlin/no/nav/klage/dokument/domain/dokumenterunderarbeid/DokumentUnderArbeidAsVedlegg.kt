package no.nav.klage.dokument.domain.dokumenterunderarbeid

import jakarta.persistence.Column
import jakarta.persistence.Entity
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.oppgave.domain.klage.BehandlingRole
import java.time.LocalDateTime
import java.util.*

@Entity
abstract class DokumentUnderArbeidAsVedlegg(
    @Column(name = "parent_id")
    var parentId: UUID? = null,

    //Common properties
    id: UUID = UUID.randomUUID(),
    name: String,
    behandlingId: UUID,
    created: LocalDateTime,
    modified: LocalDateTime,
    markertFerdig: LocalDateTime?,
    markertFerdigBy: String?,
    ferdigstilt: LocalDateTime?,
    creatorIdent: String,
    creatorRole: BehandlingRole,
    dokumentType: DokumentType?,
) : DokumentUnderArbeid(
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
)