package no.nav.klage.dokument.domain.dokumenterunderarbeid

import org.hibernate.annotations.DynamicUpdate
import java.time.LocalDateTime
import java.util.*
import javax.persistence.*

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@Table(name = "dokument_under_arbeid", schema = "klage")
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING)
@DynamicUpdate
abstract class DokumentUnderArbeid(
    @EmbeddedId
    open var id: DokumentId,
    //Innfører denne fordi vi trenger noe som er persistent selv når et dokument bytter fra å være et hoveddokument til å bli et vedlegg eller motsatt, og å bruke id skaper litt krøll i persistence contexten..
    @Embedded
    open var persistentDokumentId: PersistentDokumentId,
    @Column(name = "mellomlager_id")
    open var mellomlagerId: String,
    @Column(name = "opplastet")
    open var opplastet: LocalDateTime,
    @Column(name = "size")
    open var size: Long,
    @Column(name = "name")
    open var name: String,
    @Column(name = "smarteditor_id")
    open var smartEditorId: UUID? = null,
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
) : Comparable<DokumentUnderArbeid> {
    companion object {
        const val HOVED_DOKUMENT = "hoved"
        const val VEDLEGG = "vedlegg"
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DokumentUnderArbeid) return false

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun compareTo(other: DokumentUnderArbeid): Int {
        return this.created.compareTo(other.created)
    }

    fun erMarkertFerdig(): Boolean {
        return markertFerdig != null
    }

    fun erFerdigstilt(): Boolean {
        return ferdigstilt != null
    }
}


