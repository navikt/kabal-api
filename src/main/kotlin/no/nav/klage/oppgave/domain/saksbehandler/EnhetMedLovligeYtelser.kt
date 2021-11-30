package no.nav.klage.oppgave.domain.saksbehandler

import no.nav.klage.kodeverk.Ytelse

data class EnhetMedLovligeYtelser(val enhet: Enhet, val ytelser: List<Ytelse>)

data class Enhet(val enhetId: String, val navn: String)