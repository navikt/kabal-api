package no.nav.klage.dokument.domain.dokumenterunderarbeid

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.DynamicUpdate
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "innholdsfortegnelse", schema = "klage")
@DynamicUpdate
open class Innholdsfortegnelse(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "mellomlager_id")
    open var mellomlagerId: String?,
    @Column(name = "hoveddokument_id")
    open var hoveddokumentId: UUID? = null,
    @Column(name = "created")
    open var created: LocalDateTime = LocalDateTime.now(),
    @Column(name = "modified")
    open var modified: LocalDateTime = LocalDateTime.now(),
) : Comparable<Innholdsfortegnelse> {

    override fun compareTo(other: Innholdsfortegnelse): Int =
        created.compareTo(other.created)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Innholdsfortegnelse

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "Innholdsfortegnelse(id=$id)"
    }

}