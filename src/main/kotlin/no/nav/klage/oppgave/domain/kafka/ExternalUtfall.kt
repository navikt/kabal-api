package no.nav.klage.oppgave.domain.kafka

enum class ExternalUtfall(val navn: String) {
    TRUKKET("Trukket"),
    RETUR("Retur"),
    OPPHEVET("Opphevet"),
    MEDHOLD("Medhold"),
    DELVIS_MEDHOLD("Delvis medhold"),
    STADFESTELSE("Stadfestelse"),
    UGUNST("Ugunst (Ugyldig)"),
    AVVIST("Avvist"),
    INNSTILLING_STADFESTELSE("Innstilling: Stadfestelse"),
    INNSTILLING_AVVIST("Innstilling: Avvist"),
    HENVIST("Henvist"),
    //TODO: Uavklart hva som skal gjøres for "HEVET" fra TR
    ;
}