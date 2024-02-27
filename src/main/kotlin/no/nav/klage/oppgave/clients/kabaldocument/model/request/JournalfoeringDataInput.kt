package no.nav.klage.oppgave.clients.kabaldocument.model.request

import java.time.LocalDate

data class JournalfoeringDataInput(
    val sakenGjelder: PartIdInput,
    val temaId: String,
    val sakFagsakId: String,
    val sakFagsystemId: String,
    val kildeReferanse: String,
    val enhet: String,
    val behandlingstema: String,
    val tittel: String,
    val brevKode: String,
    val tilleggsopplysning: TilleggsopplysningInput?,
    val inngaaendeKanal: Kanal?,
    val datoMottatt: LocalDate?,
)

enum class Kanal {
    ALTINN,
    ALTINN_INNBOKS,
    E_POST,
    EIA,
    NAV_NO,
    NAV_NO_UINNLOGGET,
    NAV_NO_CHAT,
    SKAN_NETS,
    SKAN_PEN,
    SKAN_IM,
    INNSENDT_NAV_ANSATT,
    EESSI,
    EKST_OPPS,
    S,
    L,
    SDP,
    TRYGDERETTEN,
    HELSENETTET,
    INGEN_DISTRIBUSJON,
    DPV,
    DPVS,
    DPVT,
    UKJENT,
}
