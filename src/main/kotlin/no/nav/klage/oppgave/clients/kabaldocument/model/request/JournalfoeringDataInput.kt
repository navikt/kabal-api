package no.nav.klage.oppgave.clients.kabaldocument.model.request

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
)

enum class Kanal {
    ALTINN,
    ALTINN_INNBOKS,
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
    SENTRAL_UTSKRIFT,
    LOKAL_UTSKRIFT,
    SDP,
    TRYGDERETTEN,
    HELSENETTET,
    INGEN_DISTRIBUSJON,
    DPV,
    DPVS,
    UKJENT,
}
