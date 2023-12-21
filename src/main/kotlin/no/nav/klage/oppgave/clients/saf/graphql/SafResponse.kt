package no.nav.klage.oppgave.clients.saf.graphql

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDateTime

data class DokumentoversiktBrukerResponse(val data: DokumentoversiktBrukerDataWrapper?, val errors: List<Error>?)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Error(val message: String, val extensions: Extensions)

data class Extensions(val classification: String)

data class DokumentoversiktBrukerDataWrapper(val dokumentoversiktBruker: DokumentoversiktBruker)

data class DokumentoversiktBruker(val journalposter: List<Journalpost>, val sideInfo: SideInfo)

data class SideInfo(val sluttpeker: String?, val finnesNesteSide: Boolean, val antall: Int, val totaltAntall: Int)

data class JournalpostResponse(val data: JournalpostDataWrapper?, val errors: List<Error>?)

data class JournalpostDataWrapper(val journalpost: Journalpost?)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Journalpost(
    val journalpostId: String,
    val journalposttype: Journalposttype?,
    val journalstatus: Journalstatus?,
    val tema: Tema?,
    val sak: Sak?,
    val avsenderMottaker: AvsenderMottaker?,
    val opprettetAvNavn: String?,
    val skjerming: String?,
    val datoOpprettet: LocalDateTime,
    val dokumenter: List<DokumentInfo>?,
    val relevanteDatoer: List<RelevantDato>?,
    val kanal: String,
    val kanalnavn: String,
    val utsendingsinfo: Utsendingsinfo?,
)

data class AvsenderMottaker(
    val id: String?,
    val type: AvsenderMottakerIdType?,
    val navn: String?,
    val land: String?,
    val erLikBruker: Boolean,

) {
    enum class AvsenderMottakerIdType {
        //Why is NULL sent as a value?
        FNR, ORGNR, HPRNR, UTL_ORG, UKJENT, NULL
    }
}

data class Utsendingsinfo(
    val epostVarselSendt: EpostVarselSendt?,
    val smsVarselSendt: SmsVarselSendt?,
    val fysiskpostSendt: FysiskpostSendt?,
    val digitalpostSendt: DigitalpostSendt?,
) {
    data class EpostVarselSendt(
        val tittel: String,
        val adresse: String,
        val varslingstekst: String,
    )

    data class SmsVarselSendt(
        val adresse: String,
        val varslingstekst: String,
    )

    data class FysiskpostSendt(
        val adressetekstKonvolutt: String,
    )

    data class DigitalpostSendt(
        val adresse: String,
    )
}

data class RelevantDato(
    val dato: LocalDateTime,
    val datotype: Datotype,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DokumentInfo(
    val dokumentInfoId: String,
    val tittel: String?,
    val brevkode: String?,
    val skjerming: String?,
    val dokumentvarianter: List<Dokumentvariant>,
    val datoFerdigstilt: LocalDateTime?,
    val originalJournalpostId: String?,
)

data class Dokumentvariant(
    val variantformat: Variantformat,
    val filnavn: String?,
    val saksbehandlerHarTilgang: Boolean,
    val skjerming: SkjermingType?
)

enum class SkjermingType {
    POL, //Indikerer at det er fattet et vedtak etter personopplysningsloven (GDPR - brukers rett til å bli glemt).
    FEIL //Indikerer at det har blitt gjort en feil under mottak, journalføring eller brevproduksjon, slik at journalposten eller dokumentene er markert for sletting.
}

enum class Variantformat {
    //Den "offisielle" versjonen av et dokument, som er beregnet på visning og langtidsbevaring. I de fleste tilfeller er arkivvarianten lik dokumentet brukeren sendte inn eller mottok (digitalt eller på papir). Arkivvarianten er alltid i menneskelesbart format, som PDF, PDF/A eller PNG.
    //Alle dokumenter har en arkivvariant, med mindre bruker har fått innvilget vedtak om sletting eller skjerming av opplysninger i arkivet.
    ARKIV,

    //Dette er en sladdet variant av det opprinnelige dokumentet. Dersom det finnes en SLADDET variant, vil de fleste NAV-ansatte kun ha tilgang til denne varianten og ikke arkivvariant. Enkelte saksbehandlere vil imidlertid ha tilgang til både SLADDET og ARKIV.
    SLADDET,

    //Produksjonsvariant i eget proprietært format. Varianten finnes for dokumenter som er produsert i Metaforce eller Brevklient.
    PRODUKSJON,

    //Produksjonsvariant i eget proprietært format. Varianten finnes kun for dokumenter som er produsert i Exstream Live Editor.
    PRODUKSJON_DLF,

    //Variant av dokument som inneholder spørsmålstekster, hjelpetekster og ubesvarte spørsmål fra søknadsdialogen. Fullversjon genereres for enkelte søknadsskjema fra nav.no, og brukes ved klagebehandling.
    FULLVERSJON,

    //Variant av dokumentet i strukturert format, f.eks. XML eller JSON. Originalvarianten er beregnet på maskinell lesning og behandling.
    ORIGINAL
}

data class Sak(val datoOpprettet: LocalDateTime?, val fagsakId: String?, val fagsaksystem: String?)

enum class Tema {
    AAP, //Arbeidsavklaringspenger
    AAR, //Aa-registeret
    AGR, //Ajourhold - Grunnopplysninger
    ARP, //Arbeidsrådgivning - psykologtjenester
    ARS, //Arbeidsrådgivning - skjermet
    BAR, //Barnetrygd
    BID, //Bidrag
    BIL, //Bil
    DAG, //Dagpenger
    ENF, //Enslig forsørger
    ERS, //Erstatning
    EYB, //Barnepensjon
    EYO, //Omstillingsstønad
    FAR, //Farskap
    FEI, //Feilutbetaling
    FOR, //Foreldre- og svangerskapspenger
    FOS, //Forsikring
    FRI, //Kompensasjon for selvstendig næringsdrivende/frilansere
    FUL, //Fullmakt
    GEN, //Generell
    GRA, //Gravferdsstønad
    GRU, //Grunn- og hjelpestønad
    HEL, //Helsetjenester og ortopediske hjelpemidler
    HJE, //Hjelpemidler
    IAR, //Inkluderende arbeidsliv
    IND, //Tiltakspenger
    KLL, //Klage - lønnsgaranti
    KON, //Kontantstøtte
    KTA, //Kontroll - anmeldelse
    KTR, //Kontroll
    MED, //Medlemskap
    MOB, //Mobilitetsfremmende stønad
    OMS, //Omsorgspenger, pleiepenger og opplæringspenger
    OPA, //Oppfølging - Arbeidsgiver
    OPP, //Oppfølging
    PEN, //Pensjon
    PER, //Permittering og masseoppsigelser
    REH, //Rehabilitering
    REK, //Rekruttering og stilling
    RPO, //Retting av personopplysninger
    RVE, //Rettferdsvederlag
    SAA, //Sanksjon - Arbeidsgiver
    SAK, //Saksomkostninger
    SAP, //Sanksjon - Person
    SER, //Serviceklager
    SIK, //Sikkerhetstiltak
    STO, //Regnskap/utbetaling
    SUP, //	Supplerende stønad
    SYK, //Sykepenger
    SYM, //Sykmeldinger
    TIL, //Tiltak
    TRK, //Trekkhåndtering
    TRY, //Trygdeavgift
    TSO, //Tilleggsstønad
    TSR, //Tilleggsstønad arbeidssøkere
    UFM, //Unntak fra medlemskap
    UFO, //Uføretrygd
    UKJ, //Ukjent
    VEN, //Ventelønn
    YRA, //Yrkesrettet attføring
    YRK //Yrkesskade / Menerstatning
}

enum class Journalstatus {
    //Journalposten er mottatt, men ikke journalført. "Mottatt" er et annet ord for "arkivert" eller "midlertidig journalført"
    //Statusen vil kun forekomme for inngående dokumenter.
    MOTTATT,

    //Journalposten er ferdigstilt og ansvaret for videre behandling av forsendelsen er overført til fagsystemet. Journalen er i prinsippet låst for videre endringer.
    //Journalposter med status JOURNALFØRT oppfyller minimumskrav til metadata i arkivet, som for eksempel tema, sak, bruker og avsender.
    JOURNALFOERT,

    //Journalposten med tilhørende dokumenter er ferdigstilt, og journalen er i prinsippet låst for videre endringer. FERDIGSTILT tilsvarer statusen JOURNALFØRT for inngående dokumenter.
    //Tilsvarer begrepet Arkivert
    //Statusen kan forekomme for utgående dokumenter og notater.
    FERDIGSTILT,

    //Dokumentet er sendt til bruker. Statusen benyttes også når dokumentet er tilgjengeliggjort for bruker på DittNAV, og bruker er varslet.
    //Tilsvarer begrepet Sendt
    //Statusen kan forekomme for utgående dokumenter.
    EKSPEDERT,

    //Journalposten er opprettet i arkivet, men fremdeles under arbeid.
    //Statusen kan forekomme for utgående dokumenter og notater.
    UNDER_ARBEID,

    //Journalposten har blitt arkivavgrenset etter at den feilaktig har blitt knyttet til en sak.
    //Statusen kan forekomme for alle journalposttyper.
    FEILREGISTRERT,

    //Journalposten er arkivavgrenset grunnet en feilsituasjon, ofte knyttet til skanning eller journalføring.
    //Statusen vil kun forekomme for inngående dokumenter.
    UTGAAR,

    //Utgående dokumenter og notater kan avbrytes mens de er under arbeid, og ikke enda er ferdigstilt. Statusen AVBRUTT brukes stort sett ved feilsituasjoner knyttet til dokumentproduksjon.
    //Statusen kan forekomme for utgående dokumenter og notater.
    AVBRUTT,

    //Journalposten har ikke noen kjent bruker.
    //NB: UKJENT_BRUKER er ikke en midlertidig status, men benyttes der det ikke er mulig å journalføre fordi man ikke klarer å identifisere brukeren forsendelsen gjelder.
    //Statusen kan kun forekomme for inngående dokumenter.
    UKJENT_BRUKER,

    //Statusen benyttes bl.a. i forbindelse med brevproduksjon for å reservere 'plass' i journalen for dokumenter som skal populeres på et senere tidspunkt.
    //Dersom en journalpost blir stående i status RESEVERT over tid, tyder dette på at noe har gått feil under dokumentproduksjon eller ved skanning av et utgående dokument.
    //Statusen kan forekomme for utgående dokumenter og notater.
    RESERVERT,

    //Midlertidig status på vei mot MOTTATT.
    //Dersom en journalpost blir stående i status OPPLASTING_DOKUMENT over tid, tyder dette på at noe har gått feil under opplasting av vedlegg ved arkivering.
    //Statusen kan kun forekomme for inngående dokumenter.
    OPPLASTING_DOKUMENT,

    //Dersom statusfeltet i Joark er tomt, mappes dette til "UKJENT"
    UKJENT
}

enum class Journalposttype {
    I, //Inngående dokument: Dokumentasjon som NAV har mottatt fra en ekstern part. De fleste inngående dokumenter er søknader, ettersendelser av dokumentasjon til sak, eller innsendinger fra arbeidsgivere. Meldinger brukere har sendt til "Skriv til NAV" arkiveres også som inngående dokumenter.
    U, //Utgående dokument: Dokumentasjon som NAV har produsert og sendt ut til en ekstern part. De fleste utgående dokumenter er informasjons- eller vedtaksbrev til privatpersoner eller organisasjoner. "Skriv til NAV"-meldinger som saksbehandlere har sendt til brukere arkiveres også som utgående dokumenter.
    N //Notat: Dokumentasjon som NAV har produsert selv, uten at formålet er å distribuere dette ut av NAV. Eksempler på notater er samtalereferater med veileder på kontaktsenter og interne forvaltningsnotater.
}

enum class Datotype {
    DATO_SENDT_PRINT,
    DATO_EKSPEDERT,
    DATO_JOURNALFOERT,
    DATO_REGISTRERT,
    DATO_AVS_RETUR,
    DATO_DOKUMENT,
    DATO_LEST,
}
