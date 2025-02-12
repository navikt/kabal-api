package no.nav.klage.oppgave.domain

import jakarta.persistence.Entity
import jakarta.persistence.Table
import no.nav.klage.oppgave.eventlisteners.OurRevisionListener
import org.hibernate.envers.DefaultRevisionEntity
import org.hibernate.envers.RevisionEntity

@Entity
@Table(name = "revision", schema = "klage")
@RevisionEntity(OurRevisionListener::class)
class OurRevision(
    var actor: String,
    var request: String?,
) : DefaultRevisionEntity()