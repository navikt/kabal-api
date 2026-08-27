package no.nav.klage.oppgave.config

import no.nav.klage.dokument.exceptions.AddressValidationException
import no.nav.klage.dokument.exceptions.AttachmentHasVirusException
import no.nav.klage.dokument.exceptions.AttachmentIsEmptyException
import no.nav.klage.dokument.exceptions.AttachmentTooLargeException
import no.nav.klage.dokument.exceptions.DocumentDoesNotExistException
import no.nav.klage.dokument.exceptions.DokumentValidationException
import no.nav.klage.dokument.exceptions.NoAccessToDocumentException
import no.nav.klage.dokument.exceptions.SmartDocumentValidationException
import no.nav.klage.dokument.exceptions.SvarbrevPreviewException
import no.nav.klage.oppgave.exceptions.BehandlingAvsluttetException
import no.nav.klage.oppgave.exceptions.BehandlingManglerMedunderskriverException
import no.nav.klage.oppgave.exceptions.BehandlingNotFoundException
import no.nav.klage.oppgave.exceptions.DuplicateGosysOppgaveIdException
import no.nav.klage.oppgave.exceptions.DuplicateOversendelseException
import no.nav.klage.oppgave.exceptions.EREGOrganizationNotFoundException
import no.nav.klage.oppgave.exceptions.EnhetNotFoundForSaksbehandlerException
import no.nav.klage.oppgave.exceptions.FeilregistreringException
import no.nav.klage.oppgave.exceptions.GosysOppgaveNotEditableException
import no.nav.klage.oppgave.exceptions.GosysOppgaveNotFoundException
import no.nav.klage.oppgave.exceptions.GroupNotFoundException
import no.nav.klage.oppgave.exceptions.IllegalOperation
import no.nav.klage.oppgave.exceptions.IllegalUpdateException
import no.nav.klage.oppgave.exceptions.MeldingNotFoundException
import no.nav.klage.oppgave.exceptions.MissingTilgangException
import no.nav.klage.oppgave.exceptions.OversendtKlageNotValidException
import no.nav.klage.oppgave.exceptions.PreviousBehandlingNotFinalizedException
import no.nav.klage.oppgave.exceptions.SectionedValidationErrorWithDetailsException
import no.nav.klage.oppgave.exceptions.UserNotFoundException
import no.nav.klage.oppgave.exceptions.ValidationException
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getTeamLogger
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice
class ProblemHandlingControllerAdvice : ResponseEntityExceptionHandler() {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val ourLogger = getLogger(javaClass.enclosingClass)
        private val teamLogger = getTeamLogger()
    }

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        logError(
            httpStatus = HttpStatus.valueOf(statusCode.value()),
            errorMessage = ex.message ?: "No error message available",
            exception = ex,
        )

        return super.handleExceptionInternal(ex, body, headers, statusCode, request)
    }

    @ExceptionHandler
    fun handleIllegalArgumentException(ex: IllegalArgumentException): ProblemDetail = create(httpStatus = HttpStatus.BAD_REQUEST, ex = ex)

    @ExceptionHandler
    fun handleSizeLimitExceededException(ex: AttachmentTooLargeException): ProblemDetail =
        create(httpStatus = HttpStatus.PAYLOAD_TOO_LARGE, ex = ex)

    @ExceptionHandler
    fun handleFeilregistreringException(ex: FeilregistreringException): ProblemDetail = create(httpStatus = HttpStatus.BAD_REQUEST, ex = ex)

    @ExceptionHandler
    fun handleOversendtKlageNotValidException(ex: OversendtKlageNotValidException): ProblemDetail =
        create(httpStatus = HttpStatus.BAD_REQUEST, ex = ex)

    @ExceptionHandler
    fun handleBehandlingNotFound(ex: BehandlingNotFoundException): ProblemDetail = create(httpStatus = HttpStatus.NOT_FOUND, ex = ex)

    @ExceptionHandler
    fun handleMeldingNotFound(ex: MeldingNotFoundException): ProblemDetail = create(httpStatus = HttpStatus.NOT_FOUND, ex = ex)

    @ExceptionHandler
    fun handleGosysOppgaveNotFoundException(ex: GosysOppgaveNotFoundException): ProblemDetail =
        create(httpStatus = HttpStatus.NOT_FOUND, ex = ex)

    @ExceptionHandler
    fun handleGosysOppgaveNotEditableException(ex: GosysOppgaveNotEditableException): ProblemDetail =
        create(httpStatus = HttpStatus.BAD_REQUEST, ex = ex)

    @ExceptionHandler
    fun handleEREGOrganizationNotFoundException(ex: EREGOrganizationNotFoundException): ProblemDetail =
        create(httpStatus = HttpStatus.NOT_FOUND, ex = ex)

    @ExceptionHandler
    fun handleValidationException(ex: ValidationException): ProblemDetail = create(httpStatus = HttpStatus.BAD_REQUEST, ex = ex)

    @ExceptionHandler
    fun handleBehandlingAvsluttetException(ex: BehandlingAvsluttetException): ProblemDetail =
        create(httpStatus = HttpStatus.FORBIDDEN, ex = ex)

    @ExceptionHandler
    fun handlePreviousBehandlingNotFinalizedException(ex: PreviousBehandlingNotFinalizedException): ProblemDetail =
        create(httpStatus = HttpStatus.FORBIDDEN, ex = ex)

    @ExceptionHandler
    fun handleMissingTilgang(ex: MissingTilgangException): ProblemDetail = create(httpStatus = HttpStatus.FORBIDDEN, ex = ex)

    @ExceptionHandler
    fun handleResponseStatusException(ex: WebClientResponseException): ResponseEntity<Any> = createProblemForWebClientResponseException(ex)

    @ExceptionHandler
    fun handleDuplicateOversendelse(ex: DuplicateOversendelseException): ProblemDetail = create(httpStatus = HttpStatus.CONFLICT, ex = ex)

    @ExceptionHandler
    fun handleDuplicateGosysOppgaveIdException(ex: DuplicateGosysOppgaveIdException): ProblemDetail =
        create(httpStatus = HttpStatus.CONFLICT, ex = ex)

    @ExceptionHandler
    fun handleBehandlingManglerMedunderskriverException(ex: BehandlingManglerMedunderskriverException): ProblemDetail =
        create(httpStatus = HttpStatus.BAD_REQUEST, ex = ex)

    @ExceptionHandler
    fun handleEnhetNotFoundForSaksbehandlerException(ex: EnhetNotFoundForSaksbehandlerException): ProblemDetail =
        create(httpStatus = HttpStatus.INTERNAL_SERVER_ERROR, ex = ex)

    @ExceptionHandler
    fun handleIllegalOperation(ex: IllegalOperation): ProblemDetail = create(httpStatus = HttpStatus.BAD_REQUEST, ex = ex)

    @ExceptionHandler
    fun handleSectionedValidationErrorWithDetailsException(ex: SectionedValidationErrorWithDetailsException): ProblemDetail =
        createSectionedValidationProblem(ex)

    @ExceptionHandler
    fun handleDokumentValidationException(ex: DokumentValidationException): ProblemDetail =
        create(httpStatus = HttpStatus.BAD_REQUEST, ex = ex)

    @ExceptionHandler
    fun handleAddressValidationException(ex: AddressValidationException): ProblemDetail =
        create(httpStatus = HttpStatus.BAD_REQUEST, ex = ex)

    @ExceptionHandler
    fun handleDocumentDoesNotExistException(ex: DocumentDoesNotExistException): ProblemDetail =
        create(httpStatus = HttpStatus.NOT_FOUND, ex = ex)

    @ExceptionHandler(IllegalUpdateException::class)
    fun handleIllegalUpdateException(ex: IllegalUpdateException): ProblemDetail = create(httpStatus = HttpStatus.BAD_REQUEST, ex = ex)

    @ExceptionHandler
    fun handleSvarbrevPreviewException(ex: SvarbrevPreviewException): ProblemDetail = create(httpStatus = HttpStatus.BAD_REQUEST, ex = ex)

    @ExceptionHandler
    fun handleIllegalStateException(ex: IllegalStateException): ProblemDetail = create(httpStatus = HttpStatus.BAD_REQUEST, ex = ex)

    @ExceptionHandler
    fun handleSmartDocumentValidationException(ex: SmartDocumentValidationException): ProblemDetail =
        createSmartDocumentValidationProblem(ex)

    @ExceptionHandler
    fun handleNoAccessToDocumentException(ex: NoAccessToDocumentException): ProblemDetail =
        create(httpStatus = HttpStatus.FORBIDDEN, ex = ex)

    @ExceptionHandler
    fun handleUserNotFoundException(ex: UserNotFoundException): ProblemDetail = create(httpStatus = HttpStatus.NOT_FOUND, ex = ex)

    @ExceptionHandler
    fun handleGroupNotFoundException(ex: GroupNotFoundException): ProblemDetail = create(httpStatus = HttpStatus.NOT_FOUND, ex = ex)

    @ExceptionHandler
    fun handleAttachmentIsEmptyException(ex: AttachmentIsEmptyException): ProblemDetail =
        create(httpStatus = HttpStatus.BAD_REQUEST, ex = ex)

    @ExceptionHandler
    fun handleAttachmentHasVirusException(ex: AttachmentHasVirusException): ProblemDetail =
        create(httpStatus = HttpStatus.BAD_REQUEST, ex = ex)

    private fun createSmartDocumentValidationProblem(ex: SmartDocumentValidationException): ProblemDetail {
        logError(
            httpStatus = HttpStatus.BAD_REQUEST,
            errorMessage = ex.message ?: "smartDocument validation error without description",
            exception = ex,
        )

        return ProblemDetail.forStatus(HttpStatus.BAD_REQUEST).apply {
            this.title = ex.message
            this.setProperty("documents", ex.errors)
            // TODO remove when FE changed
            this.setProperty("dokumenter", ex.errors)
        }
    }

    private fun createProblemForWebClientResponseException(ex: WebClientResponseException): ResponseEntity<Any> {
        logError(
            httpStatus = HttpStatus.valueOf(ex.statusCode.value()),
            errorMessage = ex.statusText,
            exception = ex,
        )

        val contentType = ex.headers.contentType
        if (contentType != null && MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(contentType)) {
            // Pass through as-is when upstream already returned problem+json
            val body = ex.responseBodyAsByteArray
            return ResponseEntity.status(ex.statusCode).contentType(contentType).body(body)
        }

        // Fallback: wrap into a ProblemDetail
        val problemDetail =
            ProblemDetail.forStatus(ex.statusCode).apply {
                title = ex.statusText
                detail = ex.responseBodyAsString
            }
        return ResponseEntity
            .status(ex.statusCode)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problemDetail)
    }

    private fun createSectionedValidationProblem(ex: SectionedValidationErrorWithDetailsException): ProblemDetail {
        logError(
            httpStatus = HttpStatus.BAD_REQUEST,
            errorMessage = ex.title,
            exception = ex,
        )

        return ProblemDetail.forStatus(HttpStatus.BAD_REQUEST).apply {
            this.title = ex.title
            this.setProperty("sections", ex.sections)
        }
    }

    private fun create(
        httpStatus: HttpStatus,
        ex: Exception,
    ): ProblemDetail {
        val errorMessage = ex.message ?: "No error message available"

        logError(
            httpStatus = httpStatus,
            errorMessage = errorMessage,
            exception = ex,
        )

        return ProblemDetail.forStatus(httpStatus).apply {
            title = errorMessage
        }
    }

    private fun logError(
        httpStatus: HttpStatus,
        errorMessage: String,
        exception: Exception,
    ) {
        when {
            httpStatus.is5xxServerError -> {
                ourLogger.error("Exception thrown to client: ${exception.javaClass.name}. See team-logs for more details.")
                teamLogger.error("Exception thrown to client: ${httpStatus.reasonPhrase}, $errorMessage", exception)
            }

            else -> {
                ourLogger.warn("Exception thrown to client: ${exception.javaClass.name}. See team-logs for more details.")
                teamLogger.warn("Exception thrown to client: ${httpStatus.reasonPhrase}, $errorMessage", exception)
            }
        }
    }
}
