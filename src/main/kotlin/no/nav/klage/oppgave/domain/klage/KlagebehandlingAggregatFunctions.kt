package no.nav.klage.oppgave.domain.klage

import no.nav.klage.kodeverk.MedunderskriverFlyt
import no.nav.klage.kodeverk.Utfall
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.oppgave.domain.events.KlagebehandlingEndretEvent
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

object KlagebehandlingAggregatFunctions {

    fun Klagebehandling.setTildeling(
        nyVerdiSaksbehandlerident: String?,
        nyVerdiEnhet: String?,
        saksbehandlerident: String
    ): KlagebehandlingEndretEvent {
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

        return KlagebehandlingEndretEvent(klagebehandling = this, endringslogginnslag = endringslogginnslag)
    }

    fun Klagebehandling.setMedunderskriverIdentAndMedunderskriverFlyt(
        nyVerdiMedunderskriverident: String?,
        nyVerdiMedunderskriverFlyt: MedunderskriverFlyt,
        saksbehandlerident: String
    ): KlagebehandlingEndretEvent {
        val gammelVerdiMedunderskriverident = medunderskriver?.saksbehandlerident
        val gammelVerdiMedunderskriverFlyt = medunderskriverFlyt
        val tidspunkt = LocalDateTime.now()
        if (medunderskriver != null) {
            medunderskriverHistorikk.add(MedunderskriverHistorikk(medunderskriver = medunderskriver!!.copy()))
        }
        medunderskriver = MedunderskriverTildeling(nyVerdiMedunderskriverident, tidspunkt)
        medunderskriverFlyt = nyVerdiMedunderskriverFlyt
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

        return KlagebehandlingEndretEvent(klagebehandling = this, endringslogginnslag = endringslogginnslag)
    }

    fun Klagebehandling.setMedunderskriverFlyt(
        nyMedunderskriverFlyt: MedunderskriverFlyt,
        saksbehandlerident: String
    ): KlagebehandlingEndretEvent {
        val gammelVerdiMedunderskriverFlyt = medunderskriverFlyt
        val tidspunkt = LocalDateTime.now()

        medunderskriverFlyt = nyMedunderskriverFlyt
        modified = tidspunkt

        val endringslogginnslag = mutableListOf<Endringslogginnslag>()

        endringslogg(
            saksbehandlerident,
            Felt.MEDUNDERSKRIVERFLYT,
            gammelVerdiMedunderskriverFlyt.id,
            nyMedunderskriverFlyt.id,
            tidspunkt
        )?.let { endringslogginnslag.add(it) }

        return KlagebehandlingEndretEvent(klagebehandling = this, endringslogginnslag = endringslogginnslag)
    }

    fun Klagebehandling.setHjemlerInVedtak(
        nyVerdi: Set<Hjemmel>,
        saksbehandlerident: String
    ): KlagebehandlingEndretEvent {
        val gammelVerdi = vedtak.hjemler
        val tidspunkt = LocalDateTime.now()
        vedtak.hjemler = nyVerdi.toMutableSet()
        vedtak.modified = tidspunkt
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident,
                Felt.HJEMLER_I_VEDTAK,
                gammelVerdi.joinToString { it.id },
                nyVerdi.joinToString { it.id },
                tidspunkt
            )
        return KlagebehandlingEndretEvent(klagebehandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Klagebehandling.setUtfallInVedtak(
        nyVerdi: Utfall?,
        saksbehandlerident: String
    ): KlagebehandlingEndretEvent {
        val gammelVerdi = vedtak.utfall
        val tidspunkt = LocalDateTime.now()
        vedtak.utfall = nyVerdi
        vedtak.modified = tidspunkt
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident,
                Felt.UTFALL,
                gammelVerdi.toString(),
                nyVerdi.toString(),
                tidspunkt
            )
        return KlagebehandlingEndretEvent(klagebehandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Klagebehandling.setDokumentEnhetIdInVedtak(
        nyVerdi: UUID?,
        saksbehandlerident: String
    ): KlagebehandlingEndretEvent {
        val gammelVerdi = vedtak.dokumentEnhetId
        val tidspunkt = LocalDateTime.now()
        vedtak.dokumentEnhetId = nyVerdi
        vedtak.modified = tidspunkt
        modified = tidspunkt
        val endringslogg = listOfNotNull(
            endringslogg(
                saksbehandlerident,
                Felt.DOKUMENT_ENHET_ID_I_VEDTAK,
                gammelVerdi.toString(),
                nyVerdi.toString(),
                tidspunkt
            )
        )
        return KlagebehandlingEndretEvent(klagebehandling = this, endringslogginnslag = endringslogg)
    }

    fun Klagebehandling.setAvsluttetAvSaksbehandler(
        saksbehandlerident: String
    ): KlagebehandlingEndretEvent {
        val gammelVerdi = avsluttetAvSaksbehandler
        val tidspunkt = LocalDateTime.now()
        avsluttetAvSaksbehandler = tidspunkt
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident,
                Felt.AVSLUTTET_AV_SAKSBEHANDLER,
                gammelVerdi.toString(),
                tidspunkt.toString(),
                tidspunkt
            )
        return KlagebehandlingEndretEvent(klagebehandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Klagebehandling.setAvsluttet(
        saksbehandlerident: String
    ): KlagebehandlingEndretEvent {
        val gammelVerdi = avsluttet
        val tidspunkt = LocalDateTime.now()
        avsluttet = tidspunkt
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident,
                Felt.AVSLUTTET,
                gammelVerdi.toString(),
                tidspunkt.toString(),
                tidspunkt
            )
        return KlagebehandlingEndretEvent(klagebehandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Klagebehandling.addSaksdokument(
        saksdokument: Saksdokument,
        saksbehandlerident: String
    ): KlagebehandlingEndretEvent? {
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
            return KlagebehandlingEndretEvent(klagebehandling = this, endringslogginnslag = listOfNotNull(endringslogg))
        }
        return null
    }

    fun Klagebehandling.removeSaksdokument(
        saksdokument: Saksdokument,
        saksbehandlerident: String
    ): KlagebehandlingEndretEvent {
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
        return KlagebehandlingEndretEvent(klagebehandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Klagebehandling.setSmartEditorIdInVedtak(
        nyVerdi: String?,
        saksbehandlerident: String
    ): KlagebehandlingEndretEvent {
        val gammelVerdi = vedtak.smartEditorId
        val tidspunkt = LocalDateTime.now()
        vedtak.smartEditorId = nyVerdi
        vedtak.modified = tidspunkt
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident,
                Felt.SMART_EDITOR_ID,
                gammelVerdi,
                nyVerdi,
                tidspunkt
            )
        return KlagebehandlingEndretEvent(klagebehandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    private fun Klagebehandling.endringslogg(
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