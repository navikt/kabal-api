package no.nav.klage.oppgave.clients.kant

import no.nav.klage.kodeverk.Enhet
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingEtter2027
import no.nav.klage.oppgave.domain.behandling.AnkebehandlingEtter2027
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.BehandlingWithKantReference
import no.nav.klage.oppgave.exceptions.InvalidProperty
import no.nav.klage.oppgave.util.getLogger
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Service
import java.time.temporal.ChronoUnit

@Service
class KantApiGateway(
    private val kantApiClient: KantApiClient,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun createSaksdataInKant(behandling: BehandlingWithKantReference): KantSaksdata {
        behandling as Behandling
        val response =
            kantApiClient.createSaksdata(
                KantSaksdataInput(
                    kabalReference = behandling.id,
                    sakstype = behandling.type,
                    ytelse = behandling.ytelse,
                ),
            )
        validateIdentity(behandling, response)
        check(behandling.kantReference == null || behandling.kantReference == response.id) {
            "Kant reference changed during creation"
        }
        return response
    }

    fun finalizeSaksdata(behandling: BehandlingWithKantReference) {
        behandling as Behandling
        val tilknyttetEnhet = Enhet.entries.find { it.navn == behandling.tildeling?.enhet!! }
        val sakITrygderettenOpprettet =
            if (behandling is AnkeITrygderettenbehandlingEtter2027) {
                behandling.sendtTilTrygderetten.toLocalDate()
            } else {
                null
            }
        val mottattKlageinstans =
            if (behandling is AnkebehandlingEtter2027) {
                behandling.mottattKlageinstans.toLocalDate()
            } else {
                null
            }
        val input =
            KantAvslutningInput(
                utfall = checkNotNull(behandling.utfall) { "Cannot finalize Kant without utfall" },
                registreringshjemler = behandling.registreringshjemler.toSet(),
                avsluttetAvSaksbehandler =
                    checkNotNull(behandling.ferdigstilling) {
                        "Cannot finalize Kant before user completion"
                    }.avsluttetAvSaksbehandler.truncatedTo(ChronoUnit.MICROS),
                sakITrygderettenOpprettet = sakITrygderettenOpprettet,
                mottattKlageinstans = mottattKlageinstans,
                tilknyttetEnhet = tilknyttetEnhet!!.id,
                tilbakekreving = behandling.tilbakekreving,
            )

        val reference = checkNotNull(behandling.kantReference)
        val response = kantApiClient.finalizeSaksdata(id = reference, input = input)
        validateIdentity(behandling = behandling, response = response)
    }

    @Retryable
    fun getValidationErrors(behandling: BehandlingWithKantReference): List<InvalidProperty> =
        behandling.kantReference?.let { kantApiClient.getValidationErrors(kantId = it) }?.validationErrors?.map {
            InvalidProperty(
                field = it.field,
                reason = it.reason,
            )
        } ?: emptyList()

    private fun validateIdentity(
        behandling: BehandlingWithKantReference,
        response: KantSaksdata,
    ) {
        behandling as Behandling
        if (response.kabalReference != behandling.id || response.sakstype != behandling.type ||
            response.ytelse != behandling.ytelse
        ) {
            logger.error("Kant returned an inconsistent identity for behandling {}", behandling.id)
            error("Kant identity does not match behandling")
        }
    }
}
