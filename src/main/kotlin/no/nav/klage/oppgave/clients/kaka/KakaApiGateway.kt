package no.nav.klage.oppgave.clients.kaka

import no.nav.klage.kodeverk.Enhet
import no.nav.klage.oppgave.clients.kaka.model.request.SaksdataInput
import no.nav.klage.oppgave.clients.kaka.model.response.KakaOutput
import no.nav.klage.oppgave.domain.behandling.*
import no.nav.klage.oppgave.exceptions.InvalidProperty
import no.nav.klage.oppgave.util.getLogger
import org.springframework.stereotype.Service
import java.util.*

@Service
class KakaApiGateway(private val kakaApiClient: KakaApiClient) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun createKvalitetsvurdering(kvalitetsvurderingVersion: Int): KakaOutput {
        val kakaOutput = kakaApiClient.createKvalitetsvurdering(kvalitetsvurderingVersion = kvalitetsvurderingVersion)
        logger.debug("New kvalitetsvurderingId {} created in Kaka", kakaOutput)
        return kakaOutput
    }

    fun finalizeBehandling(behandling: BehandlingWithKvalitetsvurdering) {
        logger.debug("Sending saksdata to Kaka because behandling is finished.")

        kakaApiClient.finalizeBehandling(
            saksdataInput = behandling.toSaksdataInput(),
            kvalitetsvurderingVersion = behandling.kakaKvalitetsvurderingVersion,
        )
    }

    fun deleteKvalitetsvurdering(kvalitetsvurderingId: UUID, kvalitetsvurderingVersion: Int) {
        logger.debug("Deleting kvalitetsvurdering with id {} in Kaka.", kvalitetsvurderingId)
        kakaApiClient.deleteKvalitetsvurdering(
            kvalitetsvurderingId = kvalitetsvurderingId,
            kvalitetsvurderingVersion = kvalitetsvurderingVersion,
        )
    }

    fun getValidationErrors(behandling: BehandlingWithKvalitetsvurdering): List<InvalidProperty> {
        logger.debug("Getting kvalitetsvurdering validation errors")

        behandling as Behandling

        return kakaApiClient.getValidationErrors(
            kvalitetsvurderingId = behandling.kakaKvalitetsvurderingId!!,
            ytelseId = behandling.ytelse.id,
            typeId = behandling.type.id,
            kvalitetsvurderingVersion = behandling.kakaKvalitetsvurderingVersion,
        ).validationErrors.map {
            InvalidProperty(
                field = it.field,
                reason = it.reason
            )
        }
    }

    private fun BehandlingWithKvalitetsvurdering.toSaksdataInput(): SaksdataInput {
        val (vedtaksinstansEnhet, kvalitetsvurderingId) =
            when (this) {
                is Klagebehandling -> {
                    if (Enhet.entries.none { it.navn == avsenderEnhetFoersteinstans }) {
                        logger.error("avsenderEnhetFoersteinstans $avsenderEnhetFoersteinstans not found in internal kodeverk")
                    }
                    avsenderEnhetFoersteinstans to kakaKvalitetsvurderingId
                }

                is Ankebehandling -> {
                    if (Enhet.entries.none { it.navn == klageBehandlendeEnhet }) {
                        logger.error("klageBehandlendeEnhet $klageBehandlendeEnhet not found in internal kodeverk")
                    }
                    klageBehandlendeEnhet to kakaKvalitetsvurderingId
                }

                is BehandlingEtterTrygderettenOpphevet -> {
                    if (Enhet.entries.none { it.navn == ankeBehandlendeEnhet }) {
                        logger.error("ankeBehandlendeEnhet $ankeBehandlendeEnhet not found in internal kodeverk")
                    }
                    ankeBehandlendeEnhet to kakaKvalitetsvurderingId
                }

                is Omgjoeringskravbehandling -> {
                    if (Enhet.entries.none { it.navn == klageBehandlendeEnhet }) {
                        logger.error("klageBehandlendeEnhet $klageBehandlendeEnhet not found in internal kodeverk")
                    }
                    klageBehandlendeEnhet to kakaKvalitetsvurderingId
                }

                is Gjenopptaksbehandling -> {
                    if (Enhet.entries.none { it.navn == klageBehandlendeEnhet }) {
                        logger.error("klageBehandlendeEnhet $klageBehandlendeEnhet not found in internal kodeverk")
                    }
                    klageBehandlendeEnhet to kakaKvalitetsvurderingId
                }

                else -> {
                    throw RuntimeException("Wrong behandling type")
                }
            }

        val tilknyttetEnhet = Enhet.entries.find { it.navn == tildeling?.enhet!! }

        return SaksdataInput(
            sakenGjelder = sakenGjelder.partId.value,
            sakstype = type.id,
            ytelseId = ytelse.id,
            mottattKlageinstans = mottattKlageinstans.toLocalDate(),
            vedtaksinstansEnhet = vedtaksinstansEnhet,
            mottattVedtaksinstans = if (this is Klagebehandling) mottattVedtaksinstans else null,
            utfall = utfall!!.id,
            registreringshjemler = registreringshjemler.map { it.id },
            kvalitetsvurderingId = kvalitetsvurderingId!!,
            avsluttetAvSaksbehandler = ferdigstilling!!.avsluttetAvSaksbehandler,
            utfoerendeSaksbehandler = tildeling?.saksbehandlerident!!,
            tilknyttetEnhet = tilknyttetEnhet!!.navn,
            tilbakekreving = tilbakekreving,
        )
    }
}