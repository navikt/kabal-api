package no.nav.klage.oppgave.domain.kafka

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * Brukes av DVH, sendes på topic kabal-statistikk.v2.
 *
 * Hver behandling i Kabal får hendelsene OPPRETTET, TILDELT_SAKSBEHANDLER (ved hver tildeling og fradeling)
 * og AVSLUTTET. En sak som går videre (f.eks. anke til Trygderetten) blir en ny behandling med egen behandlingIdKabal.
 *
 * Feltene har samme betydning som i StatistikkTilDVH (v1), med unntak av behandlingStatus, behandlingType,
 * ansvarligEnhetKode og behandlingStartetKA.
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
data class StatistikkTilDVHV2(
    /** Unik id for denne forsendelsen/eventen. Kan brukes til idempotency av konsumenter. */
    val eventId: UUID,
    /**
     * Kode som angir hvilken enhet som er ansvarlig for behandlingen på det gjeldende tidspunktet.
     * Saksbehandlers enhet når behandlingen er tildelt, TR0000 når Trygderetten har avsluttet, ellers null.
     */
    val ansvarligEnhetKode: String?,
    /** Kode som angir hvilken type enhetskode det er snakk om, som oftest NORG. */
    val ansvarligEnhetType: String = "NORG",
    /** Feltet angir hvem som er avsender av dataene (navnet på systemet). */
    val avsender: String = "Kabal",
    /** Nøkkel til den aktuelle behandling, som kan identifisere den i kildesystemet. Typisk førsteinstans. */
    val behandlingId: String?,
    /** Nøkkel til den aktuelle behandling, som kan identifisere den i Kabal. */
    val behandlingIdKabal: String,
    /** Dato for første tildeling til saksbehandler i KA. Endres ikke ved fradeling eller ny tildeling. Null ved OPPRETTET. */
    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = DATE_FORMAT,
    )
    val behandlingStartetKA: LocalDate?,
    /** Kode som angir den aktuelle behandlingens tilstand på gjeldende tidspunkt. */
    val behandlingStatus: BehandlingStateV2,
    /** Kode som beskriver behandlingen. Samme verdier som Type i Kabal sitt kodeverk. */
    val behandlingType: BehandlingTypeV2,
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
    /** Angir på hvilken versjon av kildekoden JSON stringen er generert på bakgrunn av. */
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

enum class BehandlingStateV2 {
    OPPRETTET,
    TILDELT_SAKSBEHANDLER,
    AVSLUTTET,
}

/** Samme verdier som Type i Kabal sitt kodeverk. */
enum class BehandlingTypeV2 {
    KLAGE,
    ANKE_FOER_2027,
    ANKE_ETTER_2027,
    ANKE_I_TRYGDERETTEN_FOER_2027,
    ANKE_I_TRYGDERETTEN_ETTER_2027,
    BEGJAERING_OM_GJENOPPTAK_FOER_2027,
    BEGJAERING_OM_GJENOPPTAK_ETTER_2027,
    BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN_FOER_2027,
    BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN_ETTER_2027,
    BEHANDLING_ETTER_TRYGDERETTEN_OPPHEVET,
    OMGJOERINGSKRAV,
}
