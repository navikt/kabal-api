package no.nav.klage.oppgave.domain

import jakarta.persistence.*
import no.nav.klage.oppgave.eventlisteners.OurRevisionListener
import org.hibernate.envers.DefaultRevisionEntity
import org.hibernate.envers.RevisionEntity
import org.hibernate.envers.RevisionNumber
import org.hibernate.envers.RevisionTimestamp

@Entity
@Table(name = "revision", schema = "klage")
@RevisionEntity(OurRevisionListener::class)
class OurRevision(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "revision_seq")
    @RevisionNumber
    var id: Long?,
    @RevisionTimestamp
    var timestamp: Long?,
    var actor: String,
    var request: String?,
) : DefaultRevisionEntity()