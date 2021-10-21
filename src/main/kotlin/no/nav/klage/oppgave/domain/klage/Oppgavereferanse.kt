package no.nav.klage.oppgave.domain.klage

import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "mottak_oppgave", schema = "klage")
class Oppgavereferanse(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "oppgave_id")
    val oppgaveId: Long
)
