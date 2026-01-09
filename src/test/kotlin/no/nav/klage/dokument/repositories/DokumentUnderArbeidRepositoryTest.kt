package no.nav.klage.dokument.repositories

import com.ninjasquad.springmockk.MockkBean
import no.nav.klage.dokument.domain.dokumenterunderarbeid.Brevmottaker
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeid
import no.nav.klage.dokument.domain.dokumenterunderarbeid.OpplastetDokumentUnderArbeidAsHoveddokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.OpplastetDokumentUnderArbeidAsVedlegg
import no.nav.klage.dokument.exceptions.DokumentValidationException
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.oppgave.db.PostgresIntegrationTestBase
import no.nav.klage.oppgave.domain.behandling.BehandlingRole.KABAL_SAKSBEHANDLING
import no.nav.klage.oppgave.util.TokenUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime
import java.util.*

@ActiveProfiles("local")
@DataJpaTest
class DokumentUnderArbeidRepositoryTest : PostgresIntegrationTestBase() {

    @Autowired
    lateinit var testEntityManager: TestEntityManager

    @Autowired
    lateinit var dokumentUnderArbeidRepository: DokumentUnderArbeidRepository

    @Autowired
    lateinit var opplastetDokumentUnderArbeidAsHoveddokumentRepository: OpplastetDokumentUnderArbeidAsHoveddokumentRepository

    @Autowired
    lateinit var opplastetDokumentUnderArbeidAsVedleggRepository: OpplastetDokumentUnderArbeidAsVedleggRepository

    //Because of Hibernate Envers and our setup for audit logs.
    @MockkBean
    lateinit var tokenUtil: TokenUtil

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
                Brevmottaker(
                    technicalPartId = UUID.randomUUID(),
                    identifikator = "123",
                    localPrint = false,
                    forceCentralPrint = false,
                    address = null,
                    navn = null,
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
        assertThat(hoveddokument.brevmottakere.first().identifikator == "123")
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

    @Test
    fun `document name with exactly max length is allowed`() {
        val behandlingId = UUID.randomUUID()
        val maxLengthName = "a".repeat(DokumentUnderArbeid.MAX_NAME_LENGTH)

        val hovedDokument = OpplastetDokumentUnderArbeidAsHoveddokument(
            mellomlagerId = UUID.randomUUID().toString(),
            mellomlagretDate = LocalDateTime.now(),
            size = 1001,
            name = maxLengthName,
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

        val savedDocument = dokumentUnderArbeidRepository.getReferenceById(hovedDokument.id)
        assertThat(savedDocument.name).isEqualTo(maxLengthName)
        assertThat(savedDocument.name.length).isEqualTo(DokumentUnderArbeid.MAX_NAME_LENGTH)
    }

    @Test
    fun `document name exceeding max length throws DokumentValidationException`() {
        val behandlingId = UUID.randomUUID()
        val tooLongName = "a".repeat(DokumentUnderArbeid.MAX_NAME_LENGTH + 1)

        assertThrows<DokumentValidationException> {
            OpplastetDokumentUnderArbeidAsHoveddokument(
                mellomlagerId = UUID.randomUUID().toString(),
                mellomlagretDate = LocalDateTime.now(),
                size = 1001,
                name = tooLongName,
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
        }
    }

}