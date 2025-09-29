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
    INNSTILLING_GJENOPPTAS_KAS_VEDTAK_STADFESTES("Innstilling: Gjenopptas, men klageinstanses vedtak stadfestes"),
    INNSTILLING_GJENOPPTAS_IKKE("Innstilling: Gjenopptas ikke"),
    HENVIST("Henvist"),
    HEVET("Hevet"),
    MEDHOLD_ETTER_FVL_35("Medhold etter fvl. § 35"),
    HENLAGT("Henlagt"),
    GJENOPPTATT_DELVIS_ELLER_FULLT_MEDHOLD("Gjenopptatt - Delvis eller fullt medhold"),
    GJENOPPTATT_OPPHEVET("Gjenopptatt - Opphevet"),
    GJENOPPTATT_STADFESTET("Gjenopptatt - Stadfestet"),
    IKKE_GJENOPPTATT("Ikke gjenopptatt")
    ;
}