package no.nav.klage.dokument.domain.dokumenterunderarbeid

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.*

@Entity
@Table(name = "dokument_under_arbeid_brevmottaker_info", schema = "klage")
class DokumentUnderArbeidBrevmottakerInfo(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "identifikator")
    val identifikator: String,
    @Column(name = "local_print")
    val localPrint: Boolean,
)