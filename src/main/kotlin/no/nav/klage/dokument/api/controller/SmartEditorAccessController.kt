package no.nav.klage.dokument.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.dokument.api.view.SmartDocumentsWriteAccessList
import no.nav.klage.dokument.service.SmartDocumentService
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.util.getLogger
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
@Tag(name = "kabal-api-smartdokumenter")
@ProtectedWithClaims(issuer = ISSUER_AAD)
@RequestMapping("/smart-document-write-access")
class SmartEditorAccessController(
    private val smartDocumentService: SmartDocumentService,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @Operation(
        summary = "Who has access to recently active smart documents",
        description = "Who has access to recently active smart documents"
    )
    @GetMapping
    fun getAccess(): SmartDocumentsWriteAccessList {
        return smartDocumentService.getSmartDocumentWriteAccessList()
    }
}