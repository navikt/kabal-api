package no.nav.klage.oppgave.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.*

@Entity
@Table(name = "person_protection", schema = "klage")
class PersonProtection(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "foedselsnummer", nullable = false, unique = true)
    val foedselsnummer: String,
    @Column(name = "fortrolig", nullable = false)
    var fortrolig: Boolean,
    @Column(name = "strengt_fortrolig", nullable = false)
    var strengtFortrolig: Boolean,
    @Column(name = "skjermet", nullable = false)
    var skjermet: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PersonProtection

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "PersonProtection(id=$id, foedselsnummer=$foedselsnummer, fortrolig=$fortrolig, strengtFortrolig=$strengtFortrolig, skjermet=$skjermet)"
    }
}