package no.nav.klage.oppgave.domain.saksbehandler

import no.nav.klage.kodeverk.Tema

data class EnhetMedLovligeTemaer(val enhetId: String, val navn: String, val temaer: List<Tema>)