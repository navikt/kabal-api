package no.nav.klage.dokument.repositories

import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAvsenderMottakerInfo
import no.nav.klage.dokument.domain.dokumenterunderarbeid.OpplastetDokumentUnderArbeidAsHoveddokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.OpplastetDokumentUnderArbeidAsVedlegg
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.oppgave.db.TestPostgresqlContainer
import no.nav.klage.oppgave.domain.klage.BehandlingRole.KABAL_SAKSBEHANDLING
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

    @Autowired
    lateinit var opplastetDokumentUnderArbeidAsHoveddokumentRepository: OpplastetDokumentUnderArbeidAsHoveddokumentRepository

    @Autowired
    lateinit var opplastetDokumentUnderArbeidAsVedleggRepository: OpplastetDokumentUnderArbeidAsVedleggRepository

    @Test
    fun `persist opplastet hoveddokument works`() {
        val behandlingId = UUID.randomUUID()
        val hovedDokument = OpplastetDokumentUnderArbeidAsHoveddokument(
            mellomlagerId = UUID.randomUUID().toString(),
            mellomlagretDate = LocalDateTime.now(),
            markertFerdig = LocalDateTime.now(),
            size = 1002,
            name = "Vedtak.pdf",
            behandlingId = behandlingId,
            dokumentType = DokumentType.BREV,
            creatorIdent = "null",
            creatorRole = KABAL_SAKSBEHANDLING,
            created = LocalDateTime.now(),
            modified = LocalDateTime.now(),
            datoMottatt = null,
            journalfoerendeEnhetId = null,
            inngaaendeKanal = null,
        )
        hovedDokument.markerFerdigHvisIkkeAlleredeMarkertFerdig(LocalDateTime.now(), "S123456")
        hovedDokument.ferdigstillHvisIkkeAlleredeFerdigstilt(LocalDateTime.now())
        dokumentUnderArbeidRepository.save(hovedDokument)

        testEntityManager.flush()
        testEntityManager.clear()

        val byId = dokumentUnderArbeidRepository.getReferenceById(hovedDokument.id)
        assertThat(byId).isEqualTo(hovedDokument)
    }

    @Test
    fun `hoveddokument can have vedlegg`() {
        val behandlingId = UUID.randomUUID()
        val hovedDokument = OpplastetDokumentUnderArbeidAsHoveddokument(
            mellomlagerId = UUID.randomUUID().toString(),
            mellomlagretDate = LocalDateTime.now(),
            size = 1001,
            name = "Vedtak.pdf",
            behandlingId = behandlingId,
            dokumentType = DokumentType.BREV,
            creatorIdent = "null",
            creatorRole = KABAL_SAKSBEHANDLING,
            created = LocalDateTime.now(),
            modified = LocalDateTime.now(),
            datoMottatt = null,
            journalfoerendeEnhetId = null,
            inngaaendeKanal = null,
        )

        dokumentUnderArbeidRepository.save(hovedDokument)

        testEntityManager.flush()
        testEntityManager.clear()

        dokumentUnderArbeidRepository.save(
            OpplastetDokumentUnderArbeidAsVedlegg(
                mellomlagerId = UUID.randomUUID().toString(),
                mellomlagretDate = LocalDateTime.now(),
                size = 1001,
                name = "Vedtak.pdf",
                behandlingId = behandlingId,
                dokumentType = DokumentType.BREV,
                parentId = hovedDokument.id,
                creatorIdent = "null",
                creatorRole = KABAL_SAKSBEHANDLING,
                created = LocalDateTime.now(),
                modified = LocalDateTime.now(),
            )
        )

        testEntityManager.flush()
        testEntityManager.clear()

        val vedlegg = opplastetDokumentUnderArbeidAsVedleggRepository.findByParentId(hovedDokument.id)
        assertThat(vedlegg).hasSize(1)
    }

    @Test
    fun `hoveddokument can have brevmottakerinfo`() {
        val behandlingId = UUID.randomUUID()
        val hovedDokument = OpplastetDokumentUnderArbeidAsHoveddokument(
            mellomlagerId = UUID.randomUUID().toString(),
            mellomlagretDate = LocalDateTime.now(),
            size = 1001,
            name = "Vedtak.pdf",
            behandlingId = behandlingId,
            dokumentType = DokumentType.BREV,
            creatorIdent = "null",
            creatorRole = KABAL_SAKSBEHANDLING,
            created = LocalDateTime.now(),
            modified = LocalDateTime.now(),
            datoMottatt = null,
            avsenderMottakerInfoSet = mutableSetOf(
                DokumentUnderArbeidAvsenderMottakerInfo(
                    identifikator = "123",
                    localPrint = false
                )
            ),
            journalfoerendeEnhetId = null,
            inngaaendeKanal = null,
        )
        dokumentUnderArbeidRepository.save(hovedDokument)

        testEntityManager.flush()
        testEntityManager.clear()

        dokumentUnderArbeidRepository.save(
            OpplastetDokumentUnderArbeidAsVedlegg(
                mellomlagerId = UUID.randomUUID().toString(),
                mellomlagretDate = LocalDateTime.now(),
                size = 1001,
                name = "Vedtak.pdf",
                behandlingId = behandlingId,
                dokumentType = DokumentType.BREV,
                parentId = hovedDokument.id,
                creatorIdent = "null",
                creatorRole = KABAL_SAKSBEHANDLING,
                created = LocalDateTime.now(),
                modified = LocalDateTime.now(),
            )
        )

        testEntityManager.flush()
        testEntityManager.clear()
        val hoveddokument = opplastetDokumentUnderArbeidAsHoveddokumentRepository.getReferenceById(hovedDokument.id)
        assertThat(hoveddokument.avsenderMottakerInfoSet.first().identifikator == "123")
    }

    @Test
    fun `documents can be found and edited`() {
        val behandlingId = UUID.randomUUID()
        val name = "some name"

        val hovedDokument = OpplastetDokumentUnderArbeidAsHoveddokument(
            mellomlagerId = UUID.randomUUID().toString(),
            mellomlagretDate = LocalDateTime.now(),
            size = 1001,
            name = "other name",
            behandlingId = behandlingId,
            dokumentType = DokumentType.BREV,
            creatorIdent = "null",
            creatorRole = KABAL_SAKSBEHANDLING,
            created = LocalDateTime.now(),
            modified = LocalDateTime.now(),
            datoMottatt = null,
            journalfoerendeEnhetId = null,
            inngaaendeKanal = null,
        )
        dokumentUnderArbeidRepository.save(hovedDokument)

        testEntityManager.flush()
        testEntityManager.clear()

        val hovedDokumentet = dokumentUnderArbeidRepository.getReferenceById(hovedDokument.id)
        assertThat(hovedDokumentet).isNotNull
        hovedDokumentet.name = name

        testEntityManager.flush()
        testEntityManager.clear()

        assertThat(dokumentUnderArbeidRepository.getReferenceById(hovedDokument.id).name).isEqualTo(name)
    }

}