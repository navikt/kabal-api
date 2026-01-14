package no.nav.klage.oppgave.repositories

import com.ninjasquad.springmockk.MockkBean
import no.nav.klage.oppgave.db.PostgresIntegrationTestBase
import no.nav.klage.oppgave.domain.document.DocumentToMerge
import no.nav.klage.oppgave.domain.document.MergedDocument
import no.nav.klage.oppgave.util.TokenUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*

@ActiveProfiles("local")
@DataJpaTest
class MergedDocumentRepositoryTest : PostgresIntegrationTestBase() {

    @Autowired
    lateinit var testEntityManager: TestEntityManager

    @Autowired
    lateinit var mergedDocumentRepository: MergedDocumentRepository

    //Because of Hibernate Envers and our setup for audit logs.
    @MockkBean
    lateinit var tokenUtil: TokenUtil

    @Test
    fun `delete old merged documents works`() {
        val thresholdWeeks = 3L

        val now = LocalDateTime.of(LocalDate.of(2023, 5, 1), LocalTime.MIN)

        val documentToMergeList = mutableListOf<MergedDocument>()

        val idToKeep = UUID.randomUUID()
        documentToMergeList += MergedDocument(
            id = idToKeep,
            title = "title",
            documentsToMerge = setOf(
                DocumentToMerge(
                    journalpostId = "2",
                    dokumentInfoId = "2",
                    index = 0,
                )
            ),
            hash = "a",
            created = now.minusWeeks(thresholdWeeks),
        )

        documentToMergeList += MergedDocument(
            title = "title 2",
            documentsToMerge = setOf(
                DocumentToMerge(
                    journalpostId = "3",
                    dokumentInfoId = "3",
                    index = 0,
                ),
                DocumentToMerge(
                    journalpostId = "3",
                    dokumentInfoId = "3",
                    index = 1,
                ),
                DocumentToMerge(
                    journalpostId = "3",
                    dokumentInfoId = "3",
                    index = 2,
                )
            ),
            hash = "b",
            created = now.minusWeeks(thresholdWeeks).minusMinutes(1),
        )

        mergedDocumentRepository.saveAll(documentToMergeList)

        testEntityManager.flush()
        testEntityManager.clear()

        assertThat(mergedDocumentRepository.findAll()).hasSize(2)

        mergedDocumentRepository.deleteByCreatedBefore(now.minusWeeks(thresholdWeeks))

        testEntityManager.flush()
        testEntityManager.clear()

        assertThat(mergedDocumentRepository.findAll()).hasSize(1)
        assertThat(mergedDocumentRepository.findAll().first().id).isEqualTo(idToKeep)
    }

}