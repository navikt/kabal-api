package no.nav.klage.dokument.service

import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsHoveddokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsVedlegg
import no.nav.klage.dokument.repositories.JournalfoertDokumentUnderArbeidAsVedleggRepository
import no.nav.klage.dokument.repositories.OpplastetDokumentUnderArbeidAsHoveddokumentRepository
import no.nav.klage.dokument.repositories.OpplastetDokumentUnderArbeidAsVedleggRepository
import no.nav.klage.dokument.repositories.SmartdokumentUnderArbeidAsHoveddokumentRepository
import no.nav.klage.dokument.repositories.SmartdokumentUnderArbeidAsVedleggRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class DokumentUnderArbeidCommonService(
    private val opplastetDokumentUnderArbeidAsVedleggRepository: OpplastetDokumentUnderArbeidAsVedleggRepository,
    private val journalfoertDokumentUnderArbeidAsVedleggRepository: JournalfoertDokumentUnderArbeidAsVedleggRepository,
    private val smartdokumentUnderArbeidAsVedleggRepository: SmartdokumentUnderArbeidAsVedleggRepository,
    private val opplastetDokumentUnderArbeidAsHoveddokumentRepository: OpplastetDokumentUnderArbeidAsHoveddokumentRepository,
    private val smartdokumentUnderArbeidAsHoveddokumentRepository: SmartdokumentUnderArbeidAsHoveddokumentRepository,
) {
    fun findVedleggByParentId(parentId: UUID): Set<DokumentUnderArbeidAsVedlegg> =
        opplastetDokumentUnderArbeidAsVedleggRepository.findByParentId(parentId) +
            journalfoertDokumentUnderArbeidAsVedleggRepository.findByParentId(parentId) +
            smartdokumentUnderArbeidAsVedleggRepository.findByParentId(parentId)

    fun findHoveddokumenterByBehandlingIdAndHasJournalposter(behandlingId: UUID): Set<DokumentUnderArbeidAsHoveddokument> =
        opplastetDokumentUnderArbeidAsHoveddokumentRepository.findByBehandlingIdAndDokarkivReferencesIsNotEmpty(
            behandlingId,
        ) + smartdokumentUnderArbeidAsHoveddokumentRepository.findByBehandlingIdAndDokarkivReferencesIsNotEmpty(behandlingId)

    fun findHoveddokumenterByMarkertFerdigNotNullAndFerdigstiltNull(): Set<DokumentUnderArbeidAsHoveddokument> =
        opplastetDokumentUnderArbeidAsHoveddokumentRepository.findByMarkertFerdigNotNullAndFerdigstiltNull() +
            smartdokumentUnderArbeidAsHoveddokumentRepository.findByMarkertFerdigNotNullAndFerdigstiltNull()

    fun findHoveddokumenterOnBehandlingByMarkertFerdigNotNullAndFerdigstiltNull(
        behandlingId: UUID,
    ): Set<DokumentUnderArbeidAsHoveddokument> =
        opplastetDokumentUnderArbeidAsHoveddokumentRepository.findByMarkertFerdigNotNullAndFerdigstiltNullAndBehandlingId(behandlingId) +
            smartdokumentUnderArbeidAsHoveddokumentRepository.findByMarkertFerdigNotNullAndFerdigstiltNullAndBehandlingId(behandlingId)
}
