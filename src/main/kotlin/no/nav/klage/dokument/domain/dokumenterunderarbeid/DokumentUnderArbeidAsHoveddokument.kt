package no.nav.klage.dokument.domain.dokumenterunderarbeid

import jakarta.persistence.*
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.kodeverk.DokumentTypeConverter
import no.nav.klage.oppgave.domain.klage.BehandlingRole
import no.nav.klage.oppgave.util.isInngaaende
import no.nav.klage.oppgave.util.isUtgaaende
import org.hibernate.annotations.BatchSize
import org.hibernate.annotations.Fetch
import org.hibernate.annotations.FetchMode
import org.hibernate.envers.AuditJoinTable
import org.hibernate.envers.Audited
import java.time.LocalDateTime
import java.util.*

@Entity
@Audited
abstract class DokumentUnderArbeidAsHoveddokument(
    @Column(name = "dokument_enhet_id")
    open var dokumentEnhetId: UUID? = null,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "dokument_under_arbeid_id", referencedColumnName = "id", nullable = false)
    @Fetch(FetchMode.SELECT)
    @BatchSize(size = 5)
    @AuditJoinTable(name = "dua_dokument_under_arbeid_avsender_mottaker_info_aud")
    open val avsenderMottakerInfoSet: MutableSet<DokumentUnderArbeidAvsenderMottakerInfo> = mutableSetOf(),
    @Column(name = "journalfoerende_enhet_id")
    open var journalfoerendeEnhetId: String?,
    @Column(name = "dokument_type_id")
    @Convert(converter = DokumentTypeConverter::class)
    open var dokumentType: DokumentType,


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
    dokarkivReferences = dokarkivReferences,
) {
    fun isInngaaende(): Boolean {
        return dokumentType.isInngaaende()
    }

    fun isUtgaaende(): Boolean {
        return dokumentType.isUtgaaende()
    }
}
