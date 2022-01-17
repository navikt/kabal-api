package no.nav.klage.oppgave.domain

import no.nav.klage.kodeverk.*
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
class Behandling(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Embedded
    var klager: Klager,
    @Embedded
    var sakenGjelder: SakenGjelder,
    @Column(name = "ytelse_id")
    @Convert(converter = YtelseConverter::class)
    val ytelse: Ytelse,
    @Column(name = "type_id")
    @Convert(converter = TypeConverter::class)
    var type: Type,
    @Column(name = "kilde_referanse")
    val kildeReferanse: String,
    @Column(name = "dato_mottatt_klageinstans")
    val mottattKlageinstans: LocalDateTime,
    @Column(name = "mottak_id")
    val mottakId: UUID,
    @Column(name = "kildesystem")
    @Convert(converter = FagsystemConverter::class)
    val kildesystem: Fagsystem,
    @Column(name = "modified")
    var modified: LocalDateTime = LocalDateTime.now(),
    @Column(name = "created")
    val created: LocalDateTime = LocalDateTime.now(),
    @Column(name = "kaka_kvalitetsvurdering_id", nullable = true)
    var kakaKvalitetsvurderingId: UUID? = null,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "behandling_id", referencedColumnName = "id", nullable = false)
    @Fetch(FetchMode.SELECT)
    @BatchSize(size = 100)
    val tildelingHistorikk: MutableSet<TildelingHistorikk> = mutableSetOf(),
    @Embedded
    @AttributeOverrides(
        value = [
            AttributeOverride(name = "saksbehandlerident", column = Column(name = "tildelt_saksbehandlerident")),
            AttributeOverride(name = "enhet", column = Column(name = "tildelt_enhet")),
            AttributeOverride(name = "tidspunkt", column = Column(name = "dato_behandling_tildelt"))
        ]
    )
    var tildeling: Tildeling? = null,
    @Column(name = "frist")
    var frist: LocalDate? = null,
    @Column(name = "dato_behandling_avsluttet")
    var avsluttet: LocalDateTime? = null,
    @Column(name = "dato_innsendt")
    val innsendt: LocalDate? = null,
    @Column(name = "sak_fagsak_id")
    val sakFagsakId: String? = null,
    @Column(name = "sak_fagsystem")
    @Convert(converter = FagsystemConverter::class)
    val sakFagsystem: Fagsystem? = null,
    @Column(name = "dvh_referanse")
    val dvhReferanse: String? = null,
    //Her går vi mot en løsning der en behandling har flere delbehandlingerer, som nok er bedre begrep enn vedtak.
    //Trenger en markering av hvilken delbehandlinger som er den gjeldende.
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "behandling_id", referencedColumnName = "id", nullable = false)
    val delbehandlinger: Set<Delbehandling>,
    //Liste med dokumenter fra Joark. De dokumentene saksbehandler krysser av for havner her. Bør være i delbehandlinger. Kopierer fra forrige når ny delbehandlinger opprettes.
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "behandling_id", referencedColumnName = "id", nullable = false)
    @Fetch(FetchMode.SELECT)
    @BatchSize(size = 100)
    val saksdokumenter: MutableSet<Saksdokument> = mutableSetOf(),
)