package no.nav.klage.oppgave.domain

import jakarta.persistence.*
import no.nav.klage.oppgave.eventlisteners.OurRevisionListener
import org.hibernate.envers.RevisionEntity
import org.hibernate.envers.RevisionNumber
import org.hibernate.envers.RevisionTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "revision", schema = "klage")
@RevisionEntity(OurRevisionListener::class)
class OurRevision(
    @Id
    @SequenceGenerator(name = "klage.revision_seq", sequenceName = "klage.revision_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "klage.revision_seq")
    @RevisionNumber
    var id: Long?,
    @RevisionTimestamp
    var timestamp: LocalDateTime?,
    var actor: String,
    var request: String?,
)