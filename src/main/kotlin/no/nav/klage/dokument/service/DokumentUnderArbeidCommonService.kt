package no.nav.klage.dokument.service

import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsHoveddokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsVedlegg
import no.nav.klage.dokument.repositories.*
import org.springframework.stereotype.Service
import java.util.*

@Service
class DokumentUnderArbeidCommonService(
    private val opplastetDokumentUnderArbeidAsVedleggRepository: OpplastetDokumentUnderArbeidAsVedleggRepository,
    private val journalfoertDokumentUnderArbeidAsVedleggRepository: JournalfoertDokumentUnderArbeidAsVedleggRepository,
    private val smartdokumentUnderArbeidAsVedleggRepository: SmartdokumentUnderArbeidAsVedleggRepository,
    private val opplastetDokumentUnderArbeidAsHoveddokumentRepository: OpplastetDokumentUnderArbeidAsHoveddokumentRepository,
    private val smartdokumentUnderArbeidAsHoveddokumentRepository: SmartdokumentUnderArbeidAsHoveddokumentRepository,
) {

    fun findVedleggByParentId(parentId: UUID): Set<DokumentUnderArbeidAsVedlegg> {
        return opplastetDokumentUnderArbeidAsVedleggRepository.findByParentId(parentId) +
                journalfoertDokumentUnderArbeidAsVedleggRepository.findByParentId(parentId) +
                smartdokumentUnderArbeidAsVedleggRepository.findByParentId(parentId)
    }

//    fun findHoveddokumenterByBehandlingId(behandlingId: UUID): Set<DokumentUnderArbeid> {
//        return opplastetDokumentUnderArbeidAsHoveddokumentRepository.findByBehandlingId(behandlingId) +
//                smartdokumentUnderArbeidAsHoveddokumentRepository.findByBehandlingId(behandlingId)
//    }

    fun findHoveddokumenterByBehandlingIdAndHasJournalposter(behandlingId: UUID): Set<DokumentUnderArbeidAsHoveddokument> {
        return opplastetDokumentUnderArbeidAsHoveddokumentRepository.findByBehandlingIdAndJournalposterIsNotEmpty(
            behandlingId
        ) + smartdokumentUnderArbeidAsHoveddokumentRepository.findByBehandlingIdAndJournalposterIsNotEmpty(behandlingId)
    }

    fun findHoveddokumenterByMarkertFerdigNotNullAndFerdigstiltNull(): Set<DokumentUnderArbeidAsHoveddokument> {
        return opplastetDokumentUnderArbeidAsHoveddokumentRepository.findByMarkertFerdigNotNullAndFerdigstiltNull() +
                smartdokumentUnderArbeidAsHoveddokumentRepository.findByMarkertFerdigNotNullAndFerdigstiltNull()
    }

}