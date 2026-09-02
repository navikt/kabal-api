package no.nav.klage.oppgave.api.view

import no.nav.klage.kodeverk.Type

/**
 * Type som kan oversendes fra fagsystem via oversendelses-API-ene (V3 og V4).
 *
 * Frikoblet fra [Type] med vilje: [Type] serialiseres på enum-navn (`@JsonValue`), så en renaming
 * der ville brutt kontraktene våre. Verdiene her skal derfor aldri endres.
 */
enum class OversendtType {
    KLAGE,
    ANKE,
    ;

    fun toType(): Type =
        when (this) {
            KLAGE -> Type.KLAGE
            ANKE -> Type.ANKE_FOER_2027
        }
}
