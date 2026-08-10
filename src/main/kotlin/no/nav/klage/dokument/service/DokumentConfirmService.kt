package no.nav.klage.dokument.service

import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import jakarta.persistence.PersistenceContext
import no.nav.klage.dokument.clients.klagefileapi.FileApiClient
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentStatus
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeid
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsOpplastet
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsVedlegg
import no.nav.klage.dokument.exceptions.ConversionConflictException
import no.nav.klage.dokument.exceptions.DocumentDoesNotExistException
import no.nav.klage.dokument.exceptions.DokumentValidationException
import no.nav.klage.dokument.service.DokumentConfirmService.Companion.FOLLOW_STALL_TIMEOUT_MILLIS
import no.nav.klage.dokument.util.DuaAccessPolicy
import no.nav.klage.oppgave.exceptions.AttachmentCouldNotBeConvertedException
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.util.getLogger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/**
 * Drives an uploaded document from [DokumentStatus.UPLOADING] to a terminal status, reporting every
 * state change to the caller through `emit` as it happens.
 *
 * The file itself is uploaded straight to Google by the client, and virus scanned and (if needed)
 * converted to PDF by kabal-file-api, so all this does is tell kabal-file-api what to do and keep
 * track of where the document is.
 *
 * The whole thing is idempotent and resumable: every status change is committed on its own, so a
 * client that loses the connection (or a request that dies) can simply call confirm again and the
 * machine picks up from the last persisted status. Every individual step is safe to repeat.
 */
@Service
class DokumentConfirmService(
    private val dokumentStateService: DokumentStateService,
    private val fileApiClient: FileApiClient,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)

        //Enforced on the FINAL (converted) file, since that is what gets journalført to Joark via
        //kabal-document. 512 MB.
        private const val MAX_SIZE = 536870912L

        private const val FOLLOW_POLL_INTERVAL_MILLIS = 500L

        //How long we follow another request without seeing any progress before taking over the work
        //ourselves. Covers the case where the request that was driving the document died.
        private const val FOLLOW_STALL_TIMEOUT_MILLIS = 60 * 1000L

        //Total time a single confirm request is willing to spend before giving up.
        private const val TOTAL_TIMEOUT_MILLIS = 10 * 60 * 1000L

        //Only coordinates requests within one pod. Across pods the persisted status plus the fact
        //that every step is repeatable is what keeps things correct.
        private val locks = ConcurrentHashMap<UUID, ReentrantLock>()
    }

    /**
     * Blocks until the document reaches a terminal status, calling [emit] with the current state and
     * with every state change along the way.
     *
     * Errors that happen before anything has been emitted are thrown, so the caller can turn them
     * into a normal error response. Once the first state has been emitted, failures are reported as
     * terminal statuses instead.
     */
    fun confirmDokument(behandlingId: UUID, dokumentId: UUID, emit: (DokumentState) -> Unit) {
        dokumentStateService.validateWriteAccess(behandlingId = behandlingId, dokumentId = dokumentId)

        val deadline = System.currentTimeMillis() + TOTAL_TIMEOUT_MILLIS
        var lastEmitted: DokumentState? = null

        val emitOnChange: (DokumentState) -> Unit = { state ->
            if (state != lastEmitted) {
                emit(state)
                lastEmitted = state
            }
        }

        while (System.currentTimeMillis() < deadline) {
            val state = dokumentStateService.getState(behandlingId = behandlingId, dokumentId = dokumentId)

            if (state.status.isTerminal()) {
                emitOnChange(state)
                return
            }

            val lock = acquireLock(dokumentId)

            if (lock != null) {
                try {
                    //Not necessarily finished when this returns: a document that turned out to have
                    //changed since it was scanned is sent back to the scan, and the next round of the
                    //loop picks it up again.
                    drive(behandlingId = behandlingId, dokumentId = dokumentId, emit = emitOnChange)
                    continue
                } finally {
                    //Removed while still holding it, so a request that captured this lock cannot end
                    //up driving the same document as a request that creates the replacement.
                    locks.remove(dokumentId, lock)
                    lock.unlock()
                }
            }

            //Someone else is already driving this document forward. Tag along instead of doing the
            //work twice, but take over if they stop making progress.
            if (followProgress(behandlingId = behandlingId, dokumentId = dokumentId, emit = emitOnChange)) {
                return
            }

            logger.warn("No progress on document {} while following, taking over.", dokumentId)
        }

        logger.warn("Gave up confirming document {} after {} ms.", dokumentId, TOTAL_TIMEOUT_MILLIS)
        throw DokumentValidationException("Dokumentet ble ikke ferdig behandlet. Prøv igjen.")
    }

    /**
     * Returns the lock if it was acquired, or null if another request in this pod holds it. Retries
     * on the race where the lock we found in the map was removed just before we got it.
     */
    private fun acquireLock(dokumentId: UUID): ReentrantLock? {
        while (true) {
            val lock = locks.computeIfAbsent(dokumentId) { ReentrantLock() }

            if (!lock.tryLock()) {
                return null
            }

            if (locks[dokumentId] === lock) {
                return lock
            }

            //The owner removed this lock from the map on its way out, so it no longer guards anything.
            lock.unlock()
        }
    }

    private fun drive(behandlingId: UUID, dokumentId: UUID, emit: (DokumentState) -> Unit) {
        var state = dokumentStateService.getState(behandlingId = behandlingId, dokumentId = dokumentId)

        //Nothing has been emitted yet, so a missing upload can still be reported as a normal error.
        if (state.status == DokumentStatus.UPLOADING) {
            val metadata = fileApiClient.getDocumentMetadata(state.mellomlagerId)
            if (!metadata.exists) {
                throw DokumentValidationException("Fant ikke opplastet dokument. Ble opplastingen fullført?")
            }
            state = dokumentStateService.setStatus(
                behandlingId = behandlingId,
                dokumentId = dokumentId,
                status = DokumentStatus.UPLOADED,
                size = metadata.size,
            )
        }

        //Always let the client know where we are before doing any more work, so a resumed stream is
        //immediately useful.
        emit(state)

        //A document left in CONVERTING without a scanned generation cannot be converted safely, so
        //it goes through the scan again.
        val needsScan = state.status in setOf(DokumentStatus.UPLOADED, DokumentStatus.VIRUS_SCANNING) ||
                (state.status == DokumentStatus.CONVERTING && state.scannedGeneration == null)

        if (needsScan) {
            state = dokumentStateService.setStatus(
                behandlingId = behandlingId,
                dokumentId = dokumentId,
                status = DokumentStatus.VIRUS_SCANNING,
            )
            emit(state)

            val scanResult = try {
                fileApiClient.scanDocument(state.mellomlagerId)
            } catch (e: AttachmentCouldNotBeConvertedException) {
                emit(fail(behandlingId, dokumentId, DokumentStatus.CONVERSION_FAILED))
                return
            }

            if (scanResult.hasVirus) {
                logger.warn("Virus found in uploaded document {}, deleting it.", dokumentId)
                fileApiClient.deleteDocument(state.mellomlagerId)
                emit(fail(behandlingId, dokumentId, DokumentStatus.VIRUS_FOUND))
                return
            }

            if (scanResult.requiresConversion) {
                state = dokumentStateService.setScanned(
                    behandlingId = behandlingId,
                    dokumentId = dokumentId,
                    scannedGeneration = scanResult.generation,
                    //The size before conversion. Better than nothing while the conversion is running,
                    //and it is replaced with the size of the PDF when the document is done.
                    size = scanResult.size,
                )
            } else {
                val size = scanResult.size ?: 0L
                if (isTooLarge(size = size, dokumentId = dokumentId, mellomlagerId = state.mellomlagerId)) {
                    emit(fail(behandlingId, dokumentId, DokumentStatus.CONVERSION_FAILED))
                    return
                }
                state = dokumentStateService.setDone(
                    behandlingId = behandlingId,
                    dokumentId = dokumentId,
                    size = size,
                )
            }
            emit(state)
        }

        if (state.status == DokumentStatus.CONVERTING) {
            val convertResult = try {
                fileApiClient.convertDocument(
                    id = state.mellomlagerId,
                    scannedGeneration = state.scannedGeneration!!,
                )
            } catch (e: ConversionConflictException) {
                //The file is no longer the one we scanned, so start over from the scan. The caller
                //keeps driving until the document is terminal or the deadline is reached.
                logger.warn("Document {} changed since it was scanned, scanning it again.", dokumentId)
                emit(
                    dokumentStateService.resetForRescan(
                        behandlingId = behandlingId,
                        dokumentId = dokumentId,
                    )
                )
                return
            } catch (e: AttachmentCouldNotBeConvertedException) {
                emit(fail(behandlingId, dokumentId, DokumentStatus.CONVERSION_FAILED))
                return
            }

            val size = convertResult.size ?: 0L
            if (isTooLarge(size = size, dokumentId = dokumentId, mellomlagerId = state.mellomlagerId)) {
                emit(fail(behandlingId, dokumentId, DokumentStatus.CONVERSION_FAILED))
                return
            }

            state = dokumentStateService.setDone(
                behandlingId = behandlingId,
                dokumentId = dokumentId,
                size = size,
            )
            emit(state)
        }
    }

    private fun isTooLarge(size: Long, dokumentId: UUID, mellomlagerId: String): Boolean {
        if (size <= MAX_SIZE) {
            return false
        }
        logger.warn("Document {} is too large ({} bytes), deleting it.", dokumentId, size)
        fileApiClient.deleteDocument(mellomlagerId)
        return true
    }

    private fun fail(behandlingId: UUID, dokumentId: UUID, status: DokumentStatus): DokumentState =
        dokumentStateService.setStatus(
            behandlingId = behandlingId,
            dokumentId = dokumentId,
            status = status,
        )

    /**
     * Follows the progress made by another request. Returns true if the document reached a terminal
     * status, and false if nothing happened for [FOLLOW_STALL_TIMEOUT_MILLIS], meaning the caller
     * should take over the work.
     */
    private fun followProgress(behandlingId: UUID, dokumentId: UUID, emit: (DokumentState) -> Unit): Boolean {
        var lastSeen: DokumentState? = null
        var lastChange = System.currentTimeMillis()

        while (System.currentTimeMillis() - lastChange < FOLLOW_STALL_TIMEOUT_MILLIS) {
            val state = dokumentStateService.getState(behandlingId = behandlingId, dokumentId = dokumentId)

            if (state != lastSeen) {
                emit(state)
                lastSeen = state
                lastChange = System.currentTimeMillis()
            }

            if (state.status.isTerminal()) {
                return true
            }

            Thread.sleep(FOLLOW_POLL_INTERVAL_MILLIS)
        }

        return false
    }
}

data class DokumentState(
    val status: DokumentStatus,
    val size: Long,
    val mellomlagerId: String,
    val scannedGeneration: Long?,
    val parentId: UUID?,
)

/**
 * Reads and writes the state of a single uploaded document in its own transaction, so that every
 * status change is visible to other requests (and other pods) the moment it happens.
 */
@Service
class DokumentStateService(
    @PersistenceContext private val entityManager: EntityManager,
    private val behandlingService: BehandlingService,
    private val documentPolicyService: DocumentPolicyService,
    private val dokumentStatusEventPublisher: DokumentStatusEventPublisher,
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    fun getState(behandlingId: UUID, dokumentId: UUID): DokumentState =
        getDokument(behandlingId = behandlingId, dokumentId = dokumentId).toState()

    /**
     * Confirming a document changes it, so it takes the same access as any other change to a document
     * under arbeid.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    fun validateWriteAccess(behandlingId: UUID, dokumentId: UUID) {
        val behandling = behandlingService.getBehandlingAndCheckReadAccessToSak(behandlingId)
        val dokument = getDokument(behandlingId = behandlingId, dokumentId = dokumentId)

        documentPolicyService.validateDokumentUnderArbeidAction(
            behandling = behandling,
            dokumentType = DuaAccessPolicy.DokumentType.UPLOADED,
            parentDokumentType = documentPolicyService.getParentDokumentType(
                parentDuaId = (dokument as? DokumentUnderArbeidAsVedlegg)?.parentId
            ),
            documentRole = (dokument as DokumentUnderArbeid).creatorRole,
            action = DuaAccessPolicy.Action.WRITE,
            duaMarkertFerdig = dokument.erMarkertFerdig(),
        )
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun setStatus(
        behandlingId: UUID,
        dokumentId: UUID,
        status: DokumentStatus,
        size: Long? = null,
    ): DokumentState {
        val dokument = getDokumentForUpdate(behandlingId = behandlingId, dokumentId = dokumentId)

        //Someone else already took this document to a terminal status, and that is where it stays.
        if (dokument.status.isTerminal()) {
            return dokument.toState()
        }

        dokument.status = status
        if (size != null) {
            dokument.size = size
        }

        return dokument.touch(behandlingId = behandlingId)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun setScanned(
        behandlingId: UUID,
        dokumentId: UUID,
        scannedGeneration: Long,
        size: Long?,
    ): DokumentState {
        val dokument = getDokumentForUpdate(behandlingId = behandlingId, dokumentId = dokumentId)

        if (dokument.status.isTerminal()) {
            return dokument.toState()
        }

        dokument.scannedGeneration = scannedGeneration
        dokument.status = DokumentStatus.CONVERTING
        if (size != null) {
            dokument.size = size
        }

        return dokument.touch(behandlingId = behandlingId)
    }

    /**
     * Sends the document back to the start of the scan, for when the file in kabal-file-api turned
     * out not to be the one we scanned.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun resetForRescan(behandlingId: UUID, dokumentId: UUID): DokumentState {
        val dokument = getDokumentForUpdate(behandlingId = behandlingId, dokumentId = dokumentId)

        if (dokument.status.isTerminal()) {
            return dokument.toState()
        }

        dokument.scannedGeneration = null
        dokument.status = DokumentStatus.UPLOADED

        return dokument.touch(behandlingId = behandlingId)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun setDone(behandlingId: UUID, dokumentId: UUID, size: Long): DokumentState {
        val dokument = getDokumentForUpdate(behandlingId = behandlingId, dokumentId = dokumentId)

        //A document that failed is not resurrected by a request that was still on its way.
        if (dokument.status.isTerminal() && dokument.status != DokumentStatus.DONE) {
            return dokument.toState()
        }

        dokument.size = size
        dokument.status = DokumentStatus.DONE

        return dokument.touch(behandlingId = behandlingId)
    }

    /**
     * Locks the row for the rest of the transaction, so that two pods working on the same document
     * change its status one at a time and each of them sees what the other did.
     */
    private fun getDokumentForUpdate(behandlingId: UUID, dokumentId: UUID): DokumentUnderArbeidAsOpplastet =
        getDokument(behandlingId = behandlingId, dokumentId = dokumentId, lockMode = LockModeType.PESSIMISTIC_WRITE)

    private fun getDokument(
        behandlingId: UUID,
        dokumentId: UUID,
        lockMode: LockModeType = LockModeType.NONE,
    ): DokumentUnderArbeidAsOpplastet {
        //Sjekker lesetilgang på behandlingsnivå:
        behandlingService.getBehandlingAndCheckReadAccessToSak(behandlingId)

        val dokument = entityManager.find(DokumentUnderArbeid::class.java, dokumentId, lockMode)
            ?: throw DocumentDoesNotExistException("Dokumentet med id $dokumentId finnes ikke.")

        if (dokument.behandlingId != behandlingId) {
            throw DocumentDoesNotExistException("Dokumentet med id $dokumentId finnes ikke i denne behandlingen.")
        }

        if (dokument !is DokumentUnderArbeidAsOpplastet) {
            throw DokumentValidationException("Dokumentet med id $dokumentId er ikke et opplastet dokument.")
        }

        return dokument
    }

    private fun DokumentUnderArbeidAsOpplastet.touch(behandlingId: UUID): DokumentState {
        modified = LocalDateTime.now()

        val state = toState()
        dokumentStatusEventPublisher.publish(behandlingId = behandlingId, dokumentId = id, state = state)
        return state
    }

    private fun DokumentUnderArbeidAsOpplastet.toState() = DokumentState(
        status = status,
        size = size ?: 0L,
        mellomlagerId = mellomlagerId!!,
        scannedGeneration = scannedGeneration,
        parentId = (this as? DokumentUnderArbeidAsVedlegg)?.parentId,
    )
}
