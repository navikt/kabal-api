package no.nav.klage.oppgave.clients.kaka

import no.nav.klage.kodeverk.Enhet
import no.nav.klage.oppgave.clients.kaka.model.request.SaksdataInput
import no.nav.klage.oppgave.domain.Behandling
import no.nav.klage.oppgave.domain.klage.Ankebehandling
import no.nav.klage.oppgave.domain.klage.Klagebehandling
import no.nav.klage.oppgave.exceptions.InvalidProperty
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.stereotype.Service
import java.util.*

@Service
class KakaApiGateway(private val kakaApiClient: KakaApiClient) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    fun createKvalitetsvurdering(): UUID {
        val id = kakaApiClient.createKvalitetsvurdering().id
        logger.debug("New kvalitetsvurderingId created in kaka: $id")
        return id
    }

    fun finalizeBehandling(behandling: Behandling) {
        logger.debug("Sending saksdata to Kaka because behandling is finished.")
        kakaApiClient.finalizeBehandling(behandling.toSaksdataInput())
    }

    fun getValidationErrors(behandling: Behandling): List<InvalidProperty> {
        logger.debug("Getting kvalitetsvurdering validation errors")
        return kakaApiClient.getValidationErrors(
            behandling.kakaKvalitetsvurderingId!!,
            behandling.ytelse.id,
            behandling.type.id
        ).validationErrors.map {
            InvalidProperty(
                field = it.field,
                reason = it.reason
            )
        }
    }

    private fun Behandling.toSaksdataInput(): SaksdataInput {
        val vedtaksinstansEnhet =
            when (this) {
                is Klagebehandling -> {
                    avsenderEnhetFoersteinstans
                }
                is Ankebehandling -> {
                    klageBehandlendeEnhet
                }
                else -> {
                    throw RuntimeException("Wrong behandling type")
                }
            }

        val tilknyttetEnhet = Enhet.values().find { it.navn == tildeling?.enhet!! }

        return SaksdataInput(
            sakenGjelder = sakenGjelder.partId.value,
            sakstype = type.id,
            ytelseId = ytelse.id,
            mottattKlageinstans = mottattKlageinstans.toLocalDate(),
            vedtaksinstansEnhet = vedtaksinstansEnhet,
            mottattVedtaksinstans = if (this is Klagebehandling) mottattVedtaksinstans else null,
            utfall = currentDelbehandling().utfall!!.id,
            registreringshjemler = currentDelbehandling().hjemler.map { it.id },
            kvalitetsvurderingId = kakaKvalitetsvurderingId!!,
            avsluttetAvSaksbehandler = currentDelbehandling().avsluttetAvSaksbehandler!!,
            utfoerendeSaksbehandler = tildeling?.saksbehandlerident!!,
            tilknyttetEnhet = tilknyttetEnhet!!.navn
        )
    }
}

