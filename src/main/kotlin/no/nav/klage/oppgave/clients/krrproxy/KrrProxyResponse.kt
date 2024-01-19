package no.nav.klage.oppgave.clients.krrproxy

import java.time.LocalDate
import java.time.ZonedDateTime


data class DigitalKontaktinformasjon (
    val personident: String? = null,
    val aktiv: Boolean? = null,
    val kanVarsles: Boolean? = null,
    val reservasjonOppdatert: ZonedDateTime? = null,
    val reservert: Boolean? = null,
    val spraak: String? = null,
    val spraakOppdatert: ZonedDateTime? = null,
    val epostadresse: String? = null,
    val epostadresseOppdatert: ZonedDateTime? = null,
    val epostadresseVerifisert: ZonedDateTime? = null,
    val mobiltelefonnummer: String? = null,
    val mobiltelefonnummerOppdatert: ZonedDateTime? = null,
    val mobiltelefonnummerVerifisert: ZonedDateTime? = null,
    val sikkerDigitalPostkasse: SikkerDigitalPostkasse? = null,
)

data class SikkerDigitalPostkasse (
    val adresse: String? = null,
    val leverandoerAdresse: String? = null,
    val leverandoerSertifikat: String? = null
)