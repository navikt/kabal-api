package no.nav.klage.oppgave.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OverfoeringsdataParserServiceTest {

    private val service = OverfoeringsdataParserService()

    private val beskrivelseMedKunOverfoeringer =
        """
            --- 06.05.2019 13:06 Duck, Donald (D123456, 4474) ---
            Klage jf. Ftrl. § 21-1 og Fvl. § 28 og § 2 oversendes KA
        
            Oppgaven er flyttet fra enhet 4474 til 4203, fra saksbehandler D123456 til <ingen>, fra mappe <ingen> til <ingen>
        
        
        
            --- 09.10.2020 16:25 Duck, Dolly (D112233, 4416) ---
            Klageinnstilling sendt klager. Sak (hel-elektronisk) overført Klageinstans Oslo for videre behandling av klagen.
            Oppgaven er flyttet fra enhet 4416 til 4291, fra saksbehandler D112233 til <ingen>, fra mappe <ingen> til <ingen>
        
        
        
            --- 20.10.2020 13:34 Duck, Guffen (D998877, 4418) ---
            Innstilling er skrevet i dag, tilgjengelig i Gosys i morgen
            Oppgaven er flyttet fra enhet 4418 til 4291, fra saksbehandler D998877 til <ingen>, fra mappe <ingen> til <ingen>
        
        
        
            --- 09.03.2020 10:40 McDuck, Skrue (M887766, 4416) ---
            Klageinnstilling sendt klager. Sak (hel-elektronisk) overført Klageinstans Oslo for videre behandling av klagen.
        
            Oppgaven er flyttet fra enhet 4416 til 4291, fra saksbehandler M887766 til <ingen>, fra mappe <ingen> til <ingen>
        """.trimIndent()

    private val beskrivelseMedLittAvHvert =
        """
            Beskrivelsehistorikk
            --- 18.09.2020 14:44 Nordmann, Ole Dole (N112233, 4416) ---
            ok
             
            --- 18.09.2020 14:44 Nordmann, Ole Dole (N112233, 4416) ---
            Oppgaven er flyttet  fra saksbehandler <ingen> til N112233
             
            --- 07.09.2020 17:06 Saksbehandler, Ine Kristine (S445566, 4291) ---
            Sak ferdig behandla KA. Stadfesta.
            Oppgaven er flyttet fra enhet 4291 til 4416, fra saksbehandler S445566 til <ingen>, fra mappe <ingen> til <ingen>
             
            --- 11.08.2020 11:27 Saksbehandler, Ine Kristine (S445566, 4291) ---
            UB § 8-13
            Oppgaven er flyttet  fra saksbehandler N987654 til S445566
             
            --- 10.03.2020 11:29 Nordmann, Kari (N987654, 4291) ---
            §8-13
            Oppgaven er flyttet , fra saksbehandler <ingen> til N987654, fra mappe <ingen> til Sykepenger klager
             
            --- 09.03.2020 10:40 Førsteinstansansatt, Kari (F123456, 4416) ---
            Klageinnstilling sendt klager. Sak (hel-elektronisk) overført Klageinstans Oslo for videre behandling av klagen.
             
            Oppgaven er flyttet fra enhet 4416 til 4291, fra saksbehandler F123456 til <ingen>, fra mappe <ingen> til <ingen>
             
            --- 07.03.2020 17:09 Førsteinstansansatt, Kari (F123456, 4416) ---
             
            Oppgaven er flyttet  fra saksbehandler <ingen> til F123456
             
            --- 10.02.2020 10:17 Gås, Gunnar (G887766, 4416) ---
             
            Oppgaven er flyttet   fra mappe <ingen> til 30 Klager- Klar til behandling
             
            --- 10.02.2020 10:17 Gås, Gunnar (G887766, 4416) ---
             
            Oppgaven er flyttet   fra mappe 30 Klager- Klar til behandling til <ingen>
            Oppgaven har byttet oppgavetype fra Vurder henvendelse til Behandle sak (Manuell)
             
            --- 07.02.2020 12:41 Høne, Mor (H998877, 4416) ---
            Ikke omgjøring.
            Oppgaven er flyttet , fra saksbehandler H101010 til <ingen>, fra mappe <ingen> til 30 Klager- Klar til behandling
             
            --- 07.02.2020 10:37 Hane, Far (H101010, 4416) ---
            Klage registrert i modia 030220.
             
            Svartidsbrev sendt.
        """.trimIndent()

    private val beskrivelseUtenOverfoeringsdata1 =
        """
            Beskrivelsehistorikk
            --- 26.11.2020 12:32 Duck, Dolly (D112233, 4291) ---
            §§ 21-3, 21-7 og 8-3
            Oppgaven er flyttet  fra saksbehandler D112233 til <ingen>
             
            --- 18.11.2020 08:36 Duck, Dolly (D112233, 4291) ---
            §§ 21-3, 21-7 og 8-3.
             
            Oppgaven er flyttet , fra saksbehandler <ingen> til D112233, fra mappe <ingen> til Sykepenger klager
             
            --- 07.11.2020 12:24 Duck, Donald (D667788, 4403) ---
            §§ 21-3, 21-7 og 8-3.
             
            Sak oversendt KA.
             
            Oversendelsesbrevet vil være tilgjengelig i Gosys fra 101120.
             
            Klage
             
            Reg.dato: 23.08.2020
        """.trimIndent()

    private val beskrivelseOpprettetIKA =
        """
            Beskrivelsehistorikk
            --- 11.01.2021 20:49 Nordmann, Kari (K787878, 4291) ---
            § 8-20
        """.trimIndent()

    @Test
    fun `beskrivelse med kun overfoeringer parsed correctly`() {

        val (saksbehandlerWhoMadeTheChange, enhetOfsaksbehandlerWhoMadeTheChange, datoForOverfoering, enhetOverfoertFra, enhetOverfoertTil) = service.parseBeskrivelse(
            beskrivelseMedKunOverfoeringer
        )!!

        assertThat(saksbehandlerWhoMadeTheChange).isEqualTo("D123456")
        assertThat(enhetOfsaksbehandlerWhoMadeTheChange).isEqualTo("4474")
        assertThat(datoForOverfoering).isEqualTo("2019-05-06")
        assertThat(enhetOverfoertFra).isEqualTo("4474")
        assertThat(enhetOverfoertTil).isEqualTo("4203")

    }

    @Test
    fun `beskrivelse med litt av hvert parsed correctly`() {

        val (saksbehandlerWhoMadeTheChange, enhetOfsaksbehandlerWhoMadeTheChange, datoForOverfoering, enhetOverfoertFra, enhetOverfoertTil) = service.parseBeskrivelse(
            beskrivelseMedLittAvHvert
        )!!

        assertThat(saksbehandlerWhoMadeTheChange).isEqualTo("F123456")
        assertThat(enhetOfsaksbehandlerWhoMadeTheChange).isEqualTo("4416")
        assertThat(datoForOverfoering).isEqualTo("2020-03-09")
        assertThat(enhetOverfoertFra).isEqualTo("4416")
        assertThat(enhetOverfoertTil).isEqualTo("4291")

    }

    @Test
    fun `beskrivelse uten autogenerert tekst parsed correctly`() {

        val (saksbehandlerWhoMadeTheChange, enhetOfsaksbehandlerWhoMadeTheChange, datoForOverfoering, enhetOverfoertFra, enhetOverfoertTil) = service.parseBeskrivelse(
            beskrivelseUtenOverfoeringsdata1
        )!!

        assertThat(saksbehandlerWhoMadeTheChange).isEqualTo("D667788")
        assertThat(enhetOfsaksbehandlerWhoMadeTheChange).isEqualTo("4403")
        assertThat(datoForOverfoering).isEqualTo("2020-11-07")
        assertThat(enhetOverfoertFra).isEqualTo("4403")
        assertThat(enhetOverfoertTil).isEqualTo("4291")

    }

    @Test
    fun `beskrivelse opprettet i KA returns null`() {

        val overfoeringsdata = service.parseBeskrivelse(
            beskrivelseOpprettetIKA
        )

        assertThat(overfoeringsdata).isNull()

    }
}