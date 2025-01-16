package no.nav.klage.innsyn.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.innsyn.api.view.InnsynResponse
import no.nav.klage.innsyn.service.InnsynService
import no.nav.klage.oppgave.config.SecurityConfiguration
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.klage.oppgave.util.logMethodDetails
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.Files

@RestController
@Tag(
    name = "kabal-innsyn",
    description = "api for innsyn i brukeres saker"
)

@ProtectedWithClaims(issuer = SecurityConfiguration.TOKEN_X, claimMap = ["acr=Level4"])
@RequestMapping("api/innsyn")
@SecurityRequirement(name = "bearerAuth")
class InnsynController(
    private val innsynService: InnsynService,
    private val tokenUtil: TokenUtil,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    @Operation(
        summary = "Hent en brukers saker",
        description = "Hent en brukers saker, basert på brukerens ident hentet fra token"
    )
    @GetMapping("/saker")
    fun getSaker(): InnsynResponse {
        val identFromToken = tokenUtil.getSubjectFromTokenXToken()
        logMethodDetails(
            methodName = ::getSaker.name,
            innloggetIdent = identFromToken,
            logger = secureLogger,
        )

        return innsynService.getSakerForBruker(fnr = identFromToken)
    }

    @Operation(
        summary = "Hent et gitt dokument fra arkivet",
        description = "Henter alle dokumenter på en journalpost. Må være ført på innlogget bruker."
    )
    @GetMapping("/documents/{journalpostId}")
    fun getDocument(
        @PathVariable("journalpostId") journalpostId: String,
    ): ResponseEntity<Resource> {
        val identFromToken = tokenUtil.getSubjectFromTokenXToken()
        logMethodDetails(
            methodName = ::getDocument.name,
            innloggetIdent = identFromToken,
            logger = secureLogger,
        )

        //TODO: Samkjør dette med metoden som brukes for merging av dokument inne i Kabal
        val (pathToMergedDocument, title) = innsynService.getJournalpostPdf(journalpostId = journalpostId)
        val responseHeaders = HttpHeaders()
        responseHeaders.contentType = MediaType.APPLICATION_PDF
        responseHeaders.add(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"$title.pdf\"")

        return ResponseEntity.ok()
            .headers(responseHeaders)
            .contentLength(pathToMergedDocument.toFile().length())
            .body(
                object : FileSystemResource(pathToMergedDocument) {
                    override fun getInputStream(): InputStream {
                        return object : FileInputStream(pathToMergedDocument.toFile()) {
                            override fun close() {
                                super.close()
                                //Override to do this after client has downloaded file
                                Files.delete(file.toPath())
                            }
                        }
                    }
                })


    }
}