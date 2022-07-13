package no.nav.klage.oppgave.domain.events

import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeid

data class DokumentFerdigstiltAvSaksbehandler(
    val dokumentUnderArbeid: DokumentUnderArbeid
)