package no.nav.klage.dokument.api.mapper

import io.mockk.mockk
import no.nav.klage.dokument.clients.kabaljsontopdf.domain.InnholdsfortegnelseRequest.Document.JournalpostMetadata.Type
import no.nav.klage.dokument.domain.dokumenterunderarbeid.JournalfoertDokumentUnderArbeidAsVedlegg
import no.nav.klage.dokument.domain.dokumenterunderarbeid.Language
import no.nav.klage.dokument.domain.dokumenterunderarbeid.OpplastetDokumentUnderArbeidAsVedlegg
import no.nav.klage.dokument.domain.dokumenterunderarbeid.SmartdokumentUnderArbeidAsHoveddokument
import no.nav.klage.dokument.repositories.DokumentUnderArbeidRepository
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.api.mapper.BehandlingMapper
import no.nav.klage.oppgave.clients.saf.graphql.*
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.BehandlingRole
import no.nav.klage.oppgave.domain.behandling.Klagebehandling
import no.nav.klage.oppgave.domain.behandling.embedded.Klager
import no.nav.klage.oppgave.domain.behandling.embedded.PartId
import no.nav.klage.oppgave.domain.behandling.embedded.SakenGjelder
import no.nav.klage.oppgave.service.DokDistKanalService
import no.nav.klage.oppgave.service.SaksbehandlerService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import no.nav.klage.kodeverk.Type as BehandlingType

class DokumentMapperTest {

    private lateinit var dokumentMapper: DokumentMapper
    private lateinit var saksbehandlerService: SaksbehandlerService
    private lateinit var behandlingMapper: BehandlingMapper
    private lateinit var dokumentUnderArbeidRepository: DokumentUnderArbeidRepository
    private lateinit var dokDistKanalService: DokDistKanalService

    @BeforeEach
    fun setup() {
        saksbehandlerService = mockk()
        behandlingMapper = mockk()
        dokumentUnderArbeidRepository = mockk()
        dokDistKanalService = mockk()

        dokumentMapper = DokumentMapper(
            saksbehandlerService = saksbehandlerService,
            behandlingMapper = behandlingMapper,
            dokumentUnderArbeidRepository = dokumentUnderArbeidRepository,
            dokDistKanalService = dokDistKanalService,
        )
    }

    @Nested
    inner class GetSortedDokumentViewListForInnholdsfortegnelseTest {

        private val behandlingId = UUID.randomUUID()
        private val parentId = UUID.randomUUID()

        private fun createKlagebehandling(): Klagebehandling {
            return Klagebehandling(
                fagsystem = Fagsystem.AO01,
                fagsakId = "123456",
                kildeReferanse = "abc",
                klager = Klager(
                    id = UUID.randomUUID(),
                    partId = PartId(PartIdType.PERSON, "12345678910")
                ),
                sakenGjelder = SakenGjelder(
                    id = UUID.randomUUID(),
                    partId = PartId(PartIdType.PERSON, "12345678910"),
                ),
                prosessfullmektig = null,
                mottattKlageinstans = LocalDateTime.now(),
                ytelse = Ytelse.OMS_OMP,
                type = BehandlingType.KLAGE,
                mottattVedtaksinstans = LocalDate.now(),
                avsenderEnhetFoersteinstans = "4100",
                kakaKvalitetsvurderingId = UUID.randomUUID(),
                kakaKvalitetsvurderingVersion = 2,
                frist = LocalDate.now().plusWeeks(12),
                previousSaksbehandlerident = null,
                gosysOppgaveId = null,
                varsletBehandlingstid = null,
                forlengetBehandlingstidDraft = null,
                gosysOppgaveRequired = false,
                initiatingSystem = Behandling.InitiatingSystem.KABAL,
                previousBehandlingId = null,
            )
        }

        private fun createHoveddokument(dokumentType: DokumentType = DokumentType.VEDTAK): SmartdokumentUnderArbeidAsHoveddokument {
            return SmartdokumentUnderArbeidAsHoveddokument(
                id = parentId,
                name = "Hoveddokument",
                behandlingId = behandlingId,
                created = LocalDateTime.now(),
                modified = LocalDateTime.now(),
                markertFerdig = null,
                markertFerdigBy = null,
                ferdigstilt = null,
                creatorIdent = "A12345",
                creatorRole = BehandlingRole.KABAL_SAKSBEHANDLING,
                dokumentType = dokumentType,
                size = 1000L,
                smartEditorId = UUID.randomUUID(),
                smartEditorTemplateId = "template1",
                mellomlagerId = null,
                mellomlagretDate = null,
                language = Language.NB,
                mellomlagretVersion = null,
                journalfoerendeEnhetId = "4100",
            )
        }

        private fun createOpplastetVedlegg(name: String, created: LocalDateTime): OpplastetDokumentUnderArbeidAsVedlegg {
            return OpplastetDokumentUnderArbeidAsVedlegg(
                id = UUID.randomUUID(),
                name = name,
                behandlingId = behandlingId,
                created = created,
                modified = created,
                markertFerdig = null,
                markertFerdigBy = null,
                ferdigstilt = null,
                creatorIdent = "A12345",
                creatorRole = BehandlingRole.KABAL_SAKSBEHANDLING,
                parentId = parentId,
                size = 500L,
                mellomlagerId = "mellomlagerId",
                mellomlagretDate = LocalDateTime.now(),
            )
        }

        private fun createJournalfoertVedlegg(
            journalpostId: String,
            dokumentInfoId: String,
            sortKey: String,
        ): JournalfoertDokumentUnderArbeidAsVedlegg {
            return JournalfoertDokumentUnderArbeidAsVedlegg(
                id = UUID.randomUUID(),
                name = "Journalført dokument",
                behandlingId = behandlingId,
                created = LocalDateTime.now(),
                modified = LocalDateTime.now(),
                markertFerdig = null,
                markertFerdigBy = null,
                ferdigstilt = null,
                creatorIdent = "A12345",
                creatorRole = BehandlingRole.KABAL_SAKSBEHANDLING,
                parentId = parentId,
                opprettet = LocalDateTime.now(),
                journalpostId = journalpostId,
                dokumentInfoId = dokumentInfoId,
                sortKey = sortKey,
            )
        }

        private fun createJournalpost(
            journalpostId: String,
            dokumentInfoId: String,
            tittel: String,
            journalposttype: Journalposttype,
            tema: Tema,
            avsenderMottakerNavn: String?,
            fagsakId: String?,
            datoSortering: LocalDateTime,
        ): Journalpost {
            return Journalpost(
                journalpostId = journalpostId,
                journalposttype = journalposttype,
                journalstatus = Journalstatus.JOURNALFOERT,
                tema = tema,
                sak = if (fagsakId != null) Sak(datoOpprettet = null, fagsakId = fagsakId, fagsaksystem = null) else null,
                bruker = Bruker(id = "12345678910", type = "FNR"),
                avsenderMottaker = if (avsenderMottakerNavn != null) AvsenderMottaker(id = null, type = null, navn = avsenderMottakerNavn, land = null, erLikBruker = false) else null,
                opprettetAvNavn = null,
                skjerming = null,
                datoOpprettet = datoSortering,
                datoSortering = datoSortering,
                dokumenter = listOf(
                    DokumentInfo(
                        dokumentInfoId = dokumentInfoId,
                        tittel = tittel,
                        brevkode = null,
                        skjerming = null,
                        logiskeVedlegg = null,
                        dokumentvarianter = emptyList(),
                        datoFerdigstilt = null,
                        originalJournalpostId = null,
                    )
                ),
                relevanteDatoer = null,
                kanal = "NAV_NO",
                kanalnavn = "nav.no",
                utsendingsinfo = null,
            )
        }

        @Test
        fun `should map opplastet vedlegg correctly with journalpostMetadataList`() {
            val behandling = createKlagebehandling()
            val hoveddokument = createHoveddokument()
            val opplastetVedlegg = createOpplastetVedlegg("Opplastet dokument", LocalDateTime.now())

            val result = dokumentMapper.getSortedDokumentViewListForInnholdsfortegnelse(
                vedlegg = setOf(opplastetVedlegg),
                behandling = behandling,
                hoveddokument = hoveddokument,
                journalpostList = emptyList(),
            )

            assertThat(result).hasSize(1)
            assertThat(result[0].tittel).isEqualTo("Opplastet dokument")
            assertThat(result[0].journalpostMetadataList).hasSize(1)
            assertThat(result[0].journalpostMetadataList[0].type).isEqualTo(Type.U)
            assertThat(result[0].journalpostMetadataList[0].saksnummer).isEqualTo("123456")
        }

        @Test
        fun `should map journalfoert vedlegg correctly with journalpostMetadataList`() {
            val behandling = createKlagebehandling()
            val hoveddokument = createHoveddokument()
            val journalfoertVedlegg = createJournalfoertVedlegg(
                journalpostId = "123",
                dokumentInfoId = "456",
                sortKey = "2024-01-01",
            )
            val journalpost = createJournalpost(
                journalpostId = "123",
                dokumentInfoId = "456",
                tittel = "Journalført dokument tittel",
                journalposttype = Journalposttype.I,
                tema = Tema.OMS,
                avsenderMottakerNavn = "Ola Nordmann",
                fagsakId = "789",
                datoSortering = LocalDateTime.of(2024, 1, 15, 12, 0),
            )

            val result = dokumentMapper.getSortedDokumentViewListForInnholdsfortegnelse(
                vedlegg = setOf(journalfoertVedlegg),
                behandling = behandling,
                hoveddokument = hoveddokument,
                journalpostList = listOf(journalpost),
            )

            assertThat(result).hasSize(1)
            assertThat(result[0].tittel).isEqualTo("Journalført dokument tittel")
            assertThat(result[0].journalpostMetadataList).hasSize(1)
            assertThat(result[0].journalpostMetadataList[0].type).isEqualTo(Type.I)
            assertThat(result[0].journalpostMetadataList[0].avsenderMottaker).isEqualTo("Ola Nordmann")
            assertThat(result[0].journalpostMetadataList[0].saksnummer).isEqualTo("789")
            assertThat(result[0].journalpostMetadataList[0].dato).isEqualTo(LocalDate.of(2024, 1, 15))
        }

        @Test
        fun `should group journalfoert documents with same dokumentInfoId into single Document with multiple JournalpostMetadata`() {
            val behandling = createKlagebehandling()
            val hoveddokument = createHoveddokument()

            // Two journalfoert documents with the same dokumentInfoId but different journalpostIds
            val journalfoertVedlegg1 = createJournalfoertVedlegg(
                journalpostId = "111",
                dokumentInfoId = "same-dok-id",
                sortKey = "2024-01-01",
            )
            val journalfoertVedlegg2 = createJournalfoertVedlegg(
                journalpostId = "222",
                dokumentInfoId = "same-dok-id",
                sortKey = "2024-01-02",
            )

            val journalpost1 = createJournalpost(
                journalpostId = "111",
                dokumentInfoId = "same-dok-id",
                tittel = "Felles dokument",
                journalposttype = Journalposttype.I,
                tema = Tema.OMS,
                avsenderMottakerNavn = "Avsender 1",
                fagsakId = "fagsakId1",
                datoSortering = LocalDateTime.of(2024, 1, 10, 12, 0),
            )
            val journalpost2 = createJournalpost(
                journalpostId = "222",
                dokumentInfoId = "same-dok-id",
                tittel = "Felles dokument",
                journalposttype = Journalposttype.U,
                tema = Tema.SYK,
                avsenderMottakerNavn = "Avsender 2",
                fagsakId = "fagsakId2",
                datoSortering = LocalDateTime.of(2024, 2, 15, 12, 0),
            )

            val result = dokumentMapper.getSortedDokumentViewListForInnholdsfortegnelse(
                vedlegg = setOf(journalfoertVedlegg1, journalfoertVedlegg2),
                behandling = behandling,
                hoveddokument = hoveddokument,
                journalpostList = listOf(journalpost1, journalpost2),
            )

            // Should be grouped into one Document with two JournalpostMetadata entries
            assertThat(result).hasSize(1)
            assertThat(result[0].tittel).isEqualTo("Felles dokument")
            assertThat(result[0].journalpostMetadataList).hasSize(2)

            // First metadata (sorted by sortKey, so journalfoertVedlegg1 comes first)
            assertThat(result[0].journalpostMetadataList[0].type).isEqualTo(Type.I)
            assertThat(result[0].journalpostMetadataList[0].avsenderMottaker).isEqualTo("Avsender 1")
            assertThat(result[0].journalpostMetadataList[0].saksnummer).isEqualTo("fagsakId1")

            // Second metadata
            assertThat(result[0].journalpostMetadataList[1].type).isEqualTo(Type.U)
            assertThat(result[0].journalpostMetadataList[1].avsenderMottaker).isEqualTo("Avsender 2")
            assertThat(result[0].journalpostMetadataList[1].saksnummer).isEqualTo("fagsakId2")
        }

        @Test
        fun `should NOT group journalfoert documents with different dokumentInfoId`() {
            val behandling = createKlagebehandling()
            val hoveddokument = createHoveddokument()

            val journalfoertVedlegg1 = createJournalfoertVedlegg(
                journalpostId = "111",
                dokumentInfoId = "dok-id-1",
                sortKey = "2024-01-01",
            )
            val journalfoertVedlegg2 = createJournalfoertVedlegg(
                journalpostId = "222",
                dokumentInfoId = "dok-id-2",
                sortKey = "2024-01-02",
            )

            val journalpost1 = createJournalpost(
                journalpostId = "111",
                dokumentInfoId = "dok-id-1",
                tittel = "Dokument 1",
                journalposttype = Journalposttype.I,
                tema = Tema.OMS,
                avsenderMottakerNavn = "Avsender 1",
                fagsakId = "fagsakId1",
                datoSortering = LocalDateTime.of(2024, 1, 10, 12, 0),
            )
            val journalpost2 = createJournalpost(
                journalpostId = "222",
                dokumentInfoId = "dok-id-2",
                tittel = "Dokument 2",
                journalposttype = Journalposttype.U,
                tema = Tema.SYK,
                avsenderMottakerNavn = "Avsender 2",
                fagsakId = "fagsakId2",
                datoSortering = LocalDateTime.of(2024, 2, 15, 12, 0),
            )

            val result = dokumentMapper.getSortedDokumentViewListForInnholdsfortegnelse(
                vedlegg = setOf(journalfoertVedlegg1, journalfoertVedlegg2),
                behandling = behandling,
                hoveddokument = hoveddokument,
                journalpostList = listOf(journalpost1, journalpost2),
            )

            // Should NOT be grouped - two separate Documents, each with one JournalpostMetadata
            assertThat(result).hasSize(2)
            assertThat(result[0].tittel).isEqualTo("Dokument 1")
            assertThat(result[0].journalpostMetadataList).hasSize(1)
            assertThat(result[1].tittel).isEqualTo("Dokument 2")
            assertThat(result[1].journalpostMetadataList).hasSize(1)
        }

        @Test
        fun `should place opplastet documents before journalfoert documents`() {
            val behandling = createKlagebehandling()
            val hoveddokument = createHoveddokument()

            val opplastetVedlegg = createOpplastetVedlegg("Opplastet", LocalDateTime.now())
            val journalfoertVedlegg = createJournalfoertVedlegg(
                journalpostId = "123",
                dokumentInfoId = "456",
                sortKey = "2024-01-01",
            )
            val journalpost = createJournalpost(
                journalpostId = "123",
                dokumentInfoId = "456",
                tittel = "Journalført",
                journalposttype = Journalposttype.I,
                tema = Tema.OMS,
                avsenderMottakerNavn = null,
                fagsakId = "789",
                datoSortering = LocalDateTime.now(),
            )

            val result = dokumentMapper.getSortedDokumentViewListForInnholdsfortegnelse(
                vedlegg = setOf(journalfoertVedlegg, opplastetVedlegg),
                behandling = behandling,
                hoveddokument = hoveddokument,
                journalpostList = listOf(journalpost),
            )

            assertThat(result).hasSize(2)
            assertThat(result[0].tittel).isEqualTo("Opplastet")
            assertThat(result[1].tittel).isEqualTo("Journalført")
        }

        @Test
        fun `should sort opplastet documents by created date descending`() {
            val behandling = createKlagebehandling()
            val hoveddokument = createHoveddokument()

            val older = createOpplastetVedlegg("Older", LocalDateTime.now().minusDays(2))
            val newer = createOpplastetVedlegg("Newer", LocalDateTime.now())
            val oldest = createOpplastetVedlegg("Oldest", LocalDateTime.now().minusDays(5))

            val result = dokumentMapper.getSortedDokumentViewListForInnholdsfortegnelse(
                vedlegg = setOf(older, newer, oldest),
                behandling = behandling,
                hoveddokument = hoveddokument,
                journalpostList = emptyList(),
            )

            assertThat(result).hasSize(3)
            assertThat(result[0].tittel).isEqualTo("Newer")
            assertThat(result[1].tittel).isEqualTo("Older")
            assertThat(result[2].tittel).isEqualTo("Oldest")
        }

        @Test
        fun `should use Type N for NOTAT document type`() {
            val behandling = createKlagebehandling()
            val hoveddokument = createHoveddokument(dokumentType = DokumentType.NOTAT)
            val opplastetVedlegg = createOpplastetVedlegg("Vedlegg til notat", LocalDateTime.now())

            val result = dokumentMapper.getSortedDokumentViewListForInnholdsfortegnelse(
                vedlegg = setOf(opplastetVedlegg),
                behandling = behandling,
                hoveddokument = hoveddokument,
                journalpostList = emptyList(),
            )

            assertThat(result).hasSize(1)
            assertThat(result[0].journalpostMetadataList[0].type).isEqualTo(Type.N)
        }

        @Test
        fun `should handle missing avsenderMottaker gracefully`() {
            val behandling = createKlagebehandling()
            val hoveddokument = createHoveddokument()
            val journalfoertVedlegg = createJournalfoertVedlegg(
                journalpostId = "123",
                dokumentInfoId = "456",
                sortKey = "2024-01-01",
            )
            val journalpost = createJournalpost(
                journalpostId = "123",
                dokumentInfoId = "456",
                tittel = "Dokument uten avsender",
                journalposttype = Journalposttype.I,
                tema = Tema.OMS,
                avsenderMottakerNavn = null,
                fagsakId = "789",
                datoSortering = LocalDateTime.now(),
            )

            val result = dokumentMapper.getSortedDokumentViewListForInnholdsfortegnelse(
                vedlegg = setOf(journalfoertVedlegg),
                behandling = behandling,
                hoveddokument = hoveddokument,
                journalpostList = listOf(journalpost),
            )

            assertThat(result).hasSize(1)
            assertThat(result[0].journalpostMetadataList[0].avsenderMottaker).isEqualTo("")
        }

        @Test
        fun `should handle missing fagsakId gracefully`() {
            val behandling = createKlagebehandling()
            val hoveddokument = createHoveddokument()
            val journalfoertVedlegg = createJournalfoertVedlegg(
                journalpostId = "123",
                dokumentInfoId = "456",
                sortKey = "2024-01-01",
            )
            val journalpost = createJournalpost(
                journalpostId = "123",
                dokumentInfoId = "456",
                tittel = "Dokument uten fagsakId",
                journalposttype = Journalposttype.I,
                tema = Tema.OMS,
                avsenderMottakerNavn = "Avsender",
                fagsakId = null,
                datoSortering = LocalDateTime.now(),
            )

            val result = dokumentMapper.getSortedDokumentViewListForInnholdsfortegnelse(
                vedlegg = setOf(journalfoertVedlegg),
                behandling = behandling,
                hoveddokument = hoveddokument,
                journalpostList = listOf(journalpost),
            )

            assertThat(result).hasSize(1)
            assertThat(result[0].journalpostMetadataList[0].saksnummer).isEqualTo("Saksnummer ikke funnet i SAF")
        }

        @Test
        fun `should group three documents with same dokumentInfoId`() {
            val behandling = createKlagebehandling()
            val hoveddokument = createHoveddokument()

            val journalfoertVedlegg1 = createJournalfoertVedlegg("jp1", "same-id", "2024-01-01")
            val journalfoertVedlegg2 = createJournalfoertVedlegg("jp2", "same-id", "2024-01-02")
            val journalfoertVedlegg3 = createJournalfoertVedlegg("jp3", "same-id", "2024-01-03")

            val journalpost1 = createJournalpost("jp1", "same-id", "Tittel", Journalposttype.I, Tema.OMS, "A1", "f1", LocalDateTime.of(2024, 1, 1, 0, 0))
            val journalpost2 = createJournalpost("jp2", "same-id", "Tittel", Journalposttype.U, Tema.SYK, "A2", "f2", LocalDateTime.of(2024, 2, 1, 0, 0))
            val journalpost3 = createJournalpost("jp3", "same-id", "Tittel", Journalposttype.N, Tema.FOR, "A3", "f3", LocalDateTime.of(2024, 3, 1, 0, 0))

            val result = dokumentMapper.getSortedDokumentViewListForInnholdsfortegnelse(
                vedlegg = setOf(journalfoertVedlegg1, journalfoertVedlegg2, journalfoertVedlegg3),
                behandling = behandling,
                hoveddokument = hoveddokument,
                journalpostList = listOf(journalpost1, journalpost2, journalpost3),
            )

            assertThat(result).hasSize(1)
            assertThat(result[0].journalpostMetadataList).hasSize(3)
            assertThat(result[0].journalpostMetadataList[0].type).isEqualTo(Type.I)
            assertThat(result[0].journalpostMetadataList[1].type).isEqualTo(Type.U)
            assertThat(result[0].journalpostMetadataList[2].type).isEqualTo(Type.N)
        }

        @Test
        fun `should sort journalpostMetadataList with oldest documents first based on sortKey`() {
            val behandling = createKlagebehandling()
            val hoveddokument = createHoveddokument()

            // Create documents with sortKeys in non-chronological order to verify sorting
            val newestVedlegg = createJournalfoertVedlegg("jp-newest", "same-id", "2024-03-15")
            val oldestVedlegg = createJournalfoertVedlegg("jp-oldest", "same-id", "2024-01-01")
            val middleVedlegg = createJournalfoertVedlegg("jp-middle", "same-id", "2024-02-10")

            val newestJournalpost = createJournalpost(
                journalpostId = "jp-newest",
                dokumentInfoId = "same-id",
                tittel = "Felles dokument",
                journalposttype = Journalposttype.U,
                tema = Tema.OMS,
                avsenderMottakerNavn = "Newest",
                fagsakId = "f3",
                datoSortering = LocalDateTime.of(2024, 3, 15, 12, 0),
            )
            val oldestJournalpost = createJournalpost(
                journalpostId = "jp-oldest",
                dokumentInfoId = "same-id",
                tittel = "Felles dokument",
                journalposttype = Journalposttype.I,
                tema = Tema.OMS,
                avsenderMottakerNavn = "Oldest",
                fagsakId = "f1",
                datoSortering = LocalDateTime.of(2024, 1, 1, 12, 0),
            )
            val middleJournalpost = createJournalpost(
                journalpostId = "jp-middle",
                dokumentInfoId = "same-id",
                tittel = "Felles dokument",
                journalposttype = Journalposttype.N,
                tema = Tema.OMS,
                avsenderMottakerNavn = "Middle",
                fagsakId = "f2",
                datoSortering = LocalDateTime.of(2024, 2, 10, 12, 0),
            )

            val result = dokumentMapper.getSortedDokumentViewListForInnholdsfortegnelse(
                vedlegg = setOf(newestVedlegg, oldestVedlegg, middleVedlegg),
                behandling = behandling,
                hoveddokument = hoveddokument,
                journalpostList = listOf(newestJournalpost, oldestJournalpost, middleJournalpost),
            )

            assertThat(result).hasSize(1)
            assertThat(result[0].journalpostMetadataList).hasSize(3)

            // Verify oldest comes first, then middle, then newest
            assertThat(result[0].journalpostMetadataList[0].avsenderMottaker).isEqualTo("Oldest")
            assertThat(result[0].journalpostMetadataList[0].dato).isEqualTo(LocalDate.of(2024, 1, 1))

            assertThat(result[0].journalpostMetadataList[1].avsenderMottaker).isEqualTo("Middle")
            assertThat(result[0].journalpostMetadataList[1].dato).isEqualTo(LocalDate.of(2024, 2, 10))

            assertThat(result[0].journalpostMetadataList[2].avsenderMottaker).isEqualTo("Newest")
            assertThat(result[0].journalpostMetadataList[2].dato).isEqualTo(LocalDate.of(2024, 3, 15))
        }

        @Test
        fun `should sort journalfoert documents with different dokumentInfoId by sortKey with oldest first`() {
            val behandling = createKlagebehandling()
            val hoveddokument = createHoveddokument()

            // Create documents with different dokumentInfoIds in non-chronological order
            val newestVedlegg = createJournalfoertVedlegg("jp1", "dok-id-newest", "2024-03-01")
            val oldestVedlegg = createJournalfoertVedlegg("jp2", "dok-id-oldest", "2024-01-01")
            val middleVedlegg = createJournalfoertVedlegg("jp3", "dok-id-middle", "2024-02-01")

            val newestJournalpost = createJournalpost("jp1", "dok-id-newest", "Newest Doc", Journalposttype.I, Tema.OMS, "A1", "f1", LocalDateTime.of(2024, 3, 1, 0, 0))
            val oldestJournalpost = createJournalpost("jp2", "dok-id-oldest", "Oldest Doc", Journalposttype.I, Tema.OMS, "A2", "f2", LocalDateTime.of(2024, 1, 1, 0, 0))
            val middleJournalpost = createJournalpost("jp3", "dok-id-middle", "Middle Doc", Journalposttype.I, Tema.OMS, "A3", "f3", LocalDateTime.of(2024, 2, 1, 0, 0))

            val result = dokumentMapper.getSortedDokumentViewListForInnholdsfortegnelse(
                vedlegg = setOf(newestVedlegg, oldestVedlegg, middleVedlegg),
                behandling = behandling,
                hoveddokument = hoveddokument,
                journalpostList = listOf(newestJournalpost, oldestJournalpost, middleJournalpost),
            )

            // Should have 3 separate documents, sorted with oldest first
            assertThat(result).hasSize(3)
            assertThat(result[0].tittel).isEqualTo("Oldest Doc")
            assertThat(result[1].tittel).isEqualTo("Middle Doc")
            assertThat(result[2].tittel).isEqualTo("Newest Doc")
        }

        @Test
        fun `should handle mixed document types with grouped journalfoert documents correctly`() {
            val behandling = createKlagebehandling()
            val hoveddokument = createHoveddokument()

            // Create opplastet documents (newest first when sorted descending by created)
            val opplastet1 = createOpplastetVedlegg("Opplastet Nyeste", LocalDateTime.now())
            val opplastet2 = createOpplastetVedlegg("Opplastet Eldre", LocalDateTime.now().minusDays(1))

            // Create journalført documents - two with same dokumentInfoId (should be grouped)
            val journalfoertGrouped1 = createJournalfoertVedlegg("jp-grouped-1", "grouped-dok-id", "2024-01-15")
            val journalfoertGrouped2 = createJournalfoertVedlegg("jp-grouped-2", "grouped-dok-id", "2024-02-20")

            // Create journalført document with unique dokumentInfoId (should NOT be grouped)
            val journalfoertUnique = createJournalfoertVedlegg("jp-unique", "unique-dok-id", "2024-01-10")

            // Create journalposts
            val journalpostGrouped1 = createJournalpost(
                journalpostId = "jp-grouped-1",
                dokumentInfoId = "grouped-dok-id",
                tittel = "Gruppert Dokument",
                journalposttype = Journalposttype.I,
                tema = Tema.OMS,
                avsenderMottakerNavn = "Grouped Sender 1",
                fagsakId = "fagsak-grouped-1",
                datoSortering = LocalDateTime.of(2024, 1, 15, 10, 0),
            )
            val journalpostGrouped2 = createJournalpost(
                journalpostId = "jp-grouped-2",
                dokumentInfoId = "grouped-dok-id",
                tittel = "Gruppert Dokument",
                journalposttype = Journalposttype.U,
                tema = Tema.SYK,
                avsenderMottakerNavn = "Grouped Sender 2",
                fagsakId = "fagsak-grouped-2",
                datoSortering = LocalDateTime.of(2024, 2, 20, 10, 0),
            )
            val journalpostUnique = createJournalpost(
                journalpostId = "jp-unique",
                dokumentInfoId = "unique-dok-id",
                tittel = "Unikt Dokument",
                journalposttype = Journalposttype.N,
                tema = Tema.FOR,
                avsenderMottakerNavn = "Unique Sender",
                fagsakId = "fagsak-unique",
                datoSortering = LocalDateTime.of(2024, 1, 10, 10, 0),
            )

            val result = dokumentMapper.getSortedDokumentViewListForInnholdsfortegnelse(
                vedlegg = setOf(opplastet1, opplastet2, journalfoertGrouped1, journalfoertGrouped2, journalfoertUnique),
                behandling = behandling,
                hoveddokument = hoveddokument,
                journalpostList = listOf(journalpostGrouped1, journalpostGrouped2, journalpostUnique),
            )

            // Expected order:
            // 1. Opplastet documents first (sorted by created descending - newest first)
            // 2. Journalført documents after (sorted by sortKey ascending - oldest first)
            //    - unique-dok-id (sortKey: 2024-01-10) comes before grouped-dok-id (sortKey: 2024-01-15)

            // Total: 4 Documents (2 opplastet + 1 grouped journalført + 1 unique journalført)
            assertThat(result).hasSize(4)

            // First two should be opplastet (newest first)
            assertThat(result[0].tittel).isEqualTo("Opplastet Nyeste")
            assertThat(result[0].journalpostMetadataList).hasSize(1)
            assertThat(result[0].journalpostMetadataList[0].type).isEqualTo(Type.U)

            assertThat(result[1].tittel).isEqualTo("Opplastet Eldre")
            assertThat(result[1].journalpostMetadataList).hasSize(1)
            assertThat(result[1].journalpostMetadataList[0].type).isEqualTo(Type.U)

            // Third should be the unique journalført (sortKey 2024-01-10 is oldest)
            assertThat(result[2].tittel).isEqualTo("Unikt Dokument")
            assertThat(result[2].journalpostMetadataList).hasSize(1)
            assertThat(result[2].journalpostMetadataList[0].type).isEqualTo(Type.N)
            assertThat(result[2].journalpostMetadataList[0].avsenderMottaker).isEqualTo("Unique Sender")

            // Fourth should be the grouped journalført (with 2 metadata entries, sorted by sortKey)
            assertThat(result[3].tittel).isEqualTo("Gruppert Dokument")
            assertThat(result[3].journalpostMetadataList).hasSize(2)

            // First metadata entry should be from the oldest sortKey (2024-01-15)
            assertThat(result[3].journalpostMetadataList[0].type).isEqualTo(Type.I)
            assertThat(result[3].journalpostMetadataList[0].avsenderMottaker).isEqualTo("Grouped Sender 1")
            assertThat(result[3].journalpostMetadataList[0].dato).isEqualTo(LocalDate.of(2024, 1, 15))

            // Second metadata entry should be from the newer sortKey (2024-02-20)
            assertThat(result[3].journalpostMetadataList[1].type).isEqualTo(Type.U)
            assertThat(result[3].journalpostMetadataList[1].avsenderMottaker).isEqualTo("Grouped Sender 2")
            assertThat(result[3].journalpostMetadataList[1].dato).isEqualTo(LocalDate.of(2024, 2, 20))
        }
    }
}