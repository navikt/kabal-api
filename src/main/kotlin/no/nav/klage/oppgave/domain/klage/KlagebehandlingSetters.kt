package no.nav.klage.oppgave.domain.klage

import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.domain.klage.Endringslogginnslag.Companion.endringslogg
import java.time.LocalDate
import java.time.LocalDateTime

object KlagebehandlingSetters {

    fun Klagebehandling.setMottattVedtaksinstans(
        nyVerdi: LocalDate,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdi = mottattVedtaksinstans
        val tidspunkt = LocalDateTime.now()
        mottattVedtaksinstans = nyVerdi
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.MOTTATT_FOERSTEINSTANS_DATO,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = nyVerdi.toString(),
                behandlingId = this.id,
                tidspunkt = tidspunkt,
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Klagebehandling.setVarsletBehandlingstid(
        varsletBehandlingstid: VarsletBehandlingstid,
        saksbehandlerident: String,
        saksbehandlernavn: String,
        mottakere: List<Mottaker>,
    ): BehandlingEndretEvent {
        val gammelVerdi = this.varsletBehandlingstid

        val tidspunkt = LocalDateTime.now()

        if (varsletBehandlingstidHistorikk.isEmpty()) {
            recordVarsletBehandlingstidHistory(
                tidspunkt = created,
                utfoerendeIdent = null,
                utfoerendeNavn = null,
                mottakere = listOf(),
            )
        }
        this.varsletBehandlingstid = varsletBehandlingstid

        recordVarsletBehandlingstidHistory(
            tidspunkt = tidspunkt,
            utfoerendeIdent = saksbehandlerident,
            utfoerendeNavn = saksbehandlernavn,
            mottakere = mottakere,
        )

        val endringslogginnslag = mutableListOf<Endringslogginnslag>()

        endringslogg(
            saksbehandlerident = saksbehandlerident,
            felt = Felt.VARSLET_FRIST,
            fraVerdi = gammelVerdi.toString(),
            tilVerdi = varsletBehandlingstid.toString(),
            behandlingId = this.id,
            tidspunkt = tidspunkt
        )?.let { endringslogginnslag.add(it) }

        return BehandlingEndretEvent(behandling = this, endringslogginnslag = endringslogginnslag)
    }

    private fun Klagebehandling.recordVarsletBehandlingstidHistory(
        tidspunkt: LocalDateTime,
        utfoerendeIdent: String?,
        utfoerendeNavn: String?,
        mottakere: List<Mottaker>,
    ) {
        varsletBehandlingstidHistorikk.add(
            VarsletBehandlingstidHistorikk(
                mottakerList = mottakere.map { it.toVarsletBehandlingstidHistorikkMottaker() },
                tidspunkt = tidspunkt,
                utfoerendeIdent = utfoerendeIdent,
                utfoerendeNavn = utfoerendeNavn,
                varsletBehandlingstid = varsletBehandlingstid,
            )
        )
    }
}