package no.nav.klage.oppgave.api.controller

import io.swagger.annotations.Api
import no.nav.klage.oppgave.api.mapper.KlagebehandlingMapper
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.domain.AuditLogEvent
import no.nav.klage.oppgave.exceptions.BehandlingsidWrongFormatException
import no.nav.klage.oppgave.repositories.InnloggetSaksbehandlerRepository
import no.nav.klage.oppgave.service.KlagebehandlingEditableFieldsFacade
import no.nav.klage.oppgave.service.KlagebehandlingService
import no.nav.klage.oppgave.util.AuditLogger
import no.nav.klage.oppgave.util.getLogger
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@Api(tags = ["kabal-api"])
@ProtectedWithClaims(issuer = ISSUER_AAD)
@RequestMapping("/klagebehandlinger")
class KlagebehandlingDetaljerController(
    private val klagebehandlingService: KlagebehandlingService,
    private val klagebehandlingMapper: KlagebehandlingMapper,
    private val innloggetSaksbehandlerRepository: InnloggetSaksbehandlerRepository,
    private val auditLogger: AuditLogger,
    private val editableFieldsFacade: KlagebehandlingEditableFieldsFacade
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @GetMapping("/{id}/detaljer")
    fun getKlagebehandlingDetaljer(
        @PathVariable("id") klagebehandlingId: String
    ): KlagebehandlingDetaljerView {
        logMethodDetails("getKlagebehandlingDetaljer", klagebehandlingId)
        return klagebehandlingMapper.mapKlagebehandlingToKlagebehandlingDetaljerView(
            klagebehandlingService.getKlagebehandling(klagebehandlingId.toUUIDOrException())
        ).also {
            auditLogger.log(
                AuditLogEvent(
                    navIdent = innloggetSaksbehandlerRepository.getInnloggetIdent(),
                    personFnr = it.sakenGjelderFoedselsnummer
                )
            )
        }
    }

    @PutMapping("/{id}/detaljer/editerbare")
    fun putEditableFields(
        @PathVariable("id") klagebehandlingId: String,
        @RequestBody input: KlagebehandlingEditableFieldsInput
    ): KlagebehandlingEditedView {
        logMethodDetails("putEditableFields", klagebehandlingId)
        return klagebehandlingMapper.mapKlagebehandlingToKlagebehandlingEditableFieldsView(
            editableFieldsFacade.updateEditableFields(
                klagebehandlingId.toUUIDOrException(),
                input,
                innloggetSaksbehandlerRepository.getInnloggetIdent()
            )
        )
    }

    @PutMapping("/{id}/detaljer/medunderskriverident")
    fun putMedunderskriverident(
        @PathVariable("id") klagebehandlingId: String,
        @RequestBody input: KlagebehandlingMedunderskriveridentInput
    ): SendtMedunderskriverView {
        logMethodDetails("putMedunderskriverident", klagebehandlingId)
        val klagebehandling = klagebehandlingService.setMedunderskriverIdent(
            klagebehandlingId.toUUIDOrException(),
            input.klagebehandlingVersjon,
            input.medunderskriverident,
            innloggetSaksbehandlerRepository.getInnloggetIdent()
        )
        return SendtMedunderskriverView(
            klagebehandling.versjon,
            klagebehandling.modified,
            klagebehandling.medunderskriver!!.tidspunkt.toLocalDate()
        )
    }

    private fun String.toUUIDOrException() =
        try {
            UUID.fromString(this)
        } catch (e: Exception) {
            logger.error("KlagebehandlingId could not be parsed as an UUID", e)
            throw BehandlingsidWrongFormatException("KlagebehandlingId could not be parsed as an UUID")
        }

    private fun logMethodDetails(methodName: String, klagebehandlingId: String) {
        logger.debug(
            "{} is requested by ident {} for klagebehandlingId {}",
            methodName,
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            klagebehandlingId
        )
    }
}