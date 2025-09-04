package no.nav.klage.dokument.service

import no.nav.klage.dokument.api.view.SmartDocumentWriteAccess
import no.nav.klage.dokument.api.view.SmartDocumentsWriteAccessList
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeid
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsSmartdokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.SmartdokumentUnderArbeidAsHoveddokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.SmartdokumentUnderArbeidAsVedlegg
import no.nav.klage.dokument.repositories.DokumentUnderArbeidRepository
import no.nav.klage.dokument.repositories.SmartdokumentUnderArbeidAsHoveddokumentRepository
import no.nav.klage.dokument.repositories.SmartdokumentUnderArbeidAsVedleggRepository
import no.nav.klage.dokument.util.DuaAccessPolicy
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.exceptions.BehandlingNotFoundException
import no.nav.klage.oppgave.exceptions.MissingTilgangException
import no.nav.klage.oppgave.gateway.AzureGateway
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.service.SaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*
import kotlin.jvm.optionals.getOrNull

@Service
@Transactional
class SmartDocumentAccessService(
    private val smartDokumentUnderArbeidAsHoveddokumentRepository: SmartdokumentUnderArbeidAsHoveddokumentRepository,
    private val smartDokumentUnderArbeidAsVedleggRepository: SmartdokumentUnderArbeidAsVedleggRepository,
    private val behandlingRepository: BehandlingRepository,
    private val saksbehandlerService: SaksbehandlerService,
    private val documentPolicyService: DocumentPolicyService,
    private val azureGateway: AzureGateway,
    private val aivenKafkaTemplate: KafkaTemplate<String, String>,
    private val dokumentUnderArbeidRepository: DokumentUnderArbeidRepository,
    @Value("\${SMART_DOCUMENT_WRITE_ACCESS_TOPIC}")
    private val smartDocumentWriteAccessTopic: String,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun getSmartDocumentWriteAccessList(): SmartDocumentsWriteAccessList {
        val saksbehandlerIdentList =
            azureGateway.getGroupMembersNavIdents(saksbehandlerService.getSaksbehandlerRoleId())
        val rolIdentList = azureGateway.getGroupMembersNavIdents(saksbehandlerService.getRolRoleId())

        logger.debug(
            "Found {} saksbehandlere and {} ROL users in AD groups",
            saksbehandlerIdentList.size,
            rolIdentList.size,
        )

        val someDaysAgo = LocalDateTime.now().minusDays(7)

        val hoveddokumenter =
            smartDokumentUnderArbeidAsHoveddokumentRepository.findByMarkertFerdigIsNullAndModifiedAfter(someDaysAgo)
        val vedlegg = smartDokumentUnderArbeidAsVedleggRepository.findByMarkertFerdigIsNullAndModifiedAfter(someDaysAgo)

        logger.debug(
            "Found {} unfinalized hoveddokumenter and {} unfinalized vedlegg modified since {}.",
            hoveddokumenter.size,
            vedlegg.size,
            someDaysAgo,
        )

        val behandlingCache = mutableMapOf<UUID, Behandling>()

        val documentIdToNavIdents = mutableMapOf<UUID, MutableSet<String>>()

        (saksbehandlerIdentList + rolIdentList).forEach { navIdent ->
            hoveddokumenter.forEach { dua ->
                val behandling = behandlingCache.getOrPut(dua.behandlingId) {
                    getBehandling(dua.behandlingId)
                }
                try {
                    logger.debug("Validating smart document access for document {} and navIdent {}", dua.id, navIdent)
                    documentPolicyService.validateDokumentUnderArbeidAction(
                        behandling = behandling,
                        dokumentType = DuaAccessPolicy.DokumentType.SMART_DOCUMENT,
                        parentDokumentType = DuaAccessPolicy.Parent.NONE,
                        documentRole = dua.creatorRole,
                        action = DuaAccessPolicy.Action.WRITE,
                        duaMarkertFerdig = false,
                        isSystemContext = false, //to force actual validation
                        saksbehandler = navIdent,
                    )
                    logger.debug("Adding {} to list of users with access to document {}", navIdent, dua.id)
                    documentIdToNavIdents.getOrPut(dua.id) { mutableSetOf() }.add(navIdent)
                } catch (_: MissingTilgangException) {
                    // Ignore, user does not have access
                } catch (e: Exception) {
                    logger.warn("Unexpected exception:", e)
                }
            }

            vedlegg.forEach { dua ->
                val behandling = behandlingCache.getOrPut(dua.behandlingId) {
                    getBehandling(dua.behandlingId)
                }
                try {
                    logger.debug("Validating smart document access for document {} and navIdent {}", dua.id, navIdent)
                    documentPolicyService.validateDokumentUnderArbeidAction(
                        behandling = behandling,
                        dokumentType = DuaAccessPolicy.DokumentType.SMART_DOCUMENT,
                        parentDokumentType = documentPolicyService.getParentDokumentType(dua.parentId),
                        documentRole = dua.creatorRole,
                        action = DuaAccessPolicy.Action.WRITE,
                        duaMarkertFerdig = false,
                        isSystemContext = false, //to force actual validation
                        saksbehandler = navIdent,
                    )
                    logger.debug("Adding {} to list of users with access to document {}", navIdent, dua.id)
                    documentIdToNavIdents.getOrPut(dua.id) { mutableSetOf() }.add(navIdent)
                } catch (_: MissingTilgangException) {
                    // Ignore, user does not have access
                } catch (e: Exception) {
                    logger.warn("Unexpected exception:", e)
                }
            }
        }
        return SmartDocumentsWriteAccessList(
            smartDocumentWriteAccessList = documentIdToNavIdents.map { (documentId, navIdents) ->
                SmartDocumentWriteAccess(
                    documentId = documentId,
                    navIdents = navIdents.toList(),
                )
            }
        )
    }

    fun getSmartDocumentWriteAccess(documentId: UUID): SmartDocumentWriteAccess {
        logger.debug("Getting smart document access for document {}", documentId)

        val saksbehandlerIdentList =
            azureGateway.getGroupMembersNavIdents(saksbehandlerService.getSaksbehandlerRoleId())
        val rolIdentList = azureGateway.getGroupMembersNavIdents(saksbehandlerService.getRolRoleId())

        logger.debug(
            "Found {} saksbehandlere and {} ROL users in AD groups",
            saksbehandlerIdentList.size,
            rolIdentList.size,
        )

        val smartDocument: DokumentUnderArbeid = smartDokumentUnderArbeidAsHoveddokumentRepository.findById(documentId).getOrNull()
            ?: smartDokumentUnderArbeidAsVedleggRepository.findById(documentId).getOrNull() ?: throw IllegalArgumentException("No smart document found with id $documentId")

        val navIdentsWithAccess = mutableSetOf<String>()

        val behandling = getBehandling(smartDocument.behandlingId)

        (saksbehandlerIdentList + rolIdentList).forEach { navIdent ->
            try {
                logger.debug("Validating smart document access for document {} and navIdent {}", documentId, navIdent)
                documentPolicyService.validateDokumentUnderArbeidAction(
                    behandling = behandling,
                    dokumentType = DuaAccessPolicy.DokumentType.SMART_DOCUMENT,
                    parentDokumentType = documentPolicyService.getParentDokumentType(smartDocument.id),
                    documentRole = smartDocument.creatorRole,
                    action = DuaAccessPolicy.Action.WRITE,
                    duaMarkertFerdig = false,
                    isSystemContext = false, //to force actual validation
                    saksbehandler = navIdent,
                )
                logger.debug("Adding {} to list of users with access to document {}", navIdent, documentId)
                navIdentsWithAccess += navIdent
            } catch (_: MissingTilgangException) {
                // Ignore, user does not have access
            } catch (e: Exception) {
                logger.warn("Unexpected exception:", e)
            }
        }
        return SmartDocumentWriteAccess(
            documentId = documentId,
            navIdents = navIdentsWithAccess.toList(),
        )
    }

    fun getSmartDocumentWriteAccessListForBehandling(
        behandling: Behandling,
    ): SmartDocumentsWriteAccessList {
        val smartDocuments = dokumentUnderArbeidRepository.findByBehandlingId(behandling.id)
            .filterIsInstance<DokumentUnderArbeidAsSmartdokument>()

        if (smartDocuments.isEmpty()) {
            return SmartDocumentsWriteAccessList(emptyList())
        }

        val (vedlegg, hoveddokumenter) = smartDocuments
            .partition { it is SmartdokumentUnderArbeidAsVedlegg }

        val saksbehandlerIdentList =
            azureGateway.getGroupMembersNavIdents(saksbehandlerService.getSaksbehandlerRoleId())
        val rolIdentList = azureGateway.getGroupMembersNavIdents(saksbehandlerService.getRolRoleId())

        logger.debug(
            "Found {} saksbehandlere and {} ROL users in AD groups",
            saksbehandlerIdentList.size,
            rolIdentList.size,
        )

        val documentIdToNavIdents = mutableMapOf<UUID, MutableSet<String>>()

        (saksbehandlerIdentList + rolIdentList).forEach { navIdent ->
            vedlegg.forEach { dua ->
                documentIdToNavIdents.getOrPut(dua.id) { mutableSetOf() }
                dua as SmartdokumentUnderArbeidAsVedlegg
                try {
                    documentPolicyService.validateDokumentUnderArbeidAction(
                        behandling = behandling,
                        dokumentType = DuaAccessPolicy.DokumentType.SMART_DOCUMENT,
                        parentDokumentType = documentPolicyService.getParentDokumentType(dua.parentId),
                        documentRole = dua.creatorRole,
                        action = DuaAccessPolicy.Action.WRITE,
                        duaMarkertFerdig = dua.erMarkertFerdig(),
                        isSystemContext = false, //to force actual validation
                        saksbehandler = navIdent,
                    )
                    documentIdToNavIdents[dua.id]!!.add(navIdent)
                } catch (_: MissingTilgangException) {
                    // Ignore, user does not have access
                } catch (e: Exception) {
                    logger.warn("Unexpected exception:", e)
                }
            }

            hoveddokumenter.forEach { dua ->
                documentIdToNavIdents.getOrPut(dua.id) { mutableSetOf() }
                dua as SmartdokumentUnderArbeidAsHoveddokument
                try {
                    documentPolicyService.validateDokumentUnderArbeidAction(
                        behandling = behandling,
                        dokumentType = DuaAccessPolicy.DokumentType.SMART_DOCUMENT,
                        parentDokumentType = DuaAccessPolicy.Parent.NONE,
                        documentRole = dua.creatorRole,
                        action = DuaAccessPolicy.Action.WRITE,
                        duaMarkertFerdig = dua.erMarkertFerdig(),
                        isSystemContext = false, //to force actual validation
                        saksbehandler = navIdent,
                    )
                    documentIdToNavIdents[dua.id]!!.add(navIdent)
                } catch (_: Exception) {
                    // Ignore, user does not have access
                }
            }

        }
        return SmartDocumentsWriteAccessList(
            smartDocumentWriteAccessList = documentIdToNavIdents.map { (documentId, navIdents) ->
                SmartDocumentWriteAccess(
                    documentId = documentId,
                    navIdents = navIdents.toList(),
                )
            }
        )
    }

    /**
     * Notify frontend (via Kafka) that there may be changes
     * to who has write access to smart documents in this behandling.
     */
    fun notifyFrontendAboutPossibleDocumentRightChanges(behandling: Behandling) {
        logger.debug("Notifying frontend about possible document right changes for behandling {}", behandling.id)
        getSmartDocumentWriteAccessListForBehandling(
            behandling = behandling,
        ).smartDocumentWriteAccessList.forEach { smartDocumentWriteAccess ->
            publishToKafkaTopic(
                key = smartDocumentWriteAccess.documentId.toString(),
                value = if (smartDocumentWriteAccess.navIdents.isNotEmpty()) smartDocumentWriteAccess.navIdents.joinToString(
                    ","
                ) else "",
            )
        }
    }

    /**
     * Notify frontend (via Kafka) that the document is finished or deleted.
     */
    fun notifyFrontendAboutDocumentDone(documentId: UUID) {
        logger.debug("Notifying frontend about document finished or deleted: {}", documentId)
        publishToKafkaTopic(
            key = documentId.toString(),
            value = null,
        )
    }

    private fun publishToKafkaTopic(key: String, value: String?) {
        logger.debug("Sending to Kafka topic: {}", smartDocumentWriteAccessTopic)
        runCatching {
            aivenKafkaTemplate.send(smartDocumentWriteAccessTopic, key, value).get()
            logger.debug("Payload sent to Kafka.")
        }.onFailure {
            logger.error("Could not send payload to Kafka", it)
        }
    }

    private fun getBehandling(behandlingId: UUID): Behandling =
        behandlingRepository.findById(behandlingId)
            .orElseThrow { BehandlingNotFoundException("Behandling med id $behandlingId ikke funnet") }
}