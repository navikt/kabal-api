package no.nav.klage.oppgave.config.problem

import no.nav.klage.dokument.exceptions.DokumentValidationException
import no.nav.klage.dokument.exceptions.JsonToPdfValidationException
import no.nav.klage.oppgave.exceptions.*
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice
class ProblemHandlingControllerAdvice : ResponseEntityExceptionHandler() {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val secureLogger = getSecureLogger()
    }

//    @ExceptionHandler
//    fun catchISE(
//        ex: IllegalStateException,
//        request: NativeWebRequest
//    ): ProblemDetail {
//        logger.debug("catching IllegalStateException", ex)
//
//        if (ex.cause is SizeLimitExceededException) {
//            return create(HttpStatus.PAYLOAD_TOO_LARGE, ex)
//        }
//
//        try {
//            val nativeRequest = request.nativeRequest
//
//            if (nativeRequest is HttpServletRequest) {
//                logger.debug("dispatcherType = " + nativeRequest.dispatcherType?.name)
//
//                logger.debug("path = " + nativeRequest.pathInfo)
//                logger.debug("requestURI = " + nativeRequest.requestURI)
//
//                if (nativeRequest.isAsyncStarted) {
//                    logger.debug("asyncContext = " + nativeRequest.asyncContext)
//                }
//            }
//        } catch (e: Exception) {
//            logger.warn("problems with handling ISE", e)
//        }
//
//        return create(HttpStatus.INTERNAL_SERVER_ERROR, ex)
//    }
//
//    @ExceptionHandler
//    fun handleSizeLimitExceededException(
//        ex: SizeLimitExceededException,
//        request: NativeWebRequest
//    ): ProblemDetail =
//        create(HttpStatus.PAYLOAD_TOO_LARGE, ex)

    @ExceptionHandler
    fun handleFeilregistreringException(
        ex: FeilregistreringException,
        request: NativeWebRequest
    ): ProblemDetail =
        create(HttpStatus.BAD_REQUEST, ex)

    @ExceptionHandler
    fun handleOversendtKlageNotValidException(
        ex: OversendtKlageNotValidException,
        request: NativeWebRequest
    ): ProblemDetail =
        create(HttpStatus.BAD_REQUEST, ex)

    @ExceptionHandler
    fun handleBehandlingNotFound(
        ex: BehandlingNotFoundException,
        request: NativeWebRequest
    ): ProblemDetail =
        create(HttpStatus.NOT_FOUND, ex)

    @ExceptionHandler
    fun handleMeldingNotFound(
        ex: MeldingNotFoundException,
        request: NativeWebRequest
    ): ProblemDetail =
        create(HttpStatus.NOT_FOUND, ex)

    @ExceptionHandler
    fun handlePDLPersonNotFoundException(
        ex: PDLPersonNotFoundException,
        request: NativeWebRequest
    ): ProblemDetail =
        create(HttpStatus.NOT_FOUND, ex)

    @ExceptionHandler
    fun handleEREGOrganizationNotFoundException(
        ex: EREGOrganizationNotFoundException,
        request: NativeWebRequest
    ): ProblemDetail =
        create(HttpStatus.NOT_FOUND, ex)

    @ExceptionHandler
    fun handleValidationException(
        ex: ValidationException,
        request: NativeWebRequest
    ): ProblemDetail =
        create(HttpStatus.BAD_REQUEST, ex)

    @ExceptionHandler
    fun handleBehandlingAvsluttetException(
        ex: BehandlingAvsluttetException,
        request: NativeWebRequest
    ): ProblemDetail =
        create(HttpStatus.FORBIDDEN, ex)

    @ExceptionHandler
    fun handlePreviousBehandlingNotFinalizedException(
        ex: PreviousBehandlingNotFinalizedException,
        request: NativeWebRequest
    ): ProblemDetail =
        create(HttpStatus.FORBIDDEN, ex)

    @ExceptionHandler
    fun handleMissingTilgang(ex: MissingTilgangException, request: NativeWebRequest): ProblemDetail =
        create(HttpStatus.FORBIDDEN, ex)

    @ExceptionHandler
    fun handleResponseStatusException(
        ex: WebClientResponseException,
        request: NativeWebRequest
    ): ProblemDetail =
        createProblemForWebClientResponseException(ex)

    @ExceptionHandler
    fun handleDuplicateOversendelse(
        ex: DuplicateOversendelseException,
        request: NativeWebRequest
    ): ProblemDetail =
        create(HttpStatus.CONFLICT, ex)

    @ExceptionHandler
    fun handleBehandlingManglerMedunderskriverException(
        ex: BehandlingManglerMedunderskriverException,
        request: NativeWebRequest
    ): ProblemDetail =
        create(HttpStatus.BAD_REQUEST, ex)

    @ExceptionHandler
    fun handleBehandlingFinalizedException(
        ex: BehandlingFinalizedException,
        request: NativeWebRequest
    ): ProblemDetail =
        create(HttpStatus.BAD_REQUEST, ex)

    @ExceptionHandler
    fun handleEnhetNotFoundForSaksbehandlerException(
        ex: EnhetNotFoundForSaksbehandlerException,
        request: NativeWebRequest
    ): ProblemDetail =
        create(HttpStatus.INTERNAL_SERVER_ERROR, ex)

    @ExceptionHandler
    fun handleIllegalOperation(
        ex: IllegalOperation,
        request: NativeWebRequest
    ): ProblemDetail =
        create(HttpStatus.BAD_REQUEST, ex)

    @ExceptionHandler
    fun handleSectionedValidationErrorWithDetailsException(
        ex: SectionedValidationErrorWithDetailsException,
        request: NativeWebRequest
    ): ProblemDetail =
        createSectionedValidationProblem(ex)

    @ExceptionHandler
    fun handleDokumentValidationException(
        ex: DokumentValidationException,
        request: NativeWebRequest
    ): ProblemDetail =
        create(HttpStatus.BAD_REQUEST, ex)

    @ExceptionHandler(IllegalUpdateException::class)
    fun handleIllegalUpdateException(
        ex: IllegalUpdateException,
        request: NativeWebRequest
    ): ProblemDetail =
        create(HttpStatus.BAD_REQUEST, ex)

    @ExceptionHandler
    fun handleJsonToPdfValidationException(
        ex: JsonToPdfValidationException,
        request: NativeWebRequest
    ): ProblemDetail =
        createJsonDocumentValidationProblem(ex)

    private fun createJsonDocumentValidationProblem(ex: JsonToPdfValidationException): ProblemDetail {
        logError(
            httpStatus = HttpStatus.BAD_REQUEST,
            errorMessage = ex.message ?: "json validation error without description",
            exception = ex
        )

        return ProblemDetail.forStatus(HttpStatus.BAD_REQUEST).apply {
            this.title = ex.message
            this.setProperty("dokumenter", ex.errors)
        }
    }

    private fun createProblemForWebClientResponseException(ex: WebClientResponseException): ProblemDetail {
        logError(
            httpStatus = HttpStatus.valueOf(ex.statusCode.value()),
            errorMessage = ex.statusText,
            exception = ex
        )

        return ProblemDetail.forStatus(ex.statusCode).apply {
            title = ex.statusText
            detail = ex.responseBodyAsString
        }
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
                secureLogger.error("Exception thrown to client: ${httpStatus.reasonPhrase}, $errorMessage", exception)
            }

            else -> {
                secureLogger.warn("Exception thrown to client: ${httpStatus.reasonPhrase}, $errorMessage", exception)
            }
        }
    }
}
