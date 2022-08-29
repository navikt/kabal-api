package no.nav.klage.oppgave.domain.klage

import no.nav.klage.kodeverk.MedunderskriverFlyt
import no.nav.klage.kodeverk.Utfall
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.oppgave.domain.Behandling
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object BehandlingSetters {

    fun Behandling.setTildeling(
        nyVerdiSaksbehandlerident: String?,
        nyVerdiEnhet: String?,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdiSaksbehandlerident = tildeling?.saksbehandlerident
        val gammelVerdiEnhet = tildeling?.enhet
        val gammelVerdiTidspunkt = tildeling?.tidspunkt
        val tidspunkt = LocalDateTime.now()
        if (tildeling != null) {
            tildelingHistorikk.add(TildelingHistorikk(tildeling = tildeling!!.copy()))
        }
        tildeling = Tildeling(nyVerdiSaksbehandlerident, nyVerdiEnhet, tidspunkt)
        modified = tidspunkt

        val endringslogginnslag = mutableListOf<Endringslogginnslag>()

        endringslogg(
            saksbehandlerident,
            Felt.TILDELT,
            gammelVerdiTidspunkt?.format(DateTimeFormatter.ISO_LOCAL_DATE),
            tidspunkt.format(DateTimeFormatter.ISO_LOCAL_DATE),
            tidspunkt
        )?.let { endringslogginnslag.add(it) }

        endringslogg(
            saksbehandlerident,
            Felt.TILDELT_SAKSBEHANDLERIDENT,
            gammelVerdiSaksbehandlerident,
            nyVerdiSaksbehandlerident,
            tidspunkt
        )?.let { endringslogginnslag.add(it) }

        endringslogg(saksbehandlerident, Felt.TILDELT_ENHET, gammelVerdiEnhet, nyVerdiEnhet, tidspunkt)
            ?.let { endringslogginnslag.add(it) }

        return BehandlingEndretEvent(behandling = this, endringslogginnslag = endringslogginnslag)
    }

    fun Behandling.setMedunderskriverIdentAndMedunderskriverFlyt(
        nyVerdiMedunderskriverident: String?,
        nyVerdiMedunderskriverFlyt: MedunderskriverFlyt,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdiMedunderskriverident = currentDelbehandling().medunderskriver?.saksbehandlerident
        val gammelVerdiMedunderskriverFlyt = currentDelbehandling().medunderskriverFlyt
        val tidspunkt = LocalDateTime.now()
        if (currentDelbehandling().medunderskriver != null) {
            currentDelbehandling().medunderskriverHistorikk.add(MedunderskriverHistorikk(medunderskriver = currentDelbehandling().medunderskriver!!.copy()))
        }
        currentDelbehandling().medunderskriver = MedunderskriverTildeling(nyVerdiMedunderskriverident, tidspunkt)
        currentDelbehandling().medunderskriverFlyt = nyVerdiMedunderskriverFlyt
        modified = tidspunkt

        val endringslogginnslag = mutableListOf<Endringslogginnslag>()

        endringslogg(
            saksbehandlerident,
            Felt.MEDUNDERSKRIVERFLYT,
            gammelVerdiMedunderskriverFlyt.id,
            nyVerdiMedunderskriverFlyt.id,
            tidspunkt
        )?.let { endringslogginnslag.add(it) }

        endringslogg(
            saksbehandlerident,
            Felt.MEDUNDERSKRIVERIDENT,
            gammelVerdiMedunderskriverident,
            nyVerdiMedunderskriverident,
            tidspunkt
        )?.let { endringslogginnslag.add(it) }

        return BehandlingEndretEvent(behandling = this, endringslogginnslag = endringslogginnslag)
    }

    fun Behandling.setMedunderskriverFlyt(
        nyMedunderskriverFlyt: MedunderskriverFlyt,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdiMedunderskriverFlyt = currentDelbehandling().medunderskriverFlyt
        val tidspunkt = LocalDateTime.now()

        currentDelbehandling().medunderskriverFlyt = nyMedunderskriverFlyt
        modified = tidspunkt

        val endringslogginnslag = mutableListOf<Endringslogginnslag>()

        endringslogg(
            saksbehandlerident,
            Felt.MEDUNDERSKRIVERFLYT,
            gammelVerdiMedunderskriverFlyt.id,
            nyMedunderskriverFlyt.id,
            tidspunkt
        )?.let { endringslogginnslag.add(it) }

        return BehandlingEndretEvent(behandling = this, endringslogginnslag = endringslogginnslag)
    }

    fun Behandling.setSattPaaVent(
        nyVerdi: LocalDateTime?,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelSattPaaVent = sattPaaVent
        val tidspunkt = LocalDateTime.now()

        sattPaaVent = nyVerdi
        modified = tidspunkt

        val endringslogginnslag = mutableListOf<Endringslogginnslag>()

        endringslogg(
            saksbehandlerident,
            Felt.SATT_PAA_VENT,
            gammelSattPaaVent.toString(),
            nyVerdi.toString(),
            tidspunkt
        )?.let { endringslogginnslag.add(it) }

        return BehandlingEndretEvent(behandling = this, endringslogginnslag = endringslogginnslag)
    }

    fun Behandling.setMottattKlageinstans(
        nyVerdi: LocalDateTime,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdi = mottattKlageinstans
        val tidspunkt = LocalDateTime.now()
        mottattKlageinstans = nyVerdi
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident,
                Felt.DATO_MOTTATT_KLAGEINSTANS,
                gammelVerdi.toString(),
                nyVerdi.toString(),
                tidspunkt
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.setFrist(
        nyVerdi: LocalDate,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdi = frist
        val tidspunkt = LocalDateTime.now()
        frist = nyVerdi
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident,
                Felt.FRIST,
                gammelVerdi.toString(),
                nyVerdi.toString(),
                tidspunkt
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.setInnsendingshjemler(
        nyVerdi: Set<Hjemmel>,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdi = hjemler
        val tidspunkt = LocalDateTime.now()
        hjemler = nyVerdi
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident,
                Felt.HJEMMEL,
                gammelVerdi.toString(),
                nyVerdi.toString(),
                tidspunkt
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.setHjemlerInVedtak(
        nyVerdi: Set<Registreringshjemmel>,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdi = currentDelbehandling().hjemler
        val tidspunkt = LocalDateTime.now()
        currentDelbehandling().hjemler = nyVerdi.toMutableSet()
        currentDelbehandling().modified = tidspunkt
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident,
                Felt.HJEMLER_I_VEDTAK,
                gammelVerdi.joinToString { it.id },
                nyVerdi.joinToString { it.id },
                tidspunkt
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.setUtfallInVedtak(
        nyVerdi: Utfall?,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdi = currentDelbehandling().utfall
        val tidspunkt = LocalDateTime.now()
        currentDelbehandling().utfall = nyVerdi
        currentDelbehandling().modified = tidspunkt
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident,
                Felt.UTFALL,
                gammelVerdi.toString(),
                nyVerdi.toString(),
                tidspunkt
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.setAvsluttetAvSaksbehandler(
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdi = currentDelbehandling().avsluttetAvSaksbehandler
        val tidspunkt = LocalDateTime.now()
        currentDelbehandling().avsluttetAvSaksbehandler = tidspunkt
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident,
                Felt.AVSLUTTET_AV_SAKSBEHANDLER,
                gammelVerdi.toString(),
                tidspunkt.toString(),
                tidspunkt
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.setAvsluttet(
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdi = currentDelbehandling().avsluttet
        val tidspunkt = LocalDateTime.now()
        currentDelbehandling().avsluttet = tidspunkt
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident,
                Felt.AVSLUTTET,
                gammelVerdi.toString(),
                tidspunkt.toString(),
                tidspunkt
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.addSaksdokument(
        saksdokument: Saksdokument,
        saksbehandlerident: String
    ): BehandlingEndretEvent? {
        if (saksdokumenter.none { it.journalpostId == saksdokument.journalpostId && it.dokumentInfoId == saksdokument.dokumentInfoId }) {
            val tidspunkt = LocalDateTime.now()
            saksdokumenter.add(saksdokument)
            modified = tidspunkt
            val endringslogg = Endringslogginnslag.endringslogg(
                saksbehandlerident,
                Felt.SAKSDOKUMENT,
                null,
                saksdokument.dokumentInfoId,
                id,
                tidspunkt
            )
            return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
        }
        return null
    }

    fun Behandling.removeSaksdokument(
        saksdokument: Saksdokument,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val tidspunkt = LocalDateTime.now()
        saksdokumenter.removeIf { it.id == saksdokument.id }
        modified = tidspunkt
        val endringslogg = Endringslogginnslag.endringslogg(
            saksbehandlerident,
            Felt.SAKSDOKUMENT,
            saksdokument.dokumentInfoId,
            null,
            id,
            tidspunkt
        )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    private fun Behandling.endringslogg(
        saksbehandlerident: String,
        felt: Felt,
        fraVerdi: String?,
        tilVerdi: String?,
        tidspunkt: LocalDateTime
    ): Endringslogginnslag? {
        return Endringslogginnslag.endringslogg(
            saksbehandlerident,
            felt,
            fraVerdi,
            tilVerdi,
            this.id,
            tidspunkt
        )
    }

}