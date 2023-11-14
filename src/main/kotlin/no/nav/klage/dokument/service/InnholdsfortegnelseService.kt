package no.nav.klage.dokument.service

import no.nav.klage.dokument.api.mapper.DokumentMapper
import no.nav.klage.dokument.clients.kabaljsontopdf.KabalJsonToPdfClient
import no.nav.klage.dokument.clients.kabaljsontopdf.domain.InnholdsfortegnelseRequest
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsVedlegg
import no.nav.klage.dokument.domain.dokumenterunderarbeid.Innholdsfortegnelse
import no.nav.klage.dokument.repositories.DokumentUnderArbeidRepository
import no.nav.klage.dokument.repositories.InnholdsfortegnelseRepository
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.util.getLogger
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
    ) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun getInnholdsfortegnelse(hoveddokumentId: UUID): Innholdsfortegnelse? {
        return innholdsfortegnelseRepository.findByHoveddokumentId(hoveddokumentId)
    }

    fun saveInnholdsfortegnelse(dokumentUnderArbeidId: UUID, mottakere: List<String>) {
        logger.debug("Received saveInnholdsfortegnelse")

        val content = getInnholdsfortegnelseAsPdf(dokumentUnderArbeidId = dokumentUnderArbeidId, mottakere = mottakere)

        val mellomlagerId =
            mellomlagerService.uploadByteArray(
                tittel = "Innholdsfortegnelse",
                content = content,
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

    fun getInnholdsfortegnelseAsPdf(dokumentUnderArbeidId: UUID, mottakere: List<String> = emptyList()): ByteArray {
        logger.debug("Received getInnholdsfortegnelseAsPdf")

        val document = dokumentUnderArbeidRepository.getReferenceById(dokumentUnderArbeidId)

        if (document is DokumentUnderArbeidAsVedlegg) {
            throw IllegalArgumentException("must be hoveddokument")
        }

        val vedlegg = dokumentUnderArbeidCommonService.findVedleggByParentId(dokumentUnderArbeidId)

        val documents = dokumentMapper.getSortedDokumentViewListForInnholdsfortegnelse(
            allDokumenterUnderArbeid = vedlegg.toList(),
            mottakere = mottakere,
            behandling = behandlingService.getBehandlingForReadWithoutCheckForAccess(document.behandlingId),
            hoveddokument = document,
        )

        val pdfDocument =
            kabalJsonToPdfClient.getInnholdsfortegnelse(
                InnholdsfortegnelseRequest(
                    documents = documents
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