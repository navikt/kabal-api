package no.nav.klage.oppgave.config

import no.nav.klage.dokument.exceptions.*
import no.nav.klage.oppgave.exceptions.*
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getTeamLogger
import org.springframework.http.*
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
        request: WebRequest
    ): ResponseEntity<Any>? {
        logError(
            httpStatus = HttpStatus.valueOf(statusCode.value()),
            errorMessage = ex.message ?: "No error message available",
            exception = ex,
        )

        return super.handleExceptionInternal(ex, body, headers, statusCode, request)
    }

    @ExceptionHandler
    fun handleSizeLimitExceededException(
        ex: AttachmentTooLargeException,
    ): ProblemDetail =
        create(HttpStatus.PAYLOAD_TOO_LARGE, ex)

    @ExceptionHandler
    fun handleFeilregistreringException(
        ex: FeilregistreringException,
    ): ProblemDetail =
        create(HttpStatus.BAD_REQUEST, ex)

    @ExceptionHandler
    fun handleOversendtKlageNotValidException(
        ex: OversendtKlageNotValidException,
    ): ProblemDetail =
        create(HttpStatus.BAD_REQUEST, ex)

    @ExceptionHandler
    fun handleBehandlingNotFound(
        ex: BehandlingNotFoundException,
    ): ProblemDetail {
        return create(HttpStatus.NOT_FOUND, ex)
    }

    @ExceptionHandler
    fun handleMeldingNotFound(
        ex: MeldingNotFoundException,
    ): ProblemDetail =
        create(HttpStatus.NOT_FOUND, ex)

    @ExceptionHandler
    fun handleGosysOppgaveNotFoundException(
        ex: GosysOppgaveNotFoundException,
    ): ProblemDetail =
        create(HttpStatus.NOT_FOUND, ex)

    @ExceptionHandler
    fun handleGosysOppgaveNotEditableException(
        ex: GosysOppgaveNotEditableException,
    ): ProblemDetail =
        create(HttpStatus.BAD_REQUEST, ex)

    @ExceptionHandler
    fun handlePDLPersonNotFoundException(
        ex: PDLPersonNotFoundException,
    ): ProblemDetail =
        create(HttpStatus.NOT_FOUND, ex)

    @ExceptionHandler
    fun handleEREGOrganizationNotFoundException(
        ex: EREGOrganizationNotFoundException,
    ): ProblemDetail =
        create(HttpStatus.NOT_FOUND, ex)

    @ExceptionHandler
    fun handleValidationException(
        ex: ValidationException,
    ): ProblemDetail =
        create(HttpStatus.BAD_REQUEST, ex)

    @ExceptionHandler
    fun handleBehandlingAvsluttetException(
        ex: BehandlingAvsluttetException,
    ): ProblemDetail =
        create(HttpStatus.FORBIDDEN, ex)

    @ExceptionHandler
    fun handlePreviousBehandlingNotFinalizedException(
        ex: PreviousBehandlingNotFinalizedException,
    ): ProblemDetail =
        create(HttpStatus.FORBIDDEN, ex)

    @ExceptionHandler
    fun handleMissingTilgang(ex: MissingTilgangException): ProblemDetail =
        create(HttpStatus.FORBIDDEN, ex)

    @ExceptionHandler
    fun handleResponseStatusException(ex: WebClientResponseException): ResponseEntity<Any> =
        createProblemForWebClientResponseException(ex)

    @ExceptionHandler
    fun handleDuplicateOversendelse(
        ex: DuplicateOversendelseException,
    ): ProblemDetail =
        create(HttpStatus.CONFLICT, ex)

    @ExceptionHandler
    fun handleDuplicateGosysOppgaveIdException(
        ex: DuplicateGosysOppgaveIdException,
    ): ProblemDetail =
        create(HttpStatus.CONFLICT, ex)

    @ExceptionHandler
    fun handleBehandlingManglerMedunderskriverException(
        ex: BehandlingManglerMedunderskriverException,
    ): ProblemDetail =
        create(HttpStatus.BAD_REQUEST, ex)

    @ExceptionHandler
    fun handleEnhetNotFoundForSaksbehandlerException(
        ex: EnhetNotFoundForSaksbehandlerException,
    ): ProblemDetail =
        create(HttpStatus.INTERNAL_SERVER_ERROR, ex)

    @ExceptionHandler
    fun handleIllegalOperation(
        ex: IllegalOperation,
    ): ProblemDetail =
        create(HttpStatus.BAD_REQUEST, ex)

    @ExceptionHandler
    fun handleSectionedValidationErrorWithDetailsException(
        ex: SectionedValidationErrorWithDetailsException,
    ): ProblemDetail =
        createSectionedValidationProblem(ex)

    @ExceptionHandler
    fun handleDokumentValidationException(
        ex: DokumentValidationException,
    ): ProblemDetail =
        create(HttpStatus.BAD_REQUEST, ex)

    @ExceptionHandler
    fun handleAddressValidationException(
        ex: AddressValidationException,
    ): ProblemDetail =
        create(HttpStatus.BAD_REQUEST, ex)

    @ExceptionHandler
    fun handleDocumentDoesNotExistException(
        ex: DocumentDoesNotExistException,
    ): ProblemDetail =
        create(HttpStatus.NOT_FOUND, ex)

    @ExceptionHandler(IllegalUpdateException::class)
    fun handleIllegalUpdateException(
        ex: IllegalUpdateException,
    ): ProblemDetail =
        create(HttpStatus.BAD_REQUEST, ex)

    @ExceptionHandler
    fun handleSvarbrevPreviewException(
        ex: SvarbrevPreviewException,
    ): ProblemDetail =
        create(HttpStatus.BAD_REQUEST, ex)

    @ExceptionHandler
    fun handleIllegalStateException(
        ex: IllegalStateException,
    ): ProblemDetail =
        create(HttpStatus.BAD_REQUEST, ex)

    @ExceptionHandler
    fun handleSmartDocumentValidationException(
        ex: SmartDocumentValidationException,
    ): ProblemDetail =
        createSmartDocumentValidationProblem(ex)

    @ExceptionHandler
    fun handleNoAccessToDocumentException(
        ex: NoAccessToDocumentException,
    ): ProblemDetail =
        create(HttpStatus.FORBIDDEN, ex)

    @ExceptionHandler
    fun handleUserNotFoundException(
        ex: UserNotFoundException,
    ): ProblemDetail =
        create(HttpStatus.NOT_FOUND, ex)

    @ExceptionHandler
    fun handleGroupNotFoundException(
        ex: GroupNotFoundException,
    ): ProblemDetail =
        create(HttpStatus.NOT_FOUND, ex)

    private fun createSmartDocumentValidationProblem(ex: SmartDocumentValidationException): ProblemDetail {
        logError(
            httpStatus = HttpStatus.BAD_REQUEST,
            errorMessage = ex.message ?: "smartDocument validation error without description",
            exception = ex
        )

        return ProblemDetail.forStatus(HttpStatus.BAD_REQUEST).apply {
            this.title = ex.message
            this.setProperty("documents", ex.errors)
            //TODO remove when FE changed
            this.setProperty("dokumenter", ex.errors)
        }
    }

    private fun createProblemForWebClientResponseException(ex: WebClientResponseException): ResponseEntity<Any> {
        logError(
            httpStatus = HttpStatus.valueOf(ex.statusCode.value()),
            errorMessage = ex.statusText,
            exception = ex
        )

        val contentType = ex.headers.contentType
        if (contentType != null && MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(contentType)) {
            // Pass through as-is when upstream already returned problem+json
            val body = ex.responseBodyAsByteArray
            return ResponseEntity.status(ex.statusCode).contentType(contentType).body(body)
        }

        // Fallback: wrap into a ProblemDetail
        val problemDetail = ProblemDetail.forStatus(ex.statusCode).apply {
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
            exception = ex
        )

        return ProblemDetail.forStatus(HttpStatus.BAD_REQUEST).apply {
            this.title = ex.title
            this.setProperty("sections", ex.sections)
        }
    }

    private fun create(httpStatus: HttpStatus, ex: Exception): ProblemDetail {
        val errorMessage = ex.message ?: "No error message available"

        logError(
            httpStatus = httpStatus,
            errorMessage = errorMessage,
            exception = ex
        )

        return ProblemDetail.forStatus(httpStatus).apply {
            title = errorMessage
        }
    }

    private fun logError(httpStatus: HttpStatus, errorMessage: String, exception: Exception) {
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
