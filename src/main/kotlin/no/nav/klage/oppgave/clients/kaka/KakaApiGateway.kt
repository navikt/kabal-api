package no.nav.klage.oppgave.clients.kaka

import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.oppgave.clients.kaka.model.request.SaksdataInput
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

    fun finalizeKlagebehandling(klagebehandling: Klagebehandling) {
        logger.debug("Sending saksdata to Kaka because klagebehandling is finished.")
        kakaApiClient.finalizeKlagebehandling(klagebehandling.toSaksdataInput())
    }

    fun getValidationErrors(klagebehandling: Klagebehandling): List<InvalidProperty> {
        logger.debug("Getting kvalitetsvurdering validation errors")
        return kakaApiClient.getValidationErrors(
            klagebehandling.kakaKvalitetsvurderingId!!,
            //TODO: Endre til ytelse når dette er på plass i Kaka.
            klagebehandling.ytelse.toTema().id
        ).validationErrors.map {
            InvalidProperty(
                field = it.field,
                reason = it.reason
            )
        }
    }

    private fun Klagebehandling.toSaksdataInput(): SaksdataInput {
        return SaksdataInput(
            sakenGjelder = sakenGjelder.partId.value,
            sakstype = type.id,
            ytelseId = ytelse.id,
            mottattKlageinstans = mottattKlageinstans.toLocalDate(),
            vedtaksinstansEnhet = avsenderEnhetFoersteinstans,
            mottattVedtaksinstans = mottattFoersteinstans,
            utfall = vedtak.utfall!!.id,
            hjemler = hjemmelFilter(vedtak.hjemler),
            kvalitetsvurderingId = kakaKvalitetsvurderingId!!,
            avsluttetAvSaksbehandler = avsluttetAvSaksbehandler!!,
            utfoerendeSaksbehandler = tildeling?.saksbehandlerident!!,
        )
    }

    val registreringshjemmelIdList = Registreringshjemmel.values().map { registreringshjemmel ->
        registreringshjemmel.id
    }

    private fun hjemmelFilter(input: Set<Hjemmel>): List<String> {
        input.filter {
            !registreringshjemmelIdList.contains(it.id)
        }.forEach {
            logger.warn("Forsøkte å sende ugyldig registreringshjemmel til kaka, med id ${it.id}")
        }

        return input.filter {
            registreringshjemmelIdList.contains(it.id)
        }.map {
            it.id
        }
    }
}

