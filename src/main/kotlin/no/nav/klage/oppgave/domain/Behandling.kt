package no.nav.klage.oppgave.domain

import no.nav.klage.kodeverk.*
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.HjemmelConverter
import no.nav.klage.oppgave.domain.klage.*
import org.hibernate.annotations.BatchSize
import org.hibernate.annotations.Fetch
import org.hibernate.annotations.FetchMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import javax.persistence.*

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@Table(name = "behandling", schema = "klage")
@DiscriminatorColumn(name = "behandling_type")
abstract class Behandling(
    @Id
    open val id: UUID = UUID.randomUUID(),
    @Embedded
    open var klager: Klager,
    @Embedded
    open var sakenGjelder: SakenGjelder,
    @Column(name = "ytelse_id")
    @Convert(converter = YtelseConverter::class)
    open val ytelse: Ytelse,
    @Column(name = "type_id")
    @Convert(converter = TypeConverter::class)
    open var type: Type,
    @Column(name = "kilde_referanse")
    open val kildeReferanse: String,
    @Column(name = "dato_mottatt_klageinstans")
    open var mottattKlageinstans: LocalDateTime,
    //Remove duplicate
    @Column(name = "kildesystem")
    @Convert(converter = FagsystemConverter::class)
    open val kildesystem: Fagsystem,
    @Column(name = "modified")
    open var modified: LocalDateTime = LocalDateTime.now(),
    @Column(name = "created")
    open val created: LocalDateTime = LocalDateTime.now(),
    @Column(name = "kaka_kvalitetsvurdering_id", nullable = true)
    open var kakaKvalitetsvurderingId: UUID? = null,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "behandling_id", referencedColumnName = "id", nullable = false)
    @Fetch(FetchMode.SELECT)
    @BatchSize(size = 100)
    open val tildelingHistorikk: MutableSet<TildelingHistorikk> = mutableSetOf(),
    @Embedded
    @AttributeOverrides(
        value = [
            AttributeOverride(name = "saksbehandlerident", column = Column(name = "tildelt_saksbehandlerident")),
            AttributeOverride(name = "enhet", column = Column(name = "tildelt_enhet")),
            AttributeOverride(name = "tidspunkt", column = Column(name = "dato_behandling_tildelt"))
        ]
    )
    open var tildeling: Tildeling? = null,
    @Column(name = "frist")
    open var frist: LocalDate? = null,
    @Column(name = "sak_fagsak_id")
    open val sakFagsakId: String? = null,
    //Remove duplicate
    @Column(name = "sak_fagsystem")
    @Convert(converter = FagsystemConverter::class)
    open val sakFagsystem: Fagsystem? = null,
    @Column(name = "dvh_referanse")
    open val dvhReferanse: String? = null,
    //Her går vi mot en løsning der en behandling har flere delbehandlingerer, som nok er bedre begrep enn vedtak.
    //Trenger en markering av hvilken delbehandlinger som er den gjeldende.
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "behandling_id", referencedColumnName = "id", nullable = false)
    open val delbehandlinger: Set<Delbehandling>,
    //Liste med dokumenter fra Joark. De dokumentene saksbehandler krysser av for havner her. Bør være i delbehandlinger. Kopierer fra forrige når ny delbehandlinger opprettes.
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "behandling_id", referencedColumnName = "id", nullable = false)
    @Fetch(FetchMode.SELECT)
    @BatchSize(size = 100)
    open val saksdokumenter: MutableSet<Saksdokument> = mutableSetOf(),
    //Dette er søkehjemler, input fra førsteinstans.
    @ElementCollection(targetClass = Hjemmel::class, fetch = FetchType.EAGER)
    @CollectionTable(
        name = "behandling_hjemmel",
        schema = "klage",
        joinColumns = [JoinColumn(name = "behandling_id", referencedColumnName = "id", nullable = false)]
    )
    @Convert(converter = HjemmelConverter::class)
    @Column(name = "id")
    open var hjemler: Set<Hjemmel> = emptySet(),
    @Column(name = "satt_paa_vent")
    open var sattPaaVent: LocalDateTime? = null,
) {
    fun currentDelbehandling(): Delbehandling {
        return delbehandlinger.first()
    }

    var avsluttetAvSaksbehandler: LocalDateTime?
        get() = currentDelbehandling().avsluttetAvSaksbehandler
        set(avsluttetAvSaksbehandler) {
            currentDelbehandling().avsluttetAvSaksbehandler = avsluttetAvSaksbehandler
        }

    var avsluttet: LocalDateTime?
        get() = currentDelbehandling().avsluttet
        set(avsluttet) {
            currentDelbehandling().avsluttet = avsluttet
        }

    var medunderskriver: MedunderskriverTildeling?
        get() = currentDelbehandling().medunderskriver
        set(medunderskriver) {
            currentDelbehandling().medunderskriver = medunderskriver
        }

    var medunderskriverFlyt: MedunderskriverFlyt
        get() = currentDelbehandling().medunderskriverFlyt
        set(medunderskriverFlyt) {
            currentDelbehandling().medunderskriverFlyt = medunderskriverFlyt
        }

    /**
     * Brukes til ES og statistikk per nå
     */
    fun getStatus(): Status {
        return when {
            avsluttet != null -> Status.FULLFOERT
            avsluttetAvSaksbehandler != null -> Status.AVSLUTTET_AV_SAKSBEHANDLER
            sattPaaVent != null -> Status.SATT_PAA_VENT
            medunderskriverFlyt == MedunderskriverFlyt.OVERSENDT_TIL_MEDUNDERSKRIVER -> Status.SENDT_TIL_MEDUNDERSKRIVER
            medunderskriverFlyt == MedunderskriverFlyt.RETURNERT_TIL_SAKSBEHANDLER -> Status.RETURNERT_TIL_SAKSBEHANDLER
            medunderskriver?.saksbehandlerident != null -> Status.MEDUNDERSKRIVER_VALGT
            tildeling?.saksbehandlerident != null -> Status.TILDELT
            tildeling?.saksbehandlerident == null -> Status.IKKE_TILDELT
            else -> Status.UKJENT
        }
    }

    enum class Status {
        IKKE_TILDELT, TILDELT, MEDUNDERSKRIVER_VALGT, SENDT_TIL_MEDUNDERSKRIVER, RETURNERT_TIL_SAKSBEHANDLER, AVSLUTTET_AV_SAKSBEHANDLER, SATT_PAA_VENT, FULLFOERT, UKJENT
    }
}