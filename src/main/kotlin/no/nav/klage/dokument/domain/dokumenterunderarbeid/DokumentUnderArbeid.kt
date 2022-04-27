package no.nav.klage.dokument.domain.dokumenterunderarbeid

import no.nav.klage.dokument.domain.kodeverk.BrevmottakerType
import no.nav.klage.dokument.domain.kodeverk.BrevmottakerTypeConverter
import org.hibernate.annotations.DynamicUpdate
import java.time.LocalDateTime
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "dokument_under_arbeid", schema = "klage")
@DynamicUpdate
open class DokumentUnderArbeid(
    @EmbeddedId
    open var id: DokumentId = DokumentId(UUID.randomUUID()),
    @Column(name = "mellomlager_id")
    open var mellomlagerId: String,
    @Column(name = "opplastet")
    open var opplastet: LocalDateTime,
    @Column(name = "size")
    open var size: Long,
    @Column(name = "name")
    open var name: String,
    @Column(name = "smarteditor_id")
    open var smartEditorId: UUID?,
    @Column(name = "behandling_id")
    open var behandlingId: UUID,
    @Column(name = "dokument_type")
    @Convert(converter = DokumentTypeConverter::class)
    open var dokumentType: DokumentType,
    @Column(name = "created")
    open var created: LocalDateTime = LocalDateTime.now(),
    @Column(name = "modified")
    open var modified: LocalDateTime = LocalDateTime.now(),
    @Column(name = "markert_ferdig")
    open var markertFerdig: LocalDateTime? = null,
    @Column(name = "ferdigstilt")
    open var ferdigstilt: LocalDateTime? = null,
    @Column(name = "dokument_enhet_id")
    open var dokumentEnhetId: UUID? = null,
    @Column(name = "journalpost_id")
    open var journalpostId: String? = null,
    @ElementCollection(targetClass = BrevmottakerType::class, fetch = FetchType.EAGER)
    @CollectionTable(
        name = "dokument_under_arbeid_brevmottaker_type",
        schema = "klage",
        joinColumns = [JoinColumn(name = "dokument_under_arbeid_id", referencedColumnName = "id", nullable = true)]
    )
    @Convert(converter = BrevmottakerTypeConverter::class)
    @Column(name = "id")
    var brevmottakerTyper: MutableSet<BrevmottakerType> = mutableSetOf(),
    @Embedded
    @AttributeOverrides(
        value = [
            AttributeOverride(name = "id", column = Column(name = "parent_id"))
        ]
    )
    open var parentId: DokumentId? = null,
) : Comparable<DokumentUnderArbeid> {

    override fun compareTo(other: DokumentUnderArbeid): Int =
        created.compareTo(other.created)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DokumentUnderArbeid

        if (id.id != other.id.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.id.hashCode()
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

    fun markerFerdigHvisIkkeAlleredeMarkertFerdig(tidspunkt: LocalDateTime) {
        if (markertFerdig == null) {
            markertFerdig = tidspunkt
            modified = tidspunkt
        }
    }

}