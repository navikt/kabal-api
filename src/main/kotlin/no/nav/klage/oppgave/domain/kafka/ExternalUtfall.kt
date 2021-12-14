package no.nav.klage.oppgave.domain.kafka

enum class ExternalUtfall(val navn: String) {
    TRUKKET("Trukket"),
    RETUR("Retur"),
    OPPHEVET("Opphevet"),
    MEDHOLD("Medhold"),
    DELVIS_MEDHOLD("Delvis medhold"),
    STADFESTELSE("Stadfestelse"),
    UGUNST("Ugunst (Ugyldig)"),
    AVVIST("Avvist");
}