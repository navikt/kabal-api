package no.nav.klage.dokument.service

import no.nav.klage.dokument.api.mapper.DokumentMapper
import no.nav.klage.dokument.clients.kabaljsontopdf.domain.InnholdsfortegnelseRequest
import no.nav.klage.dokument.domain.dokumenterunderarbeid.*
import no.nav.klage.dokument.repositories.InnholdsfortegnelseRepository
import no.nav.klage.oppgave.clients.saf.SafFacade
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.util.getLogger
import org.springframework.core.io.ByteArrayResource
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@Service
@Transactional
class InnholdsfortegnelseService(
    private val dokumentUnderArbeidCommonService: DokumentUnderArbeidCommonService,
    private val dokumentMapper: DokumentMapper,
    private val mellomlagerService: MellomlagerService,
    private val kabalJsonToPdfService: KabalJsonToPdfService,
    private val innholdsfortegnelseRepository: InnholdsfortegnelseRepository,
    private val behandlingService: BehandlingService,
    private val safFacade: SafFacade,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun getInnholdsfortegnelse(hoveddokumentId: UUID): Innholdsfortegnelse? {
        return innholdsfortegnelseRepository.findByHoveddokumentId(hoveddokumentId)
    }

    fun saveInnholdsfortegnelse(
        dokumentUnderArbeid: DokumentUnderArbeid,
        fnr: String,
    ) {
        logger.debug("Received saveInnholdsfortegnelse")

        val content = getInnholdsfortegnelseAsPdf(
            dokumentUnderArbeid = dokumentUnderArbeid,
            fnr = fnr
        )

        val mellomlagerId =
            mellomlagerService.uploadResource(
                resource = ByteArrayResource(content),
            )

        innholdsfortegnelseRepository.save(
            Innholdsfortegnelse(
                mellomlagerId = mellomlagerId,
                hoveddokumentId = dokumentUnderArbeid.id,
                created = LocalDateTime.now(),
                modified = LocalDateTime.now(),
            )
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun getInnholdsfortegnelseAsPdf(dokumentUnderArbeid: DokumentUnderArbeid, fnr: String): ByteArray {
        logger.debug("Received getInnholdsfortegnelseAsPdf")

        if (dokumentUnderArbeid is DokumentUnderArbeidAsVedlegg) {
            throw IllegalArgumentException("must be hoveddokument")
        }

        dokumentUnderArbeid as DokumentUnderArbeidAsHoveddokument

        val vedlegg = dokumentUnderArbeidCommonService.findVedleggByParentId(dokumentUnderArbeid.id)

        val journalpostList = safFacade.getJournalposter(
            journalpostIdSet = vedlegg.filterIsInstance<JournalfoertDokumentUnderArbeidAsVedlegg>()
                .map { it.journalpostId }.toSet(),
            fnr = fnr,
            saksbehandlerContext = true,
        )

        val pdfDocument =
            kabalJsonToPdfService.getInnholdsfortegnelse(
                InnholdsfortegnelseRequest(
                    parentTitle = dokumentUnderArbeid.name,
                    parentDate = LocalDate.now(),
                    documents = dokumentMapper.getSortedDokumentViewListForInnholdsfortegnelse(
                        vedlegg = vedlegg,
                        behandling = behandlingService.getBehandlingForReadWithoutCheckForAccess(dokumentUnderArbeid.behandlingId),
                        hoveddokument = dokumentUnderArbeid,
                        journalpostList = journalpostList,
                    )
                )
            )

        return pdfDocument.bytes
    }

    fun deleteInnholdsfortegnelse(hoveddokumentId: UUID) {
        val innholdsfortegnelse = innholdsfortegnelseRepository.findByHoveddokumentId(hoveddokumentId)
        if (innholdsfortegnelse != null) {
            innholdsfortegnelseRepository.delete(innholdsfortegnelse)
        }
    }
}