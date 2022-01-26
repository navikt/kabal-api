package no.nav.klage.oppgave.domain.klage

import no.nav.klage.kodeverk.*
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.oppgave.domain.Behandling
import no.nav.klage.oppgave.util.getLogger
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import javax.persistence.Column
import javax.persistence.DiscriminatorValue
import javax.persistence.Entity

@Entity
@DiscriminatorValue("anke")
class Ankebehandling(
    @Column(name = "klage_vedtaks_dato")
    val klageVedtaksDato: LocalDate? = null,
    @Column(name = "klage_behandlende_enhet")
    val klageBehandlendeEnhet: String,
    //Fins i noen tilfeller, men ikke alle.
    @Column(name = "klage_id")
    var klagebehandlingId: UUID? = null,

//    Finn ut hvordan dette skal fungere i anker etter hvert
//    @Column(name = "dato_behandling_avsluttet_av_saksbehandler")
//    var avsluttetAvSaksbehandler: LocalDateTime? = null,


    //Common properties between klage/anke
    id: UUID = UUID.randomUUID(),
    klager: Klager,
    sakenGjelder: SakenGjelder,
    ytelse: Ytelse,
    type: Type,
    kildeReferanse: String,
    dvhReferanse: String? = null,
    sakFagsystem: Fagsystem? = null,
    sakFagsakId: String? = null,
    //Umulig å vite innsendt-dato.
    innsendt: LocalDate? = null,
    //Settes automatisk i klage, må kunne justeres i anke. Bør også representeres i delbehandlinger. Må gjøres entydig i anke, hører antageligvis ikke hjemme i felles klasse.
    mottattKlageinstans: LocalDateTime,
    //TODO: Trenger denne være nullable? Den blir da alltid satt i createKlagebehandlingFromMottak?
    //Litt usikkert om dette hører mest hjemme her eller på delbehandlinger.
    frist: LocalDate? = null,
    //Hører hjemme på delbehandlinger, men her er det mer usikkerhet enn for medunderskriver. Litt om pragmatikken, bør se hva som er enklest å få til.
    tildeling: Tildeling? = null,
    //Hører hjemme på delbehandlinger, men her er det mer usikkerhet enn for medunderskriver
    tildelingHistorikk: MutableSet<TildelingHistorikk> = mutableSetOf(),
    //Hovedbehandling
    mottakId: UUID,
    kakaKvalitetsvurderingId: UUID? = null,
    created: LocalDateTime = LocalDateTime.now(),
    modified: LocalDateTime = LocalDateTime.now(),
    //Kommer fra innsending
    kildesystem: Fagsystem,
    delbehandlinger: Set<Delbehandling>,
    saksdokumenter: MutableSet<Saksdokument> = mutableSetOf(),
    hjemler: MutableSet<Hjemmel> = mutableSetOf(),
) : Behandling(
    id = id,
    klager = klager,
    sakenGjelder = sakenGjelder,
    ytelse = ytelse,
    type = type,
    kildeReferanse = kildeReferanse,
    mottattKlageinstans = mottattKlageinstans,
    mottakId = mottakId,
    kildesystem = kildesystem,
    modified = modified,
    created = created,
    kakaKvalitetsvurderingId = kakaKvalitetsvurderingId,
    tildelingHistorikk = tildelingHistorikk,
    tildeling = tildeling,
    frist = frist,
    innsendt = innsendt,
    sakFagsakId = sakFagsakId,
    sakFagsystem = sakFagsystem,
    dvhReferanse = dvhReferanse,
    delbehandlinger = delbehandlinger,
    saksdokumenter = saksdokumenter,
    hjemler = hjemler,
) {

    companion object {

        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val UTFALL_SOM_AVSLUTTER_BEHANDLINGEN: List<Utfall> =
            listOf(Utfall.TRUKKET, Utfall.OPPHEVET, Utfall.MEDHOLD, Utfall.UGUNST)
        private val UTFALL_SOM_KREVER_VIDERE_BEHANDLING: List<Utfall> =
            listOf(Utfall.STADFESTELSE, Utfall.AVVIST, Utfall.DELVIS_MEDHOLD)
    }

    override fun toString(): String {
        return "Ankebehandling(id=$id, " +
                "modified=$modified, " +
                "created=$created)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Ankebehandling

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    /**
     * Brukes til ES og statistikk per nå
     */
    override fun getStatus(): BehandlingStatus {
        return when {
            avsluttet != null -> BehandlingStatus.FULLFOERT
            avsluttetAvSaksbehandler != null -> BehandlingStatus.AVSLUTTET_AV_SAKSBEHANDLER
            //TODO: Hvordan skal dette fungere? Skal det vises medunderskriver etc her også f.eks? Denne må vi snakke om!
            ventetidStart != null && delbehandlinger.size > 1 -> BehandlingStatus.ANDRE_BEHANDLING
            ventetidStart != null && delbehandlinger.size <= 1 -> BehandlingStatus.PAA_VENT
            currentDelbehandling().medunderskriverFlyt == MedunderskriverFlyt.OVERSENDT_TIL_MEDUNDERSKRIVER -> BehandlingStatus.SENDT_TIL_MEDUNDERSKRIVER
            currentDelbehandling().medunderskriverFlyt == MedunderskriverFlyt.RETURNERT_TIL_SAKSBEHANDLER -> BehandlingStatus.RETURNERT_TIL_SAKSBEHANDLER
            currentDelbehandling().medunderskriver?.saksbehandlerident != null -> BehandlingStatus.MEDUNDERSKRIVER_VALGT
            tildeling?.saksbehandlerident != null -> BehandlingStatus.TILDELT
            tildeling?.saksbehandlerident == null -> BehandlingStatus.IKKE_TILDELT
            else -> BehandlingStatus.UKJENT
        }
    }

    /*
    Mulige utfall av første delbehandling:

    HVIS delbehandling er AVSLUTTET
     OG utfall er en av denne mengden: {STADFESTELSE, AVVIST, ?DELVIS_MEDHOLD?}
     DA skal innstillingsbrev sendes til bruker
       OG status skal settes til PÅ VENT
       OG Ankebehandlingen får en datoverdi for "ventetid påbegynt"
       "Ventetid påbegynt" er utledet av datoverdi for delbehandling AVSLUTTET

    HVIS delbehandling er AVSLUTTET
     OG utfall er en av denne mengden: {TRUKKET, OPPHEVET, MEDHOLD, UGUNST}
     DA skal infobrev sendes til bruker
       OG status er AVSLUTTET
       OG Ankbehandlingen anses som ferdig

    RETUR er ikke aktuelt for anker, skal ikke være et valg for saksbehandler

    SEARCH lager en liste med anker på vent basert på statusen PÅ VENT

    Dette fører til opprettelse av andre delbehandling:
     - Noen trykker på knappen Gjenåpne

     Situasjonen blir at vi har en ankebehandling med en åpen 2. delbehandling

     */

    override val avsluttetAvSaksbehandler: LocalDateTime?
        get() {

            if (delbehandlinger.isEmpty()) {
                return null
            }
            val sortedDelbehandlinger: List<Delbehandling> = delbehandlinger.sortedBy { it.created }
            if (sortedDelbehandlinger.size == 1) {
                return if (sortedDelbehandlinger.first().avsluttetAvSaksbehandler != null && sortedDelbehandlinger.first().utfall in UTFALL_SOM_AVSLUTTER_BEHANDLINGEN) {
                    sortedDelbehandlinger.first().avsluttetAvSaksbehandler
                } else {
                    null
                }
            }
            return sortedDelbehandlinger.second().avsluttetAvSaksbehandler
            //TODO: Når vi får behandlingen i retur fra Trygderetten, så er vel det mer en form for "etterbehandling"? Behandlingen bør komme opp i avsluttet-lista (i mine oppgaver og enhetens oppgaver) allerede når 2. behandling er ferdig?
        }

    override val avsluttet: LocalDateTime?
        get() {

            if (delbehandlinger.isEmpty()) {
                return null
            }
            val sortedDelbehandlinger: List<Delbehandling> = delbehandlinger.sortedBy { it.created }
            if (sortedDelbehandlinger.size == 1) {
                return if (sortedDelbehandlinger.first().avsluttet != null && sortedDelbehandlinger.first().utfall in UTFALL_SOM_AVSLUTTER_BEHANDLINGEN) {
                    sortedDelbehandlinger.first().avsluttet
                } else {
                    null
                }
            }
            return sortedDelbehandlinger.second().avsluttet
            //TODO: Når vi får behandlingen i retur fra Trygderetten, så er vel det mer en form for "etterbehandling"? Behandlingen bør komme opp i avsluttet-lista (i mine oppgaver og enhetens oppgaver) allerede når 2. behandling er ferdig?
        }

    override val ventetidStart: LocalDateTime?
        get() {
            return if (delbehandlinger.isEmpty()) {
                null
            } else {
                val foersteDelbehandling = delbehandlinger.minByOrNull { it.created }!!
                if (foersteDelbehandling.avsluttet != null && foersteDelbehandling.utfall in UTFALL_SOM_KREVER_VIDERE_BEHANDLING) {
                    foersteDelbehandling.avsluttet
                } else {
                    null
                }
            }
        }

    override val ventetidFrist: LocalDateTime?
        get() {
            return ventetidStart?.plusWeeks(4)
        }
}

private fun List<Delbehandling>.second(): Delbehandling = this.elementAt(1)

