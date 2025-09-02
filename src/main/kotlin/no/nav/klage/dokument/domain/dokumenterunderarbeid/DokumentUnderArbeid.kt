package no.nav.klage.dokument.domain.dokumenterunderarbeid

import jakarta.persistence.*
import no.nav.klage.oppgave.domain.behandling.BehandlingRole
import org.hibernate.Hibernate
import org.hibernate.annotations.BatchSize
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.Fetch
import org.hibernate.annotations.FetchMode
import org.hibernate.envers.AuditJoinTable
import org.hibernate.envers.Audited
import java.time.LocalDateTime
import java.util.*

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@Table(name = "dokument_under_arbeid", schema = "klage")
@DynamicUpdate
@DiscriminatorColumn(name = "dokument_under_arbeid_type")
@Audited
abstract class DokumentUnderArbeid(
    @Id
    open val id: UUID = UUID.randomUUID(),
    @Column(name = "name")
    open var name: String,
    @Column(name = "behandling_id")
    open var behandlingId: UUID,
    @Column(name = "created")
    open var created: LocalDateTime = LocalDateTime.now(),
    @Column(name = "modified")
    open var modified: LocalDateTime = LocalDateTime.now(),
    @Column(name = "markert_ferdig")
    open var markertFerdig: LocalDateTime? = null,
    @Column(name = "markert_ferdig_by")
    open var markertFerdigBy: String? = null,
    @Column(name = "ferdigstilt")
    open var ferdigstilt: LocalDateTime? = null,
    @Column(name = "creator_ident")
    open var creatorIdent: String,
    @Column(name = "creator_role")
    @Enumerated(EnumType.STRING)
    open var creatorRole: BehandlingRole,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "dokument_under_arbeid_id", referencedColumnName = "id", nullable = false)
    @Fetch(FetchMode.SELECT)
    @BatchSize(size = 5)
    @AuditJoinTable(name = "dua_dokument_under_arbeid_dokarkiv_reference_aud")
    open var dokarkivReferences: MutableSet<DokumentUnderArbeidDokarkivReference> = mutableSetOf(),
) : Comparable<DokumentUnderArbeid> {

    override fun compareTo(other: DokumentUnderArbeid): Int =
        created.compareTo(other.created)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DokumentUnderArbeid

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "DokumentUnderArbeid(id=$id)"
    }

    fun erMarkertFerdig(): Boolean {
        return markertFerdig != null
    }

    fun erFerdigstilt(): Boolean {
        return ferdigstilt != null
    }

    fun ferdigstillHvisIkkeAlleredeFerdigstilt(tidspunkt: LocalDateTime) {
        if (ferdigstilt == null) {
            ferdigstilt = tidspunkt
            modified = tidspunkt
        }
    }

    fun markerFerdigHvisIkkeAlleredeMarkertFerdig(tidspunkt: LocalDateTime, saksbehandlerIdent: String) {
        if (markertFerdig == null) {
            markertFerdig = tidspunkt
            markertFerdigBy = saksbehandlerIdent
            modified = tidspunkt
        }
    }

    enum class DokumentUnderArbeidType {
        UPLOADED,
        SMART,
        JOURNALFOERT,
    }

    fun getType(): DokumentUnderArbeidType {
        return when (Hibernate.unproxy(this)) {
            is DokumentUnderArbeidAsSmartdokument -> {
                DokumentUnderArbeidType.SMART
            }

            is JournalfoertDokumentUnderArbeidAsVedlegg -> {
                DokumentUnderArbeidType.JOURNALFOERT
            }

            is OpplastetDokumentUnderArbeidAsVedlegg -> {
                DokumentUnderArbeidType.UPLOADED
            }

            is OpplastetDokumentUnderArbeidAsHoveddokument -> {
                DokumentUnderArbeidType.UPLOADED
            }

            else -> error("unknown type: ${this::class.java.name}")
        }
    }
}