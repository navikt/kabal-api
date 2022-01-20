package no.nav.klage.oppgave.domain.kafka

import com.fasterxml.jackson.annotation.JsonFormat
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
 * KlageKvalitetStatistikk er en hendelse i en Sak, knyttet til en konkret behandlingstype (eks. søknad, revurdering, endring, klage).
 * Vi sender dette typisk ved mottak, tildeling og fullføring.
 */
@JsonSchemaTitle("SaksbehandlingKA")
data class KlageStatistikkTilDVH(

    /** Kan brukes til idempotency av konsumenter */
    @JsonSchemaDescription("Unik id for denne forsendelsen/eventen.")
    val eventId: UUID,

    @JsonSchemaDescription("Kode som angir hvilken enhet som er ansvarlig for behandlingen på det gjeldende tidspunktet. Dette begrepet har vi ikke helt i Kabal per nå.")
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
    @JsonSchemaDescription("Når behandlingen startet i KA")
    val behandlingStartetKA: LocalDate?,

    @JsonSchemaDescription("Kode som angir den aktuelle behandlingens tilstand på gjeldende tidspunkt.")
    val behandlingStatus: BehandlingState,

    @JsonSchemaDescription("Kode som beskriver behandlingen, for eksempel, klage, anke, tilbakekreving o.l.")
    val behandlingType: String,

    @JsonSchemaDescription("BrukerIDen til den ansvarlige beslutningstageren for saken. Medunderskriver.")
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

    @JsonSchemaDescription("F.eks. Foreldrepenger. Kodeverk.")
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

    @JsonSchemaDescription("Nøkkel til det aktuelle vedtaket da behandlingen blir tilknyttet et slikt. Vedtaket i Kabal.")
    val vedtakId: String,

    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = DATE_FORMAT
    )
    @JsonSchemaFormat(DATE_FORMAT)
    @JsonSchemaDescription("Dato for vedtaket i KA.")
    val vedtaksdato: LocalDate?,

    @JsonSchemaDescription("Angir på hvilken versjon av kildekoden JSON stringen er generert på bakgrunn av.")
    val versjon: Int = 1,

    //TODO Fyll ut når kabal-api får tilgang til det
    @JsonSchemaDescription("Stønaden eller ytelsen saken omhandler. Hva gjelder saken? Kodeverk fra DVH. TODO.")
    val ytelseType: String,

    @JsonSchemaDescription("Kvalitetsvurdering av arbeid gjort i 1. instans.")
    val kvalitetsvurdering: Kvalitetsvurdering? = null
) {
    data class Part(
        val verdi: String,
        val type: PartIdType
    )

    enum class PartIdType {
        PERSON, VIRKSOMHET
    }

    data class Kvalitetsvurdering(
        @JsonSchemaDescription("Er kvaliteten på oversendelsebrevet bra?")
        val kvalitetOversendelsesbrevBra: Boolean?,
        @JsonSchemaDescription("Mulige kvalitetsavvik i forsendelsesbrevet.")
        val kvalitetsavvikOversendelsesbrev: Set<KvalitetsavvikOversendelsesbrev>? = emptySet(),
        @JsonSchemaDescription("Er kvaliteten på utredning bra?")
        val kvalitetUtredningBra: Boolean?,
        @JsonSchemaDescription("Mulige kvalitetsavvik i utredningen.")
        val kvalitetsavvikUtredning: Set<KvalitetsavvikUtredning>? = emptySet(),
        @JsonSchemaDescription("Er kvaliteten på vedtaket bra?")
        val kvalitetVedtaketBra: Boolean?,
        @JsonSchemaDescription("Mulige kvalitetsavvik i vedtaket.")
        val kvalitetsavvikVedtak: Set<KvalitetsavvikVedtak>? = emptySet(),
        @JsonSchemaDescription("Har avviket stor konsekvens for bruker?")
        val avvikStorKonsekvens: Boolean?,
    ) {
        enum class KvalitetsavvikOversendelsesbrev(
            val id: Int,
            val beskrivelse: String
        ) {
            OVERSITTET_KLAGEFRIST_IKKE_KOMMENTERT(
                1,
                "Oversittet klagefrist er ikke kommentert"
            ),
            HOVEDINNHOLDET_IKKE_GJENGITT(
                2,
                "Hovedinnholdet i klagen er ikke gjengitt"
            ),
            MANGLER_BEGRUNNELSE(
                3,
                "Mangler begrunnelse for hvorfor vedtaket opprettholdes/hvorfor klager ikke oppfyller villkår"
            ),
            KLAGERS_ANFOERSLER_IKKE_TILSTREKKELIG_KOMMENTERT(
                4,
                "Klagers anførsler er ikke tilstrekkelig kommentert/imøtegått"
            ),
            MANGLER_KONKLUSJON(5, "Mangler konklusjon");
        }

        enum class KvalitetsavvikUtredning(
            val id: Int,
            val beskrivelse: String
        ) {
            MANGELFULL_UTREDNING_AV_MEDISINSKE_FORHOLD(
                1,
                "Mangelfull utredning av medisinske forhold "
            ),
            MANGELFULL_UTREDNING_AV_ARBEIDSFORHOLD(
                2,
                "Mangelfull utredning av arbeids- og inntektsforhold"
            ),
            MANGELFULL_UTREDNING_AV_UTENLANDSPROBLEMATIKK(
                3,
                "Mangelfull utredning av EØS/utlandsproblematikk"
            ),
            MANGELFULL_BRUK_AV_ROL(
                4,
                "Mangelfull bruk av rådgivende lege"
            ),
            MANGELFULL_UTREDNING_ANDRE_FORHOLD(
                5,
                "Mangelfull utredning av andre aktuelle forhold i saken"
            );
        }

        enum class KvalitetsavvikVedtak(
            val id: Int,
            val beskrivelse: String
        ) {
            IKKE_BRUKT_RIKTIGE_HJEMLER(
                1,
                "Det er ikke brukt riktig hjemmel/er"
            ),
            INNHOLDET_I_RETTSREGLENE_IKKE_TILSTREKKELIG_BESKREVET(
                2,
                "Innholdet i rettsreglene er ikke tilstrekkelig beskrevet"
            ),
            VURDERING_AV_BEVIS_ER_MANGELFULL(
                3,
                "Vurderingen av faktum/bevisvurderingen er mangelfull"
            ),
            BEGRUNNELSE_IKKE_TILSTREKKELIG_KONKRET_OG_INDVIDUELL(
                4,
                "Begrunnelsen er ikke tilstrekkelig konkret og individuell"
            ),
            FORMIDLING_IKKE_TYDELIG(5, "Formidlingen er ikke tydelig");
        }
    }
}

enum class BehandlingState {
    MOTTATT, TILDELT_SAKSBEHANDLER, AVSLUTTET, UKJENT
}
