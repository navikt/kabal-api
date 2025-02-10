package no.nav.klage.oppgave.domain.klage

import no.nav.klage.oppgave.domain.klage.Endringslogginnslag.Companion.endringslogg
import java.time.LocalDateTime


fun BehandlingWithVarsletBehandlingstid.setVarsletBehandlingstid(
    varsletBehandlingstid: VarsletBehandlingstid,
    saksbehandlerident: String,
    saksbehandlernavn: String,
    mottakere: List<Mottaker>,
): List<Endringslogginnslag> {
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

    return endringslogginnslag
}

private fun BehandlingWithVarsletBehandlingstid.recordVarsletBehandlingstidHistory(
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
