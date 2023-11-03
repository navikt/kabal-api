package no.nav.klage.dokument.domain.dokumenterunderarbeid

import jakarta.persistence.*
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.oppgave.domain.klage.BehandlingRole
import java.time.LocalDateTime
import java.util.*

@Entity
abstract class DokumentUnderArbeidAsHoveddokument(
    @Column(name = "dokument_enhet_id")
    var dokumentEnhetId: UUID? = null,
    @ElementCollection
    @CollectionTable(
        schema = "klage",
        name = "dokument_under_arbeid_brevmottaker_ident",
        joinColumns = [JoinColumn(name = "dokument_under_arbeid_id", referencedColumnName = "id", nullable = false)]
    )
    @Column(name = "identifikator")
    var brevmottakerIdents: Set<String> = setOf(),

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
    dokarkivReferences: MutableSet<DokumentUnderArbeidDokarkivReference> = mutableSetOf(),
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
    dokarkivReferences = dokarkivReferences,
)
