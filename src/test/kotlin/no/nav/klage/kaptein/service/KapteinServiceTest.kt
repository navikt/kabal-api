package no.nav.klage.kaptein.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.persistence.EntityManager
import jakarta.servlet.ServletOutputStream
import jakarta.servlet.WriteListener
import jakarta.servlet.http.HttpServletResponse
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Sakstype
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.Klagebehandling
import no.nav.klage.oppgave.domain.behandling.embedded.Feilregistrering
import no.nav.klage.oppgave.domain.behandling.embedded.Klager
import no.nav.klage.oppgave.domain.behandling.embedded.MedunderskriverTildeling
import no.nav.klage.oppgave.domain.behandling.embedded.PartId
import no.nav.klage.oppgave.domain.behandling.embedded.SakenGjelder
import no.nav.klage.oppgave.domain.behandling.embedded.SattPaaVent
import no.nav.klage.oppgave.repositories.BehandlingRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.aop.framework.ProxyFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
import org.springframework.transaction.interceptor.TransactionInterceptor
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class KapteinServiceTest {
    @Test
    fun `stream uses readonly transactions per bounded batch and writes unchanged NDJSON outside transactions`() {
        val transactionManager = RecordingTransactionManager()
        val ids =
            (0L until 400L).map { index ->
                UUID(0x1000000000000000L, index)
            } + UUID(Long.MIN_VALUE, 1L)
        val treatments = ids.mapIndexed { index, id -> behandling(id = id, populateOptionalFields = index == 0) }
        val treatmentsById = treatments.associateBy { it.id }
        val repository = mockk<BehandlingRepository>()
        val batches = mutableListOf<List<UUID>>()
        every { repository.findAllIdsForKaptein() } answers {
            assertReadOnlyTransaction()
            ids
        }
        every { repository.findAllForKapteinByIds(any()) } answers {
            assertReadOnlyTransaction()
            val requestedIds = firstArg<Collection<UUID>>().toList()
            batches += requestedIds
            requestedIds.mapNotNull(treatmentsById::get)
        }

        val service = proxiedService(repository, transactionManager)
        val response = mockk<HttpServletResponse>(relaxed = true)
        val output = TrackingServletOutputStream()
        stubOutputStream(response, output)

        TransactionTemplate(transactionManager).executeWithoutResult {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue()
            service.writeBehandlingerStreamedToOutputStreamAsNDJson(response)
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue()
        }

        assertThat(batches.map { it.size }).containsExactly(200, 200, 1)
        assertThat(batches.flatten()).containsExactlyElementsOf(ids)
        assertThat(transactionManager.readOnlyTransactions).containsExactly(false, true, true, true, true)
        assertThat(transactionManager.rollbacks).isZero()
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse()
        assertOutputOutsideTransactions(output)

        verify { response.contentType = MediaType.APPLICATION_NDJSON_VALUE }
        verify { response.status = HttpStatus.OK.value() }
        verify { response.addIntHeader("Kaptein-Total", ids.size) }
        verify { response.addHeader("Transfer-Encoding", "chunked") }

        val lines =
            output
                .body()
                .lineSequence()
                .filter(String::isNotEmpty)
                .toList()
        assertThat(lines).hasSize(ids.size)
        val jsonMapper = jacksonObjectMapper()
        val first = jsonMapper.readTree(lines.first())
        assertThat(first.properties().map { it.key }.toSet())
            .containsExactlyInAnyOrder(
                "id",
                "fraNAVEnhet",
                "mottattVedtaksinstans",
                "temaId",
                "ytelseId",
                "typeId",
                "mottattKlageinstans",
                "avsluttetAvSaksbehandlerDate",
                "isAvsluttetAvSaksbehandler",
                "isTildelt",
                "tildeltEnhet",
                "frist",
                "ageKA",
                "datoSendtMedunderskriver",
                "hjemmelIdList",
                "modified",
                "created",
                "resultat",
                "sattPaaVent",
                "sendtTilTrygderetten",
                "kjennelseMottatt",
                "feilregistrering",
                "fagsystemId",
                "varsletFrist",
                "tilbakekreving",
                "previousTildeltEnhet",
                "previousRegistreringshjemmelIdList",
                "initiatingSystem",
            )
        assertThat(first.path("id").asString()).isEqualTo(ids.first().toString())
        assertThat(first.path("hjemmelIdList").single().asString()).isEqualTo(Hjemmel.FTRL_8_7.id)
        assertThat(
            first
                .path("resultat")
                .path("hjemmelIdSet")
                .single()
                .asString(),
        ).isEqualTo(Registreringshjemmel.FTRL_22_15_TILBAKEKREVING.id)
        assertThat(first.path("sattPaaVent").path("reasonId").asString()).isEqualTo("SYNTHETIC")
        assertThat(first.path("datoSendtMedunderskriver").asString()).isEqualTo("2025-01-03")
        assertThat(first.path("feilregistrering").path("reason").asString()).isEqualTo("synthetic reason")
        val outputIds = lines.map { jsonMapper.readTree(it).path("id").asString() }
        assertThat(outputIds).containsExactlyElementsOf(ids.map(UUID::toString))
    }

    @Test
    fun `empty stream writes and closes response without a transaction`() {
        val transactionManager = RecordingTransactionManager()
        val repository = mockk<BehandlingRepository>()
        every { repository.findAllIdsForKaptein() } answers {
            assertReadOnlyTransaction()
            emptyList()
        }
        val service = proxiedService(repository, transactionManager)
        val response = mockk<HttpServletResponse>(relaxed = true)
        val output = TrackingServletOutputStream()
        stubOutputStream(response, output)

        service.writeBehandlingerStreamedToOutputStreamAsNDJson(response)

        verify(exactly = 0) { repository.findAllForKapteinByIds(any()) }
        verify { response.addIntHeader("Kaptein-Total", 0) }
        assertThat(transactionManager.readOnlyTransactions).containsExactly(true)
        assertThat(output.body()).isEmpty()
        assertOutputOutsideTransactions(output)
    }

    @Test
    fun `batch read failure propagates and closes response after ending transaction`() {
        val transactionManager = RecordingTransactionManager()
        val ids = (0L..200L).map { UUID(0x1000000000000000L, it) }
        val repository = mockk<BehandlingRepository>()
        every { repository.findAllIdsForKaptein() } returns ids
        every { repository.findAllForKapteinByIds(any()) } throws IllegalStateException("synthetic read failure")
        val service = proxiedService(repository, transactionManager)
        val response = mockk<HttpServletResponse>(relaxed = true)
        val output = TrackingServletOutputStream()
        stubOutputStream(response, output)

        assertThatThrownBy { service.writeBehandlingerStreamedToOutputStreamAsNDJson(response) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("synthetic read failure")

        verify(exactly = 1) { repository.findAllForKapteinByIds(any()) }
        assertThat(transactionManager.readOnlyTransactions).containsExactly(true, true)
        assertThat(transactionManager.rollbacks).isEqualTo(1)
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse()
        assertThat(output.closed).isTrue()
        assertOutputOutsideTransactions(output)
    }

    @Test
    fun `mapping failure rolls back its readonly batch and propagates`() {
        val transactionManager = RecordingTransactionManager()
        val ids = (0L..200L).map { UUID(0x1000000000000000L, it) }
        val repository = mockk<BehandlingRepository>()
        every { repository.findAllIdsForKaptein() } returns ids
        every { repository.findAllForKapteinByIds(any()) } answers {
            assertReadOnlyTransaction()
            listOf(behandling(id = ids.first(), populateOptionalFields = false, failOnMapping = true))
        }
        val service = proxiedService(repository, transactionManager)
        val response = mockk<HttpServletResponse>(relaxed = true)
        val output = TrackingServletOutputStream()
        stubOutputStream(response, output)

        assertThatThrownBy { service.writeBehandlingerStreamedToOutputStreamAsNDJson(response) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("synthetic mapping failure")

        verify(exactly = 1) { repository.findAllForKapteinByIds(any()) }
        assertThat(transactionManager.readOnlyTransactions).containsExactly(true, true)
        assertThat(transactionManager.rollbacks).isEqualTo(1)
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse()
        assertThat(output.closed).isTrue()
        assertOutputOutsideTransactions(output)
    }

    @Test
    fun `client disconnect propagates and prevents fetching the next batch`() {
        val transactionManager = RecordingTransactionManager()
        val ids = (0L..200L).map { UUID(0x1000000000000000L, it) }
        val treatmentsById = ids.associateWith { behandling(id = it, populateOptionalFields = false) }
        val repository = mockk<BehandlingRepository>()
        every { repository.findAllIdsForKaptein() } returns ids
        every { repository.findAllForKapteinByIds(any()) } answers {
            assertReadOnlyTransaction()
            firstArg<Collection<UUID>>().mapNotNull(treatmentsById::get)
        }
        val service = proxiedService(repository, transactionManager)
        val response = mockk<HttpServletResponse>(relaxed = true)
        val output = TrackingServletOutputStream(failOnWrite = true)
        stubOutputStream(response, output)

        assertThatThrownBy { service.writeBehandlingerStreamedToOutputStreamAsNDJson(response) }
            .isInstanceOf(IOException::class.java)
            .hasMessage("synthetic client disconnect")

        verify(exactly = 1) { repository.findAllForKapteinByIds(any()) }
        assertThat(transactionManager.readOnlyTransactions).containsExactly(true, true)
        assertThat(transactionManager.rollbacks).isZero()
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse()
        assertThat(output.closed).isTrue()
        assertOutputOutsideTransactions(output)
    }

    private fun proxiedService(
        repository: BehandlingRepository,
        transactionManager: RecordingTransactionManager,
    ): KapteinService {
        val entityManager = mockk<EntityManager>(relaxed = true)
        every { entityManager.clear() } answers { assertReadOnlyTransaction() }
        val target =
            KapteinService(
                behandlingRepository = repository,
                entityManager = entityManager,
                aivenKafkaTemplate = mockk<KafkaTemplate<String, String>>(relaxed = true),
                kapteinBehandlingTopic = "synthetic-topic",
                transactionManager = transactionManager,
            )
        return ProxyFactory(target)
            .apply {
                isProxyTargetClass = true
                addAdvice(TransactionInterceptor(transactionManager, AnnotationTransactionAttributeSource()))
            }.proxy as KapteinService
    }

    private fun stubOutputStream(
        response: HttpServletResponse,
        output: TrackingServletOutputStream,
    ) {
        every { response.outputStream } answers {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse()
            output
        }
    }

    private fun behandling(
        id: UUID,
        populateOptionalFields: Boolean,
        failOnMapping: Boolean = false,
    ): Klagebehandling {
        val hjemler = TransactionCheckedSet(elements = listOf(Hjemmel.FTRL_8_7), failOnAccess = failOnMapping)
        val registreringshjemler =
            TransactionCheckedMutableSet(
                elements = listOf(Registreringshjemmel.FTRL_22_15_TILBAKEKREVING),
                failOnAccess = failOnMapping,
            )
        return Klagebehandling(
            mottattVedtaksinstans = LocalDate.of(2025, 1, 1),
            avsenderEnhetFoersteinstans = "SYNTHETIC",
            kakaKvalitetsvurderingId = null,
            kakaKvalitetsvurderingVersion = 2,
            varsletBehandlingstid = null,
            forlengetBehandlingstidDraft = null,
            id = id,
            previousBehandlingId = null,
            klager =
                Klager(
                    id = UUID.randomUUID(),
                    partId = PartId(type = PartIdType.PERSON, value = UUID.randomUUID().toString()),
                ),
            sakenGjelder =
                SakenGjelder(
                    id = UUID.randomUUID(),
                    partId = PartId(type = PartIdType.PERSON, value = UUID.randomUUID().toString()),
                ),
            prosessfullmektig = null,
            ytelse = Ytelse.OMS_OMP,
            type = Type.KLAGE,
            kildeReferanse = "synthetic-reference",
            fagsystem = Fagsystem.K9,
            fagsakId = null,
            sakstype = Sakstype.GENERELL_SAK,
            arkivsaksnummer = null,
            mottattKlageinstans = LocalDateTime.of(2025, 1, 2, 0, 0),
            frist = LocalDate.of(2025, 2, 1),
            hjemler = hjemler,
            registreringshjemler = registreringshjemler,
            sattPaaVent =
                if (populateOptionalFields) {
                    SattPaaVent(
                        from = LocalDate.of(2025, 1, 2),
                        to = LocalDate.of(2025, 1, 4),
                        reason = "synthetic hold",
                        reasonId = "SYNTHETIC",
                    )
                } else {
                    null
                },
            feilregistrering =
                if (populateOptionalFields) {
                    Feilregistrering(
                        navIdent = "SYNTHETIC",
                        navn = null,
                        registered = LocalDateTime.of(2025, 1, 5, 0, 0),
                        reason = "synthetic reason",
                        fagsystem = Fagsystem.K9,
                    )
                } else {
                    null
                },
            medunderskriver =
                if (populateOptionalFields) {
                    MedunderskriverTildeling(
                        saksbehandlerident = "SYNTHETIC",
                        tidspunkt = LocalDateTime.of(2025, 1, 3, 0, 0),
                    )
                } else {
                    null
                },
            previousSaksbehandlerident = null,
            gosysOppgaveId = null,
            gosysOppgaveRequired = false,
            initiatingSystem = Behandling.InitiatingSystem.KABAL,
        )
    }

    private fun assertReadOnlyTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue()
        assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue()
    }

    private fun assertOutputOutsideTransactions(output: TrackingServletOutputStream) {
        assertThat(output.operations).isNotEmpty()
        assertThat(output.operations).allMatch { (_, transactionActive) -> !transactionActive }
    }

    private inner class TransactionCheckedSet<T>(
        private val elements: List<T>,
        private val failOnAccess: Boolean,
    ) : AbstractSet<T>() {
        override val size: Int
            get() {
                assertReadOnlyTransaction()
                return elements.size
            }

        override fun iterator(): Iterator<T> {
            assertReadOnlyTransaction()
            if (failOnAccess) error("synthetic mapping failure")
            return elements.iterator()
        }
    }

    private inner class TransactionCheckedMutableSet<T>(
        private val elements: List<T>,
        private val failOnAccess: Boolean,
    ) : AbstractMutableSet<T>() {
        override val size: Int
            get() {
                assertReadOnlyTransaction()
                return elements.size
            }

        override fun iterator(): MutableIterator<T> {
            assertReadOnlyTransaction()
            if (failOnAccess) error("synthetic mapping failure")
            return elements.toMutableList().iterator()
        }

        override fun add(element: T): Boolean = error("Test set is immutable")
    }

    private class TrackingServletOutputStream(
        private val failOnWrite: Boolean = false,
    ) : ServletOutputStream() {
        private val bytes = ByteArrayOutputStream()
        val operations = mutableListOf<Pair<String, Boolean>>()
        var closed = false
            private set

        override fun isReady(): Boolean = true

        override fun setWriteListener(writeListener: WriteListener) = Unit

        override fun write(value: Int) {
            record("write")
            if (failOnWrite) throw IOException("synthetic client disconnect")
            bytes.write(value)
        }

        override fun write(
            bytes: ByteArray,
            offset: Int,
            length: Int,
        ) {
            record("write")
            if (failOnWrite) throw IOException("synthetic client disconnect")
            this.bytes.write(bytes, offset, length)
        }

        override fun flush() {
            record("flush")
        }

        override fun close() {
            record("close")
            closed = true
        }

        fun body(): String = bytes.toString(Charsets.UTF_8)

        private fun record(operation: String) {
            operations += operation to TransactionSynchronizationManager.isActualTransactionActive()
        }
    }

    private class RecordingTransactionManager : AbstractPlatformTransactionManager() {
        private class Transaction(
            var active: Boolean = false,
        )

        private var currentTransaction: Transaction? = null
        val readOnlyTransactions = mutableListOf<Boolean>()
        var rollbacks = 0
            private set

        override fun doGetTransaction(): Any = currentTransaction ?: Transaction()

        override fun isExistingTransaction(transaction: Any): Boolean = (transaction as Transaction).active

        override fun doBegin(
            transaction: Any,
            definition: TransactionDefinition,
        ) {
            (transaction as Transaction).active = true
            currentTransaction = transaction
            readOnlyTransactions += definition.isReadOnly
        }

        override fun doCommit(status: DefaultTransactionStatus) {
            (status.transaction as Transaction).active = false
            currentTransaction = null
        }

        override fun doRollback(status: DefaultTransactionStatus) {
            (status.transaction as Transaction).active = false
            currentTransaction = null
            rollbacks++
        }

        override fun doSuspend(transaction: Any): Any {
            val suspended = currentTransaction ?: error("No active transaction to suspend")
            currentTransaction = null
            return suspended
        }

        override fun doResume(
            transaction: Any?,
            suspendedResources: Any,
        ) {
            currentTransaction = suspendedResources as Transaction
        }
    }
}
