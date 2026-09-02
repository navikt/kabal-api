package no.nav.klage.oppgave.domain.kafka

import no.nav.klage.kodeverk.Type

/**
 * Ekstern representasjon av [Type] i API- og Kafka-kontrakter mot fagsystemene.
 *
 * Frikoblet fra kodeverket med vilje: [Type] serialiseres på enum-navn (`@JsonValue`), så en
 * renaming der ville brutt kontraktene våre. Verdiene her skal derfor aldri endres.
 */
enum class ExternalType {
    KLAGE,
    ANKE,
    ANKE_I_TRYGDERETTEN,
    BEHANDLING_ETTER_TRYGDERETTEN_OPPHEVET,
    OMGJOERINGSKRAV,
    BEGJAERING_OM_GJENOPPTAK,
    BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN,
    ;

    /**
     * ANKE og ANKE_I_TRYGDERETTEN dekker både foer- og etter-2027-variantene, siden fagsystemene
     * ikke skiller på dette.
     */
    fun toTypes(): List<Type> =
        when (this) {
            KLAGE -> listOf(Type.KLAGE)
            ANKE -> listOf(Type.ANKE_FOER_2027, Type.ANKE_ETTER_2027)
            ANKE_I_TRYGDERETTEN -> listOf(Type.ANKE_I_TRYGDERETTEN_FOER_2027, Type.ANKE_I_TRYGDERETTEN_ETTER_2027)
            BEHANDLING_ETTER_TRYGDERETTEN_OPPHEVET -> listOf(Type.BEHANDLING_ETTER_TRYGDERETTEN_OPPHEVET)
            OMGJOERINGSKRAV -> listOf(Type.OMGJOERINGSKRAV)
            BEGJAERING_OM_GJENOPPTAK -> listOf(Type.BEGJAERING_OM_GJENOPPTAK)
            BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN -> listOf(Type.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN)
        }
}

fun Type.toExternalType(): ExternalType =
    when (this) {
        Type.KLAGE -> ExternalType.KLAGE
        Type.ANKE_FOER_2027, Type.ANKE_ETTER_2027 -> ExternalType.ANKE
        Type.ANKE_I_TRYGDERETTEN_FOER_2027, Type.ANKE_I_TRYGDERETTEN_ETTER_2027 -> ExternalType.ANKE_I_TRYGDERETTEN
        Type.BEHANDLING_ETTER_TRYGDERETTEN_OPPHEVET -> ExternalType.BEHANDLING_ETTER_TRYGDERETTEN_OPPHEVET
        Type.OMGJOERINGSKRAV -> ExternalType.OMGJOERINGSKRAV
        Type.BEGJAERING_OM_GJENOPPTAK -> ExternalType.BEGJAERING_OM_GJENOPPTAK
        Type.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN -> ExternalType.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN
    }
