package no.nav.klage.oppgave.domain.kafka

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

const val DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS"
const val DATE_FORMAT = "yyyy-MM-dd"

/**
 * Brukes av DVH
 * StatistikkTilDVH er en hendelse i en Sak, knyttet til en konkret behandlingstype (eks. søknad, revurdering, endring, klage).
 * Vi sender dette typisk ved mottak, tildeling og fullføring.
 *
 * Navnendringer her krever at vi sender all data til DVH på nytt med nye navn. Derfor er vi forsiktige med å gjøre slik endringer
 * da dette blir litt dyrt. Hvis et felt endrer betydning så er det viktig å dokumentere.
 *
 * Hvis EØS kommer tilbake, legg til feltet `utenlandstilsnitt: String` (maxLength 100):
 * «Kode som beskriver behandlingens utlandstilsnitt i henhold til NAV spesialisering. I hoved sak vil denne koden
 * beskrive om saksbehandlingsfrister er i henhold til utlandssaker eller innlandssaker, men vil for mange
 * kildesystem være angitt med en høyere oppløsning.»
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class StatistikkTilDVH(
    /** Unik id for denne forsendelsen/eventen. Kan brukes til idempotency av konsumenter. */
    val eventId: UUID,
    /** Kode som angir hvilken enhet som er ansvarlig for behandlingen på det gjeldende tidspunktet. Dette vet vi ikke alltid. */
    val ansvarligEnhetKode: String? = null,
    /** Kode som angir hvilken type enhetskode det er snakk om, som oftest NORG. */
    val ansvarligEnhetType: String = "NORG",
    /** Feltet angir hvem som er avsender av dataene (navnet på systemet). */
    val avsender: String = "Kabal",
    /** Nøkkel til den aktuelle behandling, som kan identifisere den i kildesystemet. Typisk førsteinstans. */
    val behandlingId: String?,
    /** Nøkkel til den aktuelle behandling, som kan identifisere den i Kabal. */
    val behandlingIdKabal: String,
    /** Når enhet blir satt i KA */
    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = DATE_FORMAT,
    )
    val behandlingStartetKA: LocalDate?,
    /** Kode som angir den aktuelle behandlingens tilstand på gjeldende tidspunkt. */
    val behandlingStatus: BehandlingState,
    /** Kode som beskriver behandlingen, for eksempel, klage, anke, tilbakekreving o.l. */
    val behandlingType: BehandlingType,
    /** BrukerIDen til ev. medunderskriver. */
    val beslutter: String?,
    /** Tidspunktet da hendelsen faktisk ble gjennomført eller registrert i systemet. (format:$DATE_TIME_FORMAT) Dette er det tidspunkt der hendelsen faktisk er gjeldende fra. Ved for eksempel patching av data eller oppdatering tilbake i tid, skal tekniskTid være lik endringstidspunktet, mens endringstid angir tidspunktet da endringen offisielt gjelder fra. */
    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = DATE_TIME_FORMAT,
    )
    val endringstid: LocalDateTime,
    /** Liste av hjemler. */
    val hjemmel: List<String>,
    /** Den som sendte inn klagen. */
    val klager: Part,
    /** Vedtaksinstans. F.eks. Foreldrepenger. Kodeverk. */
    val opprinneligFagsaksystem: String,
    /** SakId fra vedtaksinstans. */
    val opprinneligFagsakId: String?,
    /** Når KA mottok oversendelsen. */
    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = DATE_FORMAT,
    )
    val overfoertKA: LocalDate,
    /** Utfall. */
    val resultat: String?,
    /** Den som har rettigheten. */
    val sakenGjelder: Part,
    /** Bruker IDen til saksbehandler ansvarlig for saken på gjeldende tidspunkt. Kan etterlates tom ved helautomatiske delprosesser i behandlingen. Bør bare fylles når det er manuelle skritt i saksbehandlingen som utføres. */
    val saksbehandler: String?,
    /** Enhet til gjeldende saksbehandler. */
    val saksbehandlerEnhet: String?,
    /** Tidspunktet da systemet ble klar over hendelsen. (format:$DATE_TIME_FORMAT). Dette er tidspunkt hendelsen ble endret i systemet. Sammen med funksjonellTid/endringstid, vil vi kunne holde rede på hva som er blitt rapportert tidligere og når det skjer endringer tilbake i tid. */
    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = DATE_TIME_FORMAT,
    )
    val tekniskTid: LocalDateTime,
    /** Dato for vedtaket i KA. */
    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = DATE_FORMAT,
    )
    val vedtaksdato: LocalDate?,
    /*
    Angir på hvilken versjon av kildekoden JSON stringen er generert på bakgrunn av.
    TODO find version?
     */
    val versjon: Int = 1,
    /** Enum-navnet på ytelsen i Kabal sitt kodeverk. F.eks. OMS_OMP. */
    val ytelseType: String,
) {
    data class Part(
        val verdi: String,
        val type: PartIdType,
    )

    enum class PartIdType {
        PERSON,
        VIRKSOMHET,
    }
}

enum class BehandlingType {
    KLAGE,
    ANKE,
    BEHANDLING_ETTER_TRYGDERETTEN_OPPHEVET,
    OMGJOERINGSKRAV,
    BEGJAERING_OM_GJENOPPTAK,
}

enum class BehandlingState {
    MOTTATT,
    TILDELT_SAKSBEHANDLER,
    AVSLUTTET,
    UKJENT,
    SENDT_TIL_TR,
    AVSLUTTET_I_TR_OG_NY_ANKEBEHANDLING_I_KA,
    AVSLUTTET_I_TR_OG_NY_GJENOPPTAKSBEHANDLING_I_KA,
    NY_ANKEBEHANDLING_I_KA_UTEN_TR,
    NY_GJENOPPTAKSBEHANDLING_I_KA_UTEN_TR,
    AVSLUTTET_I_TR_MED_OPPHEVET_OG_NY_BEHANDLING_I_KA,
    OPPRETTET,

    // TODO: not in use anymore. Can we update field in db?
    NY_ANKEBEHANDLING_I_KA,
}
