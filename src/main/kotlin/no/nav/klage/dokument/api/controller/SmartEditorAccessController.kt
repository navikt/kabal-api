package no.nav.klage.dokument.api.controller

import no.nav.klage.dokument.api.view.SmartDocumentsWriteAccessList
import no.nav.klage.dokument.service.SmartDocumentService
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.util.getLogger
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
@ProtectedWithClaims(issuer = ISSUER_AAD)
@RequestMapping("/smart-document-write-access")
class SmartEditorAccessController(
    private val smartDocumentService: SmartDocumentService,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @GetMapping
    fun getAccess(): SmartDocumentsWriteAccessList {
        return smartDocumentService.getSmartDocumentWriteAccessList()
    }
}