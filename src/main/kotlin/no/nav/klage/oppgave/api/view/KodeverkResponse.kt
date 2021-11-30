package no.nav.klage.oppgave.api.view

import no.nav.klage.kodeverk.*

data class KodeverkResponse(
    val hjemmel: List<Kode> = Hjemmel.values().asList().toDto(),
    val type: List<Kode> = Type.values().asList().toDto(),
    val tema: List<Kode> = Tema.values().asList().toDto(),
    val ytelse: List<Kode> = Ytelse.values().asList().toDto(),
    val utfall: List<Kode> = Utfall.values().asList().toDto(),
    val hjemlerPerTema: List<HjemlerPerTema> = hjemlerPerTema(),
    val hjemlerPerYtelse: List<HjemlerPerYtelse> = hjemlerPerYtelse(),
    val partIdType: List<Kode> = PartIdType.values().asList().toDto(),
//    val rolle: List<Kode> = Rolle.values().asList().toDto(),
    val fagsystem: List<Kode> = Fagsystem.values().asList().toDto(),
)

data class KodeDto(override val id: String, override val navn: String, override val beskrivelse: String) : Kode

data class HjemlerPerTema(val temaId: String, val hjemler: List<KodeDto>)

data class HjemlerPerYtelse(val ytelseId: String, val hjemler: List<KodeDto>)

fun Kode.toDto() = KodeDto(id, navn, beskrivelse)

fun List<Kode>.toDto() = map { it.toDto() }

fun hjemlerPerTema(): List<HjemlerPerTema> = hjemlerPerTema.map { HjemlerPerTema(it.tema.id, it.hjemler.toDto()) }

fun hjemlerPerYtelse(): List<HjemlerPerYtelse> = hjemlerPerYtelse.map { HjemlerPerYtelse(it.ytelse.id, it.hjemler.toDto()) }


