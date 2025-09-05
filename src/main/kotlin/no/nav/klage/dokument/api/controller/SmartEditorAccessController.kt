package no.nav.klage.dokument.api.controller

import no.nav.klage.dokument.api.view.SmartDocumentWriteAccess
import no.nav.klage.dokument.api.view.SmartDocumentsWriteAccessList
import no.nav.klage.dokument.service.SmartDocumentAccessService
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.util.getLogger
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*


@RestController
@ProtectedWithClaims(issuer = ISSUER_AAD)
@RequestMapping("/smart-document-write-access")
class SmartEditorAccessController(
    private val smartDocumentAccessService: SmartDocumentAccessService,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @GetMapping
    fun getWriteAccessForAll(): SmartDocumentsWriteAccessList {
        return smartDocumentAccessService.getSmartDocumentWriteAccessList()
    }

    @GetMapping("/{documentId}")
    fun getWriteAccess(
        @PathVariable documentId: UUID,
        ): SmartDocumentWriteAccess {
        return smartDocumentAccessService.getSmartDocumentWriteAccess(documentId)
    }
}