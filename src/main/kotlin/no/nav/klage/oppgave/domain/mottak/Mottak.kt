package no.nav.klage.oppgave.domain.mottak

import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.domain.behandling.embedded.Klager
import no.nav.klage.oppgave.domain.behandling.embedded.Prosessfullmektig
import no.nav.klage.oppgave.domain.behandling.embedded.SakenGjelder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.util.*

data class Mottak(
    val type: Type,
    val klager: Klager,
    val prosessfullmektig: Prosessfullmektig?,
    val sakenGjelder: SakenGjelder?,
    val fagsystem: Fagsystem,
    val fagsakId: String,
    val kildeReferanse: String,
    val dvhReferanse: String?,
    val hjemler: Set<Hjemmel>,
    val forrigeSaksbehandlerident: String?,
    val forrigeBehandlendeEnhet: String,
    val mottakDokument: MutableSet<MottakDokument> = mutableSetOf(),
    val brukersKlageMottattVedtaksinstans: LocalDate?,
    val sakMottattKaDato: LocalDateTime,
    val frist: LocalDate?,
    val created: LocalDateTime = LocalDateTime.now(),
    val modified: LocalDateTime = LocalDateTime.now(),
    val ytelse: Ytelse,
    val kommentar: String?,
    val forrigeBehandlingId: UUID?,
    val sentFrom: Sender
) {
    enum class Sender {
        FAGSYSTEM, KABIN
    }

    fun generateFrist(): LocalDate {
        return frist ?: getDefaultFristForType(sakMottattKaDato = sakMottattKaDato, type = type)
    }

    private fun getDefaultFristForType(sakMottattKaDato: LocalDateTime, type: Type): LocalDate {
        return when (type) {
            Type.ANKE -> (sakMottattKaDato.toLocalDate() + Period.ofWeeks(0))
            else -> (sakMottattKaDato.toLocalDate() + Period.ofWeeks(12))
        }
    }
}
