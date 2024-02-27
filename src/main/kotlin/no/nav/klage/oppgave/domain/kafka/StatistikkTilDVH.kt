package no.nav.klage.oppgave.domain.kafka

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaDescription
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaFormat
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaTitle
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

const val DATE_TIME_FORMAT_LABEL = "date-time"
const val DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS"
const val DATE_FORMAT_LABEL = "date"
const val DATE_FORMAT = "yyyy-MM-dd"

/**
 * Brukes av DVH
 * StatistikkTilDVH er en hendelse i en Sak, knyttet til en konkret behandlingstype (eks. søknad, revurdering, endring, klage).
 * Vi sender dette typisk ved mottak, tildeling og fullføring.
 *
 * Navnendringer her krever at vi sender all data til DVH på nytt med nye navn. Derfor er vi forsiktige med å gjøre slik endringer
 * da dette blir litt dyrt. Hvis et felt endrer betydning så er det viktig å dokumentere.
 */
@JsonSchemaTitle("SaksbehandlingKA")
@JsonIgnoreProperties(ignoreUnknown = true)
data class StatistikkTilDVH(

    /** Kan brukes til idempotency av konsumenter */
    @JsonSchemaDescription("Unik id for denne forsendelsen/eventen.")
    val eventId: UUID,

    @JsonSchemaDescription("Kode som angir hvilken enhet som er ansvarlig for behandlingen på det gjeldende tidspunktet. Dette vet vi ikke alltid.")
    val ansvarligEnhetKode: String? = null,

    @JsonSchemaDescription("Kode som angir hvilken type enhetskode det er snakk om, som oftest NORG.")
    val ansvarligEnhetType: String = "NORG",

    @JsonSchemaDescription("Feltet angir hvem som er avsender av dataene, (navnet på systemet).")
    val avsender: String = "Kabal",

    @JsonSchemaDescription("Nøkkel til den aktuelle behandling, som kan identifisere den i kildesystemet. Typisk førsteinstans.")
    val behandlingId: String?,

    @JsonSchemaDescription("Nøkkel til den aktuelle behandling, som kan identifisere den i Kabal.")
    val behandlingIdKabal: String,

    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = DATE_FORMAT
    )
    @JsonSchemaFormat(DATE_FORMAT_LABEL)
    @JsonSchemaDescription("Når enhet blir satt i KA")
    val behandlingStartetKA: LocalDate?,

    @JsonSchemaDescription("Kode som angir den aktuelle behandlingens tilstand på gjeldende tidspunkt.")
    val behandlingStatus: BehandlingState,

    @JsonSchemaDescription("Kode som beskriver behandlingen, for eksempel, klage, anke, tilbakekreving o.l.")
    val behandlingType: String,

    @JsonSchemaDescription("BrukerIDen til ev. medunderskriver.")
    val beslutter: String?,

    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = DATE_TIME_FORMAT
    )
    @JsonSchemaFormat(DATE_TIME_FORMAT_LABEL)
    @JsonSchemaDescription("Tidspunktet da hendelsen faktisk ble gjennomført eller registrert i systemet. (format:$DATE_TIME_FORMAT) Dette er det tidspunkt der hendelsen faktisk er gjeldende fra. Ved for eksempel patching av data eller oppdatering tilbake i tid, skal tekniskTid være lik endringstidspunktet, mens endringstid angir tidspunktet da endringen offisielt gjelder fra.")
    val endringstid: LocalDateTime,

    @JsonSchemaDescription("Liste av hjemler.")
    val hjemmel: List<String>,

    @JsonSchemaDescription("Den som sendt inn klagen.")
    val klager: Part,

    @JsonSchemaDescription("Vedtaksinstans. F.eks. Foreldrepenger. Kodeverk.")
    val opprinneligFagsaksystem: String,

    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = DATE_FORMAT
    )
    @JsonSchemaFormat(DATE_FORMAT_LABEL)
    @JsonSchemaDescription("Når KA mottok oversendelsen.")
    val overfoertKA: LocalDate,

    @JsonSchemaDescription("Utfall.")
    val resultat: String?,

    //Hvis EØS kommer tilbake så legg til dette.
//    /**
//    "maxLength": 100 ,
//    "description": "Kode som beskriver behandlingens utlandstilsnitt i henhold til NAV spesialisering. I hoved sak vil denne koden beskrive om saksbehandlingsfrister er i henhold til utlandssaker eller innlandssaker, men vil for mange kildesystem være angitt med en høyere oppløsning."
//     */
//    val utenlandstilsnitt: String,

    @JsonSchemaDescription("Den som har rettigheten.")
    val sakenGjelder: Part,

    @JsonSchemaDescription("Bruker IDen til saksbehandler ansvarlig for saken på gjeldende tidspunkt. Kan etterlates tom ved helautomatiske delprosesser i behandlingen. Bør bare fylles når det er manuelle skritt i saksbehandlingen som utføres.")
    val saksbehandler: String?,

    @JsonSchemaDescription("Enhet til gjeldende saksbehandler.")
    val saksbehandlerEnhet: String?,

    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = DATE_TIME_FORMAT
    )
    @JsonSchemaFormat(DATE_TIME_FORMAT_LABEL)
    @JsonSchemaDescription("Tidspunktet da systemet ble klar over hendelsen. (format:$DATE_TIME_FORMAT). Dette er tidspunkt hendelsen ble endret i systemet. Sammen med funksjonellTid/endringstid, vil vi kunne holde rede på hva som er blitt rapportert tidligere og når det skjer endringer tilbake i tid.")
    val tekniskTid: LocalDateTime,

    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = DATE_FORMAT
    )
    @JsonSchemaFormat(DATE_FORMAT)
    @JsonSchemaDescription("Dato for vedtaket i KA.")
    val vedtaksdato: LocalDate?,

    @JsonSchemaDescription("Angir på hvilken versjon av kildekoden JSON stringen er generert på bakgrunn av.")
    //TODO find version?
    val versjon: Int = 1,

    @JsonSchemaDescription("Navnet på ytelsen i Kabal sitt kodeverk")
    val ytelseType: String,

    ) {
    data class Part(
        val verdi: String,
        val type: PartIdType
    )

    enum class PartIdType {
        PERSON, VIRKSOMHET
    }
}

enum class BehandlingState {
    MOTTATT, TILDELT_SAKSBEHANDLER, AVSLUTTET, UKJENT, SENDT_TIL_TR, NY_ANKEBEHANDLING_I_KA
}
