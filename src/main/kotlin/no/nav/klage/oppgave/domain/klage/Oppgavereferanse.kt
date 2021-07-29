package no.nav.klage.oppgave.domain.klage

import org.springframework.data.domain.Persistable
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "mottak_oppgave", schema = "klage")
class Oppgavereferanse(
    @Id
    @JvmField
    val id: UUID = UUID.randomUUID(),
    @Column(name = "oppgave_id")
    val oppgaveId: Long
) : Persistable<UUID> {

    override fun getId(): UUID = id
    
    @Transient
    private var isNew = true

    override fun isNew(): Boolean {
        return isNew
    }

    @PrePersist
    @PostLoad
    fun markNotNew() {
        isNew = false
    }
}
