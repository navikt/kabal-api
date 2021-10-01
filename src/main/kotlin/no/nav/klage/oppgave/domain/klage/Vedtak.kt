package no.nav.klage.oppgave.domain.klage

import no.nav.klage.oppgave.domain.kodeverk.*
import no.nav.klage.oppgave.exceptions.BrevMottakerNotFoundException
import java.time.LocalDateTime
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "vedtak", schema = "klage")
class Vedtak(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "utfall_id")
    @Convert(converter = UtfallConverter::class)
    var utfall: Utfall? = null,
    @Column(name = "grunn_id")
    @Convert(converter = GrunnConverter::class)
    var grunn: Grunn? = null,
    @ElementCollection(targetClass = Hjemmel::class, fetch = FetchType.EAGER)
    @CollectionTable(
        name = "vedtak_hjemmel",
        schema = "klage",
        joinColumns = [JoinColumn(name = "vedtak_id", referencedColumnName = "id", nullable = false)]
    )
    @Convert(converter = HjemmelConverter::class)
    @Column(name = "id")
    var hjemler: MutableSet<Hjemmel> = mutableSetOf(),
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "vedtak_id", referencedColumnName = "id", nullable = false)
    var brevmottakere: MutableSet<BrevMottaker> = mutableSetOf(),
    @Column(name = "modified")
    var modified: LocalDateTime = LocalDateTime.now(),
    @Column(name = "created")
    val created: LocalDateTime = LocalDateTime.now(),
    //Dette feltet er ikke egentlig lenger i bruk, det er bare på BrevMottaker dette lagres nå.
    //En kommentar et annet sted i koden sier at "Brukes bare for å slette gamle journalposter på vedtak"
    @Column(name = "journalpost_id")
    var journalpostId: String? = null,
    @Column(name = "opplastet")
    var opplastet: LocalDateTime? = null,
    //Jeg tror ikke denne er i bruk lenger heller, den settes så vidt jeg kan se aldri.
    //MEN, den LESES flere steder, f.eks forventes det at den er satt ifm data som sendes til DVH. Burde man der brukt ferdigDistribuert i stedet?
    @Column(name = "ferdigstilt_i_joark")
    var ferdigstiltIJoark: LocalDateTime? = null,
    @Column(name = "ferdig_distribuert")
    var ferdigDistribuert: LocalDateTime? = null,
    @Column(name = "avsluttet_av_saksbehandler")
    var avsluttetAvSaksbehandler: LocalDateTime? = null,
    @Column(name = "mellomlager_id")
    var mellomlagerId: String? = null
) {
    override fun toString(): String {
        return "Vedtak(id=$id, " +
                "modified=$modified, " +
                "created=$created)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Vedtak

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    fun getMottaker(mottakerId: UUID): BrevMottaker =
        brevmottakere.firstOrNull { it.id == mottakerId }
            ?: throw BrevMottakerNotFoundException("Brevmottaker med id $mottakerId ikke funnet")

    fun leggTilSakenGjelderSomBrevmottaker(sakenGjelder: SakenGjelder) {
        brevmottakere.add(
            BrevMottaker(
                partId = sakenGjelder.partId,
                rolle = Rolle.SAKEN_GJELDER
            )
        )
    }

    fun leggTilKlagerSomBrevmottaker(klager: Klager) {
        brevmottakere.add(
            BrevMottaker(
                partId = klager.partId,
                rolle = Rolle.KLAGER
            )
        )
    }

    fun leggTilProsessfullmektigSomBrevmottaker(prosessfullmektig: Prosessfullmektig) {
        brevmottakere.add(
            BrevMottaker(
                partId = prosessfullmektig.partId,
                rolle = Rolle.PROSESSFULLMEKTIG
            )
        )
    }

    fun erIkkeFerdigDistribuert() = ferdigDistribuert == null

    fun harIngenBrevMottakere(): Boolean = brevmottakere.isEmpty()
}
