package no.nav.klage.oppgave.config.problem

import no.nav.klage.oppgave.exceptions.*
import no.nav.klage.oppgave.util.getLogger
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.zalando.problem.Problem
import org.zalando.problem.Status
import org.zalando.problem.ThrowableProblem
import org.zalando.problem.spring.web.advice.AdviceTrait
import org.zalando.problem.spring.web.advice.ProblemHandling

@ControllerAdvice
class ProblemHandlingControllerAdvice : OurOwnExceptionAdviceTrait, ProblemHandling

interface OurOwnExceptionAdviceTrait : AdviceTrait {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @ExceptionHandler
    fun handleOversendtKlageNotValidException(
        ex: OversendtKlageNotValidException,
        request: NativeWebRequest
    ): ResponseEntity<Problem> =
        create(Status.BAD_REQUEST, ex, request)

    @ExceptionHandler
    fun handleKlagebehandlingSamtidigEndretException(
        ex: KlagebehandlingSamtidigEndretException,
        request: NativeWebRequest
    ): ResponseEntity<Problem> =
        create(Status.CONFLICT, ex, request)

    @ExceptionHandler
    fun handleOversendtKlageReceivedBeforeException(
        ex: OversendtKlageReceivedBeforeException,
        request: NativeWebRequest
    ): ResponseEntity<Problem> =
        create(Status.CONFLICT, ex, request)

    @ExceptionHandler
    fun handleOppgaveNotFound(ex: OppgaveNotFoundException, request: NativeWebRequest): ResponseEntity<Problem> =
        create(Status.NOT_FOUND, ex, request)

    @ExceptionHandler
    fun handleKlagebehandlingNotFound(
        ex: KlagebehandlingNotFoundException,
        request: NativeWebRequest
    ): ResponseEntity<Problem> =
        create(Status.NOT_FOUND, ex, request)

    @ExceptionHandler
    fun handleVedtakNotFound(
        ex: VedtakNotFoundException,
        request: NativeWebRequest
    ): ResponseEntity<Problem> =
        create(Status.NOT_FOUND, ex, request)

    @ExceptionHandler
    fun handleMeldingNotFound(
        ex: MeldingNotFoundException,
        request: NativeWebRequest
    ): ResponseEntity<Problem> =
        create(Status.NOT_FOUND, ex, request)

    @ExceptionHandler
    fun handleSaksdokumentNotFound(
        ex: SaksdokumentNotFoundException,
        request: NativeWebRequest
    ): ResponseEntity<Problem> =
        create(Status.NOT_FOUND, ex, request)

    @ExceptionHandler
    fun handleValidationException(
        ex: ValidationException,
        request: NativeWebRequest
    ): ResponseEntity<Problem> =
        create(Status.BAD_REQUEST, ex, request)

    @ExceptionHandler
    fun handleMissingTilgang(ex: MissingTilgangException, request: NativeWebRequest): ResponseEntity<Problem> =
        create(Status.FORBIDDEN, ex, request)

    @ExceptionHandler
    fun handleNotMatchingUser(ex: NotMatchingUserException, request: NativeWebRequest): ResponseEntity<Problem> =
        create(Status.FORBIDDEN, ex, request)

    @ExceptionHandler
    fun handleFeatureNotEnabled(ex: FeatureNotEnabledException, request: NativeWebRequest): ResponseEntity<Problem> =
        create(Status.FORBIDDEN, ex, request)

    @ExceptionHandler
    fun handleNoSaksbehandlerRoleEnabled(
        ex: NoSaksbehandlerRoleException,
        request: NativeWebRequest
    ): ResponseEntity<Problem> =
        create(Status.FORBIDDEN, ex, request)

    @ExceptionHandler
    fun handleNotOwnEnhet(ex: NotOwnEnhetException, request: NativeWebRequest): ResponseEntity<Problem> =
        create(Status.FORBIDDEN, ex, request)

    @ExceptionHandler
    fun handleResponseStatusException(
        ex: WebClientResponseException,
        request: NativeWebRequest
    ): ResponseEntity<Problem> =
        create(ex, createProblem(ex), request)

    @ExceptionHandler
    fun handleDuplicateOversendelse(ex: DuplicateOversendelseException, request: NativeWebRequest): ResponseEntity<Problem> =
        create(Status.CONFLICT, ex, request)

    @ExceptionHandler
    fun handleKlagebehandlingManglerMedunderskriverException(
        ex: KlagebehandlingManglerMedunderskriverException,
        request: NativeWebRequest
    ): ResponseEntity<Problem> =
        create(Status.BAD_REQUEST, ex, request)

    private fun createProblem(ex: WebClientResponseException): ThrowableProblem {
        return Problem.builder()
            .withStatus(mapStatus(ex.statusCode))
            .withTitle(ex.statusText)
            .withDetail(ex.responseBodyAsString)
            .build()
    }

    private fun mapStatus(status: HttpStatus): Status =
        try {
            Status.valueOf(status.value())
        } catch (ex: Exception) {
            logger.warn("Unable to map WebClientResponseException with status {}", status.value())
            Status.INTERNAL_SERVER_ERROR
        }
}
