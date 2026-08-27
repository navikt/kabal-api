package no.nav.klage.oppgave.domain.document

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.annotations.BatchSize
import org.hibernate.annotations.Fetch
import org.hibernate.annotations.FetchMode
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "merged_document", schema = "klage")
class MergedDocument(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "title", nullable = false)
    val title: String,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "merged_document_id", referencedColumnName = "id", nullable = false)
    @Fetch(FetchMode.SELECT)
    @BatchSize(size = 20)
    val documentsToMerge: MutableSet<DocumentToMerge>,
    @Column(name = "hash", nullable = false)
    val hash: String,
    @Column(name = "created", nullable = false)
    val created: LocalDateTime = LocalDateTime.now(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MergedDocument

        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String =
        "MergedDocument(id=$id, title='$title', documentsToMerge=$documentsToMerge, hash='$hash', created=$created)"
}
