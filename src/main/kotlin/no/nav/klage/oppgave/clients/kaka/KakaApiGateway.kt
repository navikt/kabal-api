package no.nav.klage.oppgave.clients.kaka

import no.nav.klage.kodeverk.Enhet
import no.nav.klage.oppgave.clients.kaka.model.request.SaksdataInput
import no.nav.klage.oppgave.clients.kaka.model.response.KakaOutput
import no.nav.klage.oppgave.domain.klage.Ankebehandling
import no.nav.klage.oppgave.domain.klage.Behandling
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

    fun createKvalitetsvurdering(kvalitetsvurderingVersion: Int): KakaOutput {
        val kakaOutput = kakaApiClient.createKvalitetsvurdering(kvalitetsvurderingVersion = kvalitetsvurderingVersion)
        logger.debug("New kvalitetsvurderingId {} created in Kaka", kakaOutput)
        return kakaOutput
    }

    fun finalizeBehandling(behandling: Behandling) {
        logger.debug("Sending saksdata to Kaka because behandling is finished.")

        val kvalitetsvurderingVersion = when (behandling) {
            is Klagebehandling -> behandling.kakaKvalitetsvurderingVersion
            is Ankebehandling -> behandling.kakaKvalitetsvurderingVersion
            else -> error("Not valid type")
        }

        kakaApiClient.finalizeBehandling(
            saksdataInput = behandling.toSaksdataInput(),
            kvalitetsvurderingVersion = kvalitetsvurderingVersion
        )
    }

    fun deleteKvalitetsvurderingV2(kvalitetsvurderingId: UUID) {
        logger.debug("Deleting kvalitetsvurdering with id {} in Kaka.", kvalitetsvurderingId)
        kakaApiClient.deleteKvalitetsvurderingV2(
            kvalitetsvurderingId = kvalitetsvurderingId
        )
    }

    fun getValidationErrors(behandling: Behandling): List<InvalidProperty> {
        logger.debug("Getting kvalitetsvurdering validation errors")

        val (kvalitetsvurderingId, kvalitetsvurderingVersion) = when (behandling) {
            is Klagebehandling -> {
                behandling.kakaKvalitetsvurderingId to behandling.kakaKvalitetsvurderingVersion
            }

            is Ankebehandling -> {
                behandling.kakaKvalitetsvurderingId to behandling.kakaKvalitetsvurderingVersion
            }

            else -> error("Not valid type")
        }

        return kakaApiClient.getValidationErrors(
            kvalitetsvurderingId = kvalitetsvurderingId!!,
            ytelseId = behandling.ytelse.id,
            typeId = behandling.type.id,
            kvalitetsvurderingVersion = kvalitetsvurderingVersion,
        ).validationErrors.map {
            InvalidProperty(
                field = it.field,
                reason = it.reason
            )
        }
    }

    private fun Behandling.toSaksdataInput(): SaksdataInput {
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
            tilknyttetEnhet = tilknyttetEnhet!!.navn
        )
    }
}

