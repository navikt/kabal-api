package no.nav.klage.dokument.repositories

import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeid
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.oppgave.db.TestPostgresqlContainer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDateTime
import java.util.*

@ActiveProfiles("local")
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DokumentUnderArbeidRepositoryTest {

    companion object {
        @Container
        @JvmField
        val postgreSQLContainer: TestPostgresqlContainer = TestPostgresqlContainer.instance
    }

    @Autowired
    lateinit var testEntityManager: TestEntityManager

    @Autowired
    lateinit var dokumentUnderArbeidRepository: DokumentUnderArbeidRepository

    @Test
    fun `persist hoveddokument works`() {

        val behandlingId = UUID.randomUUID()
        val hovedDokument = DokumentUnderArbeid(
            mellomlagerId = UUID.randomUUID().toString(),
            opplastet = LocalDateTime.now(),
            size = 1001,
            name = "Vedtak.pdf",
            behandlingId = behandlingId,
            dokumentType = DokumentType.BREV,
            smartEditorId = null,
            smartEditorTemplateId = null,
        )
        hovedDokument.markerFerdigHvisIkkeAlleredeMarkertFerdig(LocalDateTime.now())
        hovedDokument.ferdigstillHvisIkkeAlleredeFerdigstilt(LocalDateTime.now())
        dokumentUnderArbeidRepository.save(hovedDokument)

        testEntityManager.flush()
        testEntityManager.clear()

        val byId = dokumentUnderArbeidRepository.getById(hovedDokument.id)
        assertThat(byId).isEqualTo(hovedDokument)
    }


    @Test
    fun `hoveddokument can have vedlegg`() {

        val behandlingId = UUID.randomUUID()
        val hovedDokument = DokumentUnderArbeid(
            mellomlagerId = UUID.randomUUID().toString(),
            opplastet = LocalDateTime.now(),
            size = 1001,
            name = "Vedtak.pdf",
            behandlingId = behandlingId,
            dokumentType = DokumentType.BREV,
            smartEditorId = null,
            smartEditorTemplateId = null,
        )
        dokumentUnderArbeidRepository.save(hovedDokument)

        testEntityManager.flush()
        testEntityManager.clear()

        dokumentUnderArbeidRepository.save(
            DokumentUnderArbeid(
                mellomlagerId = UUID.randomUUID().toString(),
                opplastet = LocalDateTime.now(),
                size = 1001,
                name = "Vedtak.pdf",
                behandlingId = behandlingId,
                dokumentType = DokumentType.BREV,
                smartEditorId = null,
                smartEditorTemplateId = null,
                parentId = hovedDokument.id
            )
        )

        testEntityManager.flush()
        testEntityManager.clear()

        val vedlegg = dokumentUnderArbeidRepository.findByParentIdOrderByCreated(hovedDokument.id)
        assertThat(vedlegg).hasSize(1)
    }

    @Test
    fun `vedlegg can be unlinked`() {

        val behandlingId = UUID.randomUUID()
        val hovedDokument = DokumentUnderArbeid(
            mellomlagerId = UUID.randomUUID().toString(),
            opplastet = LocalDateTime.now(),
            size = 1001,
            name = "Vedtak.pdf",
            behandlingId = behandlingId,
            dokumentType = DokumentType.BREV,
            smartEditorId = null,
            smartEditorTemplateId = null,
        )
        dokumentUnderArbeidRepository.save(hovedDokument)

        testEntityManager.flush()
        testEntityManager.clear()


        dokumentUnderArbeidRepository.save(
            DokumentUnderArbeid(
                mellomlagerId = UUID.randomUUID().toString(),
                opplastet = LocalDateTime.now(),
                size = 1001,
                name = "Vedtak.pdf",
                behandlingId = behandlingId,
                dokumentType = DokumentType.BREV,
                smartEditorId = null,
                smartEditorTemplateId = null,
                parentId = hovedDokument.id
            )
        )

        testEntityManager.flush()
        testEntityManager.clear()

        val vedlegg = dokumentUnderArbeidRepository.findByParentIdOrderByCreated(hovedDokument.id).first()
        vedlegg.parentId = null
        testEntityManager.flush()
        testEntityManager.clear()

        assertThat(dokumentUnderArbeidRepository.findByParentIdOrderByCreated(hovedDokument.id)).hasSize(0)
    }

    @Test
    fun `documents can be found and edited`() {

        val behandlingId = UUID.randomUUID()
        val nyMellomlagerId = UUID.randomUUID().toString()

        val hovedDokument = DokumentUnderArbeid(
            mellomlagerId = UUID.randomUUID().toString(),
            opplastet = LocalDateTime.now(),
            size = 1001,
            name = "Vedtak.pdf",
            behandlingId = behandlingId,
            dokumentType = DokumentType.BREV,
            smartEditorId = null,
            smartEditorTemplateId = null,
        )
        dokumentUnderArbeidRepository.save(hovedDokument)

        testEntityManager.flush()
        testEntityManager.clear()

        val hovedDokumentet = dokumentUnderArbeidRepository.getById(hovedDokument.id)
        assertThat(hovedDokumentet).isNotNull
        hovedDokumentet.mellomlagerId = nyMellomlagerId

        testEntityManager.flush()
        testEntityManager.clear()

        assertThat(dokumentUnderArbeidRepository.getById(hovedDokument.id).mellomlagerId).isEqualTo(nyMellomlagerId)
    }


    @Test
    fun `documents are sorted correctly`() {

        val behandlingId = UUID.randomUUID()

        val hovedDokument1 = DokumentUnderArbeid(
            mellomlagerId = UUID.randomUUID().toString(),
            opplastet = LocalDateTime.now(),
            size = 1001,
            name = "Vedtak.pdf",
            behandlingId = behandlingId,
            dokumentType = DokumentType.BREV,
            created = LocalDateTime.now().minusDays(1),
            smartEditorId = null,
            smartEditorTemplateId = null,
        )
        val vedlegg1 = DokumentUnderArbeid(
            mellomlagerId = UUID.randomUUID().toString(),
            opplastet = LocalDateTime.now(),
            size = 1001,
            name = "Vedtak.pdf",
            behandlingId = behandlingId,
            dokumentType = DokumentType.BREV,
            created = LocalDateTime.now().minusDays(2),
            smartEditorId = null,
            smartEditorTemplateId = null,
            parentId = hovedDokument1.id
        )
        val vedlegg2 = DokumentUnderArbeid(
            mellomlagerId = UUID.randomUUID().toString(),
            opplastet = LocalDateTime.now(),
            size = 1001,
            name = "Vedtak.pdf",
            behandlingId = behandlingId,
            dokumentType = DokumentType.BREV,
            created = LocalDateTime.now().minusDays(5),
            smartEditorId = null,
            smartEditorTemplateId = null,
            parentId = hovedDokument1.id
        )

        val hovedDokument2 = DokumentUnderArbeid(
            mellomlagerId = UUID.randomUUID().toString(),
            opplastet = LocalDateTime.now(),
            size = 1001,
            name = "Vedtak.pdf",
            behandlingId = behandlingId,
            dokumentType = DokumentType.BREV,
            created = LocalDateTime.now().minusDays(3),
            smartEditorId = null,
            smartEditorTemplateId = null,
        )

        val hovedDokument3 = DokumentUnderArbeid(
            mellomlagerId = UUID.randomUUID().toString(),
            opplastet = LocalDateTime.now(),
            size = 1001,
            name = "Vedtak.pdf",
            behandlingId = behandlingId,
            dokumentType = DokumentType.BREV,
            created = LocalDateTime.now().plusDays(3),
            smartEditorId = null,
            smartEditorTemplateId = null,
        )
        dokumentUnderArbeidRepository.save(hovedDokument1)
        dokumentUnderArbeidRepository.save(vedlegg1)
        dokumentUnderArbeidRepository.save(vedlegg2)
        dokumentUnderArbeidRepository.save(hovedDokument2)
        dokumentUnderArbeidRepository.save(hovedDokument3)

        testEntityManager.flush()
        testEntityManager.clear()

        assertThat(dokumentUnderArbeidRepository.findByBehandlingIdAndFerdigstiltIsNullOrderByCreated(behandlingId)).containsExactly(
            vedlegg2,
            hovedDokument2,
            vedlegg1,
            hovedDokument1,
            hovedDokument3
        )
    }

}