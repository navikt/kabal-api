package no.nav.klage.oppgave.clients.kabalinnstillinger.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class Medunderskrivere(val medunderskrivere: List<Saksbehandler>)

data class MedunderskrivereInput(
    val enhet: String,
    val navIdent: String,
    val sak: SakInput,
)

data class SakInput(
    val fnr: String,
    val sakId: String,
    val ytelseId: String,
    val fagsystemId: String,
)

data class Saksbehandlere(val saksbehandlere: List<Saksbehandler>)

data class Saksbehandler(val navIdent: String, val navn: String)

data class SaksbehandlerSearchInput(
    val ytelseId: String,
    val fnr: String,
    val sakId: String,
    val fagsystemId: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SaksbehandlerAccess(
    val saksbehandlerIdent: String,
    val saksbehandlerName: String,
    val ytelseIdList: List<String>,
    val created: LocalDateTime?,
    val accessRightsModified: LocalDateTime?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TildelteYtelserResponse(
    val ytelseIdList: List<String>
)