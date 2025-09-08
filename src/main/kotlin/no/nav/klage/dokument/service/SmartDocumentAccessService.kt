package no.nav.klage.dokument.service

import no.nav.klage.dokument.api.view.SmartDocumentWriteAccess
import no.nav.klage.dokument.api.view.SmartDocumentsWriteAccessList
import no.nav.klage.dokument.domain.SmartDocumentAccessBehandlingEvent
import no.nav.klage.dokument.domain.SmartDocumentAccessDocumentEvent
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeid
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsSmartdokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.SmartdokumentUnderArbeidAsHoveddokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.SmartdokumentUnderArbeidAsVedlegg
import no.nav.klage.dokument.exceptions.DokumentValidationException
import no.nav.klage.dokument.repositories.DokumentUnderArbeidRepository
import no.nav.klage.dokument.repositories.SmartdokumentUnderArbeidAsHoveddokumentRepository
import no.nav.klage.dokument.repositories.SmartdokumentUnderArbeidAsVedleggRepository
import no.nav.klage.dokument.util.DuaAccessPolicy
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.exceptions.BehandlingNotFoundException
import no.nav.klage.oppgave.exceptions.MissingDUARuleException
import no.nav.klage.oppgave.exceptions.MissingTilgangException
import no.nav.klage.oppgave.gateway.AzureGateway
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.service.SaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.ourJacksonObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.LocalDateTime
import java.util.*
import kotlin.jvm.optionals.getOrNull

@Service
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
        private val objectMapper = ourJacksonObjectMapper()
    }

    /**
     * Used as initial load of who has write access to unfinalized smart documents.
     * Not crucial to use by FE/BFF.
     */
    fun getSmartDocumentWriteAccessList(): SmartDocumentsWriteAccessList {
        val (saksbehandlerIdentList, rolIdentList) = getUsers()

        val someDaysAgo = LocalDateTime.now().minusDays(7)

        val hoveddokumentList =
            smartDokumentUnderArbeidAsHoveddokumentRepository.findByMarkertFerdigIsNullAndModifiedAfter(someDaysAgo)
        val vedleggList =
            smartDokumentUnderArbeidAsVedleggRepository.findByMarkertFerdigIsNullAndModifiedAfter(someDaysAgo)

        logger.debug(
            "Found {} unfinalized hoveddokumenter and {} unfinalized vedlegg modified since {}.",
            hoveddokumentList.size,
            vedleggList.size,
            someDaysAgo,
        )

        val behandlingCache = mutableMapOf<UUID, Behandling>()

        val documentIdToNavIdents = mutableMapOf<UUID, MutableSet<String>>()

        (saksbehandlerIdentList + rolIdentList).forEach { (navIdent, role) ->
            hoveddokumentList.forEach { dua ->
                val behandling = behandlingCache.getOrPut(dua.behandlingId) {
                    getBehandling(dua.behandlingId)
                }
                try {
                    logger.debug("Validating smart document access for document {} and navIdent {}", dua.id, navIdent)
                    documentPolicyService.validateDokumentUnderArbeidAction(
                        behandling = behandling,
                        dokumentType = documentPolicyService.getDokumentType(dua.id),
                        parentDokumentType = DuaAccessPolicy.Parent.NONE,
                        documentRole = dua.creatorRole,
                        action = DuaAccessPolicy.Action.WRITE,
                        duaMarkertFerdig = false,
                        isSystemContext = false, //to force actual validation
                        saksbehandler = navIdent,
                        isRol = role == DuaAccessPolicy.User.ROL,
                        isSaksbehandler = role == DuaAccessPolicy.User.SAKSBEHANDLER,
                    )
                    logger.debug("Adding {} to list of users with access to document {}", navIdent, dua.id)
                    documentIdToNavIdents.getOrPut(dua.id) { mutableSetOf() }.add(navIdent)
                } catch (_: MissingTilgangException) {
                    // Ignore, user does not have access
                } catch (_: MissingDUARuleException) {
                    // Ignore, user does not have access
                } catch (_: DokumentValidationException) {
                    // Ignore, user does not have access
                } catch (e: Exception) {
                    logger.warn("Unexpected exception when validating smart document:", e)
                }
            }

            vedleggList.forEach { dua ->
                val behandling = behandlingCache.getOrPut(dua.behandlingId) {
                    getBehandling(dua.behandlingId)
                }
                try {
                    logger.debug("Validating smart document access for document {} and navIdent {}", dua.id, navIdent)
                    documentPolicyService.validateDokumentUnderArbeidAction(
                        behandling = behandling,
                        dokumentType = documentPolicyService.getDokumentType(dua.id),
                        parentDokumentType = documentPolicyService.getParentDokumentType(dua.parentId),
                        documentRole = dua.creatorRole,
                        action = DuaAccessPolicy.Action.WRITE,
                        duaMarkertFerdig = false,
                        isSystemContext = false, //to force actual validation
                        saksbehandler = navIdent,
                        isRol = role == DuaAccessPolicy.User.ROL,
                        isSaksbehandler = role == DuaAccessPolicy.User.SAKSBEHANDLER,
                    )
                    logger.debug("Adding {} to list of users with access to document {}", navIdent, dua.id)
                    documentIdToNavIdents.getOrPut(dua.id) { mutableSetOf() }.add(navIdent)
                } catch (_: MissingTilgangException) {
                    // Ignore, user does not have access
                } catch (_: MissingDUARuleException) {
                    // Ignore, user does not have access
                } catch (_: DokumentValidationException) {
                    // Ignore, user does not have access
                } catch (e: Exception) {
                    logger.warn("Unexpected exception when validating smart document:", e)
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
     * Checking a specific document
     */
    fun getSmartDocumentWriteAccess(documentId: UUID): SmartDocumentWriteAccess {
        logger.debug("Getting smart document access for document {}", documentId)

        val (saksbehandlerIdentList, rolIdentList) = getUsers()

        val smartDocument: DokumentUnderArbeid =
            smartDokumentUnderArbeidAsHoveddokumentRepository.findById(documentId).getOrNull()
                ?: smartDokumentUnderArbeidAsVedleggRepository.findById(documentId).getOrNull()
                ?: throw IllegalArgumentException("No smart document found with id $documentId")

        val navIdentsWithAccess = mutableSetOf<String>()

        val behandling = getBehandling(smartDocument.behandlingId)

        (saksbehandlerIdentList + rolIdentList).forEach { (navIdent, role) ->
            try {
                logger.debug("Validating smart document access for document {} and navIdent {}", documentId, navIdent)
                documentPolicyService.validateDokumentUnderArbeidAction(
                    behandling = behandling,
                    dokumentType = documentPolicyService.getDokumentType(smartDocument.id),
                    parentDokumentType = if (smartDocument is SmartdokumentUnderArbeidAsVedlegg) documentPolicyService.getParentDokumentType(
                        smartDocument.parentId
                    ) else DuaAccessPolicy.Parent.NONE,
                    documentRole = smartDocument.creatorRole,
                    action = DuaAccessPolicy.Action.WRITE,
                    duaMarkertFerdig = false,
                    isSystemContext = false, //to force actual validation
                    saksbehandler = navIdent,
                    isRol = role == DuaAccessPolicy.User.ROL,
                    isSaksbehandler = role == DuaAccessPolicy.User.SAKSBEHANDLER,
                )
                logger.debug("Adding {} to list of users with access to document {}", navIdent, documentId)
                navIdentsWithAccess += navIdent
            } catch (_: MissingTilgangException) {
                // Ignore, user does not have access
            } catch (_: MissingDUARuleException) {
                // Ignore, user does not have access
            } catch (_: DokumentValidationException) {
                // Ignore, user does not have access
            } catch (e: Exception) {
                logger.warn("Unexpected exception when validating smart document:", e)
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

        val (saksbehandlerIdentList, rolIdentList) = getUsers()

        val documentIdToNavIdents = mutableMapOf<UUID, MutableSet<String>>()

        (saksbehandlerIdentList + rolIdentList).forEach { (navIdent, role) ->
            vedlegg.forEach { dua ->
                documentIdToNavIdents.getOrPut(dua.id) { mutableSetOf() }
                dua as SmartdokumentUnderArbeidAsVedlegg
                try {
                    documentPolicyService.validateDokumentUnderArbeidAction(
                        behandling = behandling,
                        dokumentType = documentPolicyService.getDokumentType(dua.id),
                        parentDokumentType = documentPolicyService.getParentDokumentType(dua.parentId),
                        documentRole = dua.creatorRole,
                        action = DuaAccessPolicy.Action.WRITE,
                        duaMarkertFerdig = dua.erMarkertFerdig(),
                        isSystemContext = false, //to force actual validation
                        saksbehandler = navIdent,
                        isRol = role == DuaAccessPolicy.User.ROL,
                        isSaksbehandler = role == DuaAccessPolicy.User.SAKSBEHANDLER,
                    )
                    documentIdToNavIdents[dua.id]!!.add(navIdent)
                } catch (_: MissingTilgangException) {
                    // Ignore, user does not have access
                } catch (_: MissingDUARuleException) {
                    // Ignore, user does not have access
                } catch (_: DokumentValidationException) {
                    // Ignore, user does not have access
                } catch (e: Exception) {
                    logger.warn("Unexpected exception when validating smart document:", e)
                }
            }

            hoveddokumenter.forEach { dua ->
                documentIdToNavIdents.getOrPut(dua.id) { mutableSetOf() }
                dua as SmartdokumentUnderArbeidAsHoveddokument
                try {
                    documentPolicyService.validateDokumentUnderArbeidAction(
                        behandling = behandling,
                        dokumentType = documentPolicyService.getDokumentType(dua.id),
                        parentDokumentType = DuaAccessPolicy.Parent.NONE,
                        documentRole = dua.creatorRole,
                        action = DuaAccessPolicy.Action.WRITE,
                        duaMarkertFerdig = dua.erMarkertFerdig(),
                        isSystemContext = false, //to force actual validation
                        saksbehandler = navIdent,
                        isRol = role == DuaAccessPolicy.User.ROL,
                        isSaksbehandler = role == DuaAccessPolicy.User.SAKSBEHANDLER,
                    )
                    documentIdToNavIdents[dua.id]!!.add(navIdent)
                } catch (_: MissingTilgangException) {
                    // Ignore, user does not have access
                } catch (_: MissingDUARuleException) {
                    // Ignore, user does not have access
                } catch (_: DokumentValidationException) {
                    // Ignore, user does not have access
                } catch (e: Exception) {
                    logger.warn("Unexpected exception when validating smart document:", e)
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

    private fun getUsers(): Pair<List<Pair<String, DuaAccessPolicy.User>>, List<Pair<String, DuaAccessPolicy.User>>> {
        val saksbehandlerIdentList =
            azureGateway.getGroupMembersNavIdents(saksbehandlerService.getSaksbehandlerRoleId())
                .map { it to DuaAccessPolicy.User.SAKSBEHANDLER }
        val rolIdentList = azureGateway.getGroupMembersNavIdents(saksbehandlerService.getRolRoleId())
            .map { it to DuaAccessPolicy.User.ROL }

        logger.debug(
            "Found {} saksbehandlere and {} ROL users in AD groups",
            saksbehandlerIdentList.size,
            rolIdentList.size,
        )

        return Pair(saksbehandlerIdentList, rolIdentList)
    }

    /**
     * Notify frontend (via Kafka) that there may be changes
     * to who has write access to smart documents in this behandling.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun notifyFrontendAboutPossibleDocumentRightChanges(smartDocumentAccessBehandlingEvent: SmartDocumentAccessBehandlingEvent) {
        logger.debug(
            "Notifying frontend about possible document right changes for behandling {}",
            smartDocumentAccessBehandlingEvent.behandling.id
        )
        getSmartDocumentWriteAccessListForBehandling(
            behandling = smartDocumentAccessBehandlingEvent.behandling,
        ).smartDocumentWriteAccessList.forEach { smartDocumentWriteAccess ->
            publishToKafkaTopic(
                key = smartDocumentWriteAccess.documentId.toString(),
                json = objectMapper.writeValueAsString(smartDocumentWriteAccess.navIdents),
            )
        }
    }

    /**
     * Notify frontend (via Kafka) that the smart document is finished or deleted.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun notifyFrontendAboutDocumentDone(smartDocumentAccessDocumentEvent: SmartDocumentAccessDocumentEvent) {
        logger.debug(
            "Notifying frontend about document finished or deleted: {}",
            smartDocumentAccessDocumentEvent.duaId
        )
        publishToKafkaTopic(
            key = smartDocumentAccessDocumentEvent.duaId.toString(),
            json = null,
        )
    }

    private fun publishToKafkaTopic(key: String, json: String?) {
        logger.debug("Sending to Kafka topic: {}", smartDocumentWriteAccessTopic)
        runCatching {
            aivenKafkaTemplate.send(smartDocumentWriteAccessTopic, key, json).get()
            logger.debug("Payload sent to Kafka.")
        }.onFailure {
            logger.error("Could not send payload to Kafka", it)
        }
    }

    private fun getBehandling(behandlingId: UUID): Behandling =
        behandlingRepository.findById(behandlingId)
            .orElseThrow { BehandlingNotFoundException("Behandling med id $behandlingId ikke funnet") }
}