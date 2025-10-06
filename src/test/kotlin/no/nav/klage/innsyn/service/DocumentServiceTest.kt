package no.nav.klage.innsyn.service

import io.mockk.every
import io.mockk.mockk
import no.nav.klage.dokument.domain.dokumenterunderarbeid.Brevmottaker
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidDokarkivReference
import no.nav.klage.dokument.domain.dokumenterunderarbeid.Language
import no.nav.klage.dokument.domain.dokumenterunderarbeid.SmartdokumentUnderArbeidAsHoveddokument
import no.nav.klage.dokument.service.DokumentUnderArbeidCommonService
import no.nav.klage.innsyn.client.safselvbetjening.*
import no.nav.klage.kodeverk.*
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.BehandlingRole
import no.nav.klage.oppgave.domain.behandling.Klagebehandling
import no.nav.klage.oppgave.domain.behandling.embedded.Klager
import no.nav.klage.oppgave.domain.behandling.embedded.PartId
import no.nav.klage.oppgave.domain.behandling.embedded.SakenGjelder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal class DocumentServiceTest {
    private val safSelvbetjeningGraphQlClient = mockk<SafSelvbetjeningGraphQlClient>()
    private val safSelvbetjeningRestClient = mockk<SafSelvbetjeningRestClient>()
    private val dokumentUnderArbeidCommonService = mockk<DokumentUnderArbeidCommonService>()

    private val documentService: DocumentService = DocumentService(
        safSelvbetjeningGraphQlClient = safSelvbetjeningGraphQlClient,
        safSelvbetjeningRestClient = safSelvbetjeningRestClient,
        dokumentUnderArbeidCommonService = dokumentUnderArbeidCommonService,
    )

    private val SAKEN_GJELDER_ID = "SAKEN_GJELDER_ID"
    private val FULLMEKTIG_ID = "FULLMEKTIG_ID"
    private val JOURNALPOST_ID = "JOURNALPOST_ID"
    private val DATE = LocalDate.now()


    @Test
    fun `getSvarbrev() with saken gjelder as only recipient`() {
        every { dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(any()) } returns mutableSetOf(
            getHovedDokument(mottagerList = listOf(SAKEN_GJELDER_ID), dokumentType = DokumentType.SVARBREV),
            getHovedDokument(mottagerList = listOf(SAKEN_GJELDER_ID), dokumentType = DokumentType.VEDTAK)
        )

        val output = documentService.getSvarbrev(behandling = getBehandling())
        assertThat(output!!.journalpostId).isEqualTo(SAKEN_GJELDER_ID + JOURNALPOST_ID)
        assertThat(output.title).isEqualTo(DokumentType.SVARBREV.name)
        assertThat(output.archiveDate).isEqualTo(DATE)
    }

    @Test
    fun `getSvarbrev() with no svarbrev returns null`() {
        every { dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(any()) } returns mutableSetOf()

        val output = documentService.getSvarbrev(behandling = getBehandling())
        assertThat(output).isEqualTo(null)
    }

    @Test
    fun `getSvarbrev() with multiple recipients returns saken gjelder`() {
        every { dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(any()) } returns mutableSetOf(
            getHovedDokument(
                mottagerList = listOf(FULLMEKTIG_ID, SAKEN_GJELDER_ID),
                dokumentType = DokumentType.SVARBREV
            ),
        )

        every { safSelvbetjeningGraphQlClient.getJournalpostById(journalpostId = SAKEN_GJELDER_ID + JOURNALPOST_ID) } returns
                GetJournalpostByIdResponse(
                    data = GetJournalpostById(
                        journalpostById = JournalpostById(
                            journalpostId = SAKEN_GJELDER_ID + JOURNALPOST_ID,
                            tittel = "",
                            dokumenter = listOf()
                        )
                    ),
                    errors = listOf()
                )

        every { safSelvbetjeningGraphQlClient.getJournalpostById(journalpostId = FULLMEKTIG_ID + JOURNALPOST_ID) } returns
                GetJournalpostByIdResponse(
                    data = null,
                    errors = listOf(
                        PdlError(
                            message = "Annen part enn innlogget bruker.",
                            locations = listOf(),
                            path = listOf(),
                            extensions = PdlErrorExtension(code = "unauthorized", classification = "")

                        )
                    )
                )


        val output = documentService.getSvarbrev(behandling = getBehandling())
        assertThat(output!!.journalpostId).isEqualTo(SAKEN_GJELDER_ID + JOURNALPOST_ID)
        assertThat(output.title).isEqualTo(DokumentType.SVARBREV.name)
        assertThat(output.archiveDate).isEqualTo(DATE)
    }

    @Test
    fun `getSvarbrev() with one recipient without saken gjelder returns without journalpostId`() {
        every { dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(any()) } returns mutableSetOf(
            getHovedDokument(mottagerList = listOf(FULLMEKTIG_ID), dokumentType = DokumentType.SVARBREV),
        )

        val output = documentService.getSvarbrev(behandling = getBehandling())
        assertThat(output!!.journalpostId).isEqualTo(null)
        assertThat(output.title).isEqualTo(DokumentType.SVARBREV.name)
        assertThat(output.archiveDate).isEqualTo(DATE)
    }

    @Test
    fun `getSvarbrev() with several recipients without saken gjelder returns without journalpostId`() {
        every { dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(any()) } returns mutableSetOf(
            getHovedDokument(
                mottagerList = listOf(FULLMEKTIG_ID, FULLMEKTIG_ID + "_2"),
                dokumentType = DokumentType.SVARBREV
            ),
        )

        val output = documentService.getSvarbrev(behandling = getBehandling())
        assertThat(output!!.journalpostId).isEqualTo(null)
        assertThat(output.title).isEqualTo(DokumentType.SVARBREV.name)
        assertThat(output.archiveDate).isEqualTo(DATE)
    }

    private fun getHovedDokument(
        mottagerList: List<String>,
        dokumentType: DokumentType
    ): SmartdokumentUnderArbeidAsHoveddokument {
        return SmartdokumentUnderArbeidAsHoveddokument(
            dokumentEnhetId = null,
            avsenderMottakerInfoSet = mottagerList.map {
                Brevmottaker(
                    technicalPartId = UUID.randomUUID(),
                    identifikator = it,
                    localPrint = false,
                    forceCentralPrint = false,
                    address = null,
                    navn = null
                )
            }.toMutableSet(),
            journalfoerendeEnhetId = null,
            dokumentType = dokumentType,
            name = dokumentType.name,
            behandlingId = UUID.randomUUID(),
            created = LocalDateTime.now(),
            modified = LocalDateTime.now(),
            markertFerdig = null,
            markertFerdigBy = null,
            ferdigstilt = DATE.atStartOfDay(),
            creatorIdent = "",
            creatorRole = BehandlingRole.KABAL_SAKSBEHANDLING,

            dokarkivReferences = mottagerList.map {
                DokumentUnderArbeidDokarkivReference(
                    journalpostId = it + JOURNALPOST_ID,
                    dokumentInfoId = null

                )
            }.toMutableSet(),
            size = null,
            smartEditorId = UUID.randomUUID(),
            smartEditorTemplateId = "",
            mellomlagerId = null,
            mellomlagretDate = null,
            language = Language.NB,
            mellomlagretVersion = null,
        )
    }

    private fun getBehandling(): Behandling {
        return Klagebehandling(
            mottattVedtaksinstans = LocalDate.now(),
            avsenderEnhetFoersteinstans = "",
            kommentarFraFoersteinstans = null,
            kakaKvalitetsvurderingId = null,
            kakaKvalitetsvurderingVersion = 0,
            varsletBehandlingstid = null,
            klager = Klager(
                id = UUID.randomUUID(),
                partId = PartId(
                    type = PartIdType.PERSON,
                    value = SAKEN_GJELDER_ID,
                )
            ),
            sakenGjelder = SakenGjelder(
                id = UUID.randomUUID(),
                partId = PartId(
                    type = PartIdType.PERSON,
                    value = SAKEN_GJELDER_ID,
                )
            ),
            prosessfullmektig = null,
            ytelse = Ytelse.OMS_OMP,
            type = Type.KLAGE,
            kildeReferanse = "",
            dvhReferanse = null,
            fagsystem = Fagsystem.K9,
            fagsakId = "",
            mottattKlageinstans = LocalDateTime.now(),
            frist = LocalDate.now(),
            tildeling = null,
            created = LocalDateTime.now(),
            modified = LocalDateTime.now(),
            saksdokumenter = mutableSetOf(),
            hjemler = setOf(),
            sattPaaVent = null,
            feilregistrering = null,
            utfall = null,
            extraUtfallSet = setOf(),
            registreringshjemler = mutableSetOf(),
            medunderskriver = null,
            medunderskriverFlowState = FlowState.NOT_SENT,
            ferdigstilling = null,
            rolIdent = null,
            rolFlowState = FlowState.NOT_SENT,
            rolReturnedDate = null,
            tildelingHistorikk = mutableSetOf(),
            medunderskriverHistorikk = mutableSetOf(),
            rolHistorikk = mutableSetOf(),
            klagerHistorikk = mutableSetOf(),
            fullmektigHistorikk = mutableSetOf(),
            sattPaaVentHistorikk = mutableSetOf(),
            previousSaksbehandlerident = null,
            gosysOppgaveId = null,
            gosysOppgaveUpdate = null,
            tilbakekreving = false,
            ignoreGosysOppgave = false,
            forlengetBehandlingstidDraft = null,
            gosysOppgaveRequired = false,
            initiatingSystem = Behandling.InitiatingSystem.KABAL,
        )
    }
}