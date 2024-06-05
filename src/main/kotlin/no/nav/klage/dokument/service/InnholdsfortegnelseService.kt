package no.nav.klage.dokument.service

import no.nav.klage.dokument.api.mapper.DokumentMapper
import no.nav.klage.dokument.clients.kabaljsontopdf.KabalJsonToPdfClient
import no.nav.klage.dokument.clients.kabaljsontopdf.domain.InnholdsfortegnelseRequest
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsHoveddokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsVedlegg
import no.nav.klage.dokument.domain.dokumenterunderarbeid.Innholdsfortegnelse
import no.nav.klage.dokument.domain.dokumenterunderarbeid.JournalfoertDokumentUnderArbeidAsVedlegg
import no.nav.klage.dokument.repositories.DokumentUnderArbeidRepository
import no.nav.klage.dokument.repositories.InnholdsfortegnelseRepository
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.oppgave.clients.saf.SafFacade
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.util.getLogger
import org.springframework.core.io.ByteArrayResource
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
@Transactional
class InnholdsfortegnelseService(
    private val dokumentUnderArbeidRepository: DokumentUnderArbeidRepository,
    private val dokumentUnderArbeidCommonService: DokumentUnderArbeidCommonService,
    private val dokumentMapper: DokumentMapper,
    private val mellomlagerService: MellomlagerService,
    private val kabalJsonToPdfClient: KabalJsonToPdfClient,
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
        dokumentUnderArbeidId: UUID,
        fnr: String,
    ) {
        logger.debug("Received saveInnholdsfortegnelse")

        val content = getInnholdsfortegnelseAsPdf(
            dokumentUnderArbeidId = dokumentUnderArbeidId,
            fnr = fnr
        )

        val mellomlagerId =
            mellomlagerService.uploadResource(
                resource = ByteArrayResource(content),
            )

        innholdsfortegnelseRepository.save(
            Innholdsfortegnelse(
                mellomlagerId = mellomlagerId,
                hoveddokumentId = dokumentUnderArbeidId,
                created = LocalDateTime.now(),
                modified = LocalDateTime.now(),
            )
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun getInnholdsfortegnelseAsPdf(dokumentUnderArbeidId: UUID, fnr: String): ByteArray {
        logger.debug("Received getInnholdsfortegnelseAsPdf")

        val document = dokumentUnderArbeidRepository.findById(dokumentUnderArbeidId).get()

        if (document is DokumentUnderArbeidAsVedlegg) {
            throw IllegalArgumentException("must be hoveddokument")
        }

        document as DokumentUnderArbeidAsHoveddokument

        val vedlegg = dokumentUnderArbeidCommonService.findVedleggByParentId(dokumentUnderArbeidId)

        if (document.dokumentType in listOf(DokumentType.BREV, DokumentType.VEDTAK, DokumentType.BESLUTNING)) {
            if (vedlegg.any { it !is JournalfoertDokumentUnderArbeidAsVedlegg }) {
                error("All documents must be JournalfoertDokumentUnderArbeidAsVedlegg")
            }
        }

        val journalpostList = safFacade.getJournalposter(
            journalpostIdSet = vedlegg.filterIsInstance<JournalfoertDokumentUnderArbeidAsVedlegg>()
                .map { it.journalpostId }.toSet(),
            fnr = fnr,
            saksbehandlerContext = true,
        )

        val pdfDocument =
            kabalJsonToPdfClient.getInnholdsfortegnelse(
                InnholdsfortegnelseRequest(
                    documents = dokumentMapper.getSortedDokumentViewListForInnholdsfortegnelse(
                        allDokumenterUnderArbeid = vedlegg,
                        behandling = behandlingService.getBehandlingForReadWithoutCheckForAccess(document.behandlingId),
                        hoveddokument = document,
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