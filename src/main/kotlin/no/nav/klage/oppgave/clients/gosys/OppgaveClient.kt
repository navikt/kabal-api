package no.nav.klage.oppgave.clients.gosys

import brave.Tracer
import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.domain.KlagebehandlingerSearchCriteria
import no.nav.klage.oppgave.exceptions.OppgaveNotFoundException
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import org.springframework.web.util.UriBuilder
import java.lang.System.currentTimeMillis
import java.net.URI
import java.time.format.DateTimeFormatter

@Component
class OppgaveClient(
    private val oppgaveWebClient: WebClient,
    private val tokenUtil: TokenUtil,
    private val tracer: Tracer,
    @Value("\${spring.application.name}") val applicationName: String
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val securelogger = getSecureLogger()

        const val STATUSKATEGORI_AAPEN = "AAPEN"
        const val HJEMMEL = "HJEMMEL"
        const val BEHANDLINGSTYPE_KLAGE = "ae0058"
    }

    @Retryable
    fun getOppgaveCount(oppgaveSearchCriteria: KlagebehandlingerSearchCriteria): Int {
        return logTimingAndWebClientResponseException("getOneSearchPage") {
            oppgaveWebClient.get()
                .uri { uriBuilder -> oppgaveSearchCriteria.buildUri(uriBuilder) }
                .header("Authorization", "Bearer ${tokenUtil.getStsSystembrukerToken()}")
                .header("X-Correlation-ID", tracer.currentSpan().context().traceIdString())
                .header("Nav-Consumer-Id", applicationName)
                .retrieve()
                .bodyToMono<OppgaveResponse>()
                .block() ?: throw RuntimeException("Oppgaver could not be fetched")
        }.antallTreffTotalt
    }

    @Retryable
    fun getOneSearchPage(oppgaveSearchCriteria: KlagebehandlingerSearchCriteria): OppgaveResponse {
        return logTimingAndWebClientResponseException("getOneSearchPage") {
            oppgaveWebClient.get()
                .uri { uriBuilder -> oppgaveSearchCriteria.buildUri(uriBuilder) }
                .header("Authorization", "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithOppgaveScope()}")
                .header("X-Correlation-ID", tracer.currentSpan().context().traceIdString())
                .header("Nav-Consumer-Id", applicationName)
                .retrieve()
                .bodyToMono<OppgaveResponse>()
                .block() ?: throw RuntimeException("Oppgaver could not be fetched")
        }
    }

    private fun KlagebehandlingerSearchCriteria.buildUri(origUriBuilder: UriBuilder): URI {
        logger.debug("Search criteria: {}", this)
        val uriBuilder = origUriBuilder
            .queryParam("statuskategori", statuskategori)
            .queryParam("offset", offset)
            .queryParam("limit", limit)
            .queryParam("oppgavetype", "BEH_SAK_MK")
            .queryParam("oppgavetype", "BEH_SAK")

        enhetId?.let {
            uriBuilder.queryParam("tildeltEnhetsnr", enhetId)
        }

        if (typer.isNotEmpty()) {
            typer.forEach {
                uriBuilder.queryParam("behandlingstype", mapType(it))
            }
        } else {
            uriBuilder.queryParam("behandlingstype", mapType(Type.KLAGE))
        }

        temaer.forEach {
            uriBuilder.queryParam("tema", it.navn)
        }

        erTildeltSaksbehandler?.let {
            uriBuilder.queryParam("tildeltRessurs", erTildeltSaksbehandler)
        }
        saksbehandler?.let {
            uriBuilder.queryParam("tilordnetRessurs", saksbehandler)
        }

        opprettetFom?.let {
            uriBuilder.queryParam("opprettetFom", DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(it))
        }
        opprettetTom?.let {
            uriBuilder.queryParam("opprettetTom", DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(it))
        }
        ferdigstiltFom?.let {
            uriBuilder.queryParam(
                "ferdigstiltFom",
                DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(it.atTime(0, 0))
            )
        }
        ferdigstiltTom?.let {
            uriBuilder.queryParam(
                "ferdigstiltTom",
                DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(it.atTime(23, 59))
            )
        }
        fristFom?.let {
            uriBuilder.queryParam("fristFom", DateTimeFormatter.ISO_LOCAL_DATE.format(it))
        }
        fristTom?.let {
            uriBuilder.queryParam("fristTom", DateTimeFormatter.ISO_LOCAL_DATE.format(it))
        }

        //FRIST is default in oppgave-api.
//        uriBuilder.queryParam("sorteringsfelt", orderBy ?: "frist")
        uriBuilder.queryParam("sorteringsrekkefolge", order ?: KlagebehandlingerSearchCriteria.Order.ASC)

        if (hjemler.isNotEmpty()) {
            uriBuilder.queryParam("metadatanokkel", HJEMMEL)
            hjemler.forEach {
                uriBuilder.queryParam("metadataverdi", it)
            }
        }

        val uri = uriBuilder.build()
        logger.info("Making search request with query {}", uri.query)
        return uri
    }

    private fun mapType(type: Type): String {
        return when (type) {
            Type.KLAGE -> BEHANDLINGSTYPE_KLAGE
            else -> {
                logger.warn("Unknown type: {}", type)
                BEHANDLINGSTYPE_KLAGE
            }
        }
    }

    @Retryable
    fun putOppgave(
        oppgaveId: Long,
        oppgave: EndreOppgave
    ): Oppgave {
        return logTimingAndWebClientResponseException("putOppgave") {
            oppgaveWebClient.put()
                .uri { uriBuilder ->
                    uriBuilder.pathSegment("{id}").build(oppgaveId)
                }
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithOppgaveScope()}")
                .header("X-Correlation-ID", tracer.currentSpan().context().traceIdString())
                .header("Nav-Consumer-Id", applicationName)
                .bodyValue(oppgave)
                .retrieve()
                .bodyToMono<Oppgave>()
                .block() ?: throw OppgaveNotFoundException("Oppgave could not be put")
        }
    }

    @Retryable
    fun getOppgave(oppgaveId: Long): Oppgave {
        return logTimingAndWebClientResponseException("getOppgave") {
            oppgaveWebClient.get()
                .uri { uriBuilder ->
                    uriBuilder.pathSegment("{id}").build(oppgaveId)
                }
                .header("Authorization", "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithOppgaveScope()}")
                .header("X-Correlation-ID", tracer.currentSpan().context().traceIdString())
                .header("Nav-Consumer-Id", applicationName)
                .retrieve()
                .bodyToMono<Oppgave>()
                .block() ?: throw OppgaveNotFoundException("Oppgave could not be fetched")
        }
    }

    private fun <T> logTimingAndWebClientResponseException(methodName: String, function: () -> T): T {
        val start: Long = currentTimeMillis()
        try {
            return function.invoke()
        } catch (ex: WebClientResponseException) {
            logger.warn("Caught WebClientResponseException, see securelogs for details")
            securelogger.error(
                "Got a {} error calling Oppgave {} {} with message {}",
                ex.statusCode,
                ex.request?.method ?: "-",
                ex.request?.uri ?: "-",
                ex.responseBodyAsString
            )
            throw ex
        } catch (rtex: RuntimeException) {
            logger.warn("Caught RuntimeException", rtex)
            throw rtex
        } finally {
            val end: Long = currentTimeMillis()
            logger.info("Method {} took {} millis", methodName, (end - start))
        }
    }
}
