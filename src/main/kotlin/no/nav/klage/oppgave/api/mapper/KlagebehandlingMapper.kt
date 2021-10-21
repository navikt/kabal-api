package no.nav.klage.oppgave.api.mapper


import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.clients.egenansatt.EgenAnsattService
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.kabaldocument.KabalDocumentGateway
import no.nav.klage.oppgave.clients.norg2.Norg2Client
import no.nav.klage.oppgave.clients.pdl.PdlFacade
import no.nav.klage.oppgave.clients.pdl.Person
import no.nav.klage.oppgave.domain.DokumentInnholdOgTittel
import no.nav.klage.oppgave.domain.DokumentMetadata
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.domain.kodeverk.PartIdType
import no.nav.klage.oppgave.repositories.SaksbehandlerRepository
import no.nav.klage.oppgave.service.FileApiService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*

@Service
class KlagebehandlingMapper(
    private val pdlFacade: PdlFacade,
    private val egenAnsattService: EgenAnsattService,
    private val norg2Client: Norg2Client,
    private val eregClient: EregClient,
    private val fileApiService: FileApiService,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val kabalDocumentGateway: KabalDocumentGateway
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    fun mapKlagebehandlingToKlagebehandlingDetaljerView(klagebehandling: Klagebehandling): KlagebehandlingDetaljerView {
        val enhetNavn = klagebehandling.avsenderEnhetFoersteinstans?.let { norg2Client.fetchEnhet(it) }?.navn
        val sakenGjelderFoedselsnummer = foedselsnummer(klagebehandling.sakenGjelder.partId)
        val sakenGjelder = sakenGjelderFoedselsnummer?.let { pdlFacade.getPersonInfo(it) }
        val sakenGjelderVirksomhetsnummer = virksomhetsnummer(klagebehandling.sakenGjelder.partId)
        val sakenGjelderVirksomhet = sakenGjelderVirksomhetsnummer?.let { eregClient.hentOrganisasjon(it) }
        val klagerFoedselsnummer = foedselsnummer(klagebehandling.klager.partId)
        val klager = klagerFoedselsnummer?.let { pdlFacade.getPersonInfo(it) }
        val klagerVirksomhetsnummer = virksomhetsnummer(klagebehandling.klager.partId)
        val klagerVirksomhet = klagerVirksomhetsnummer?.let { eregClient.hentOrganisasjon(it) }

        val erFortrolig = sakenGjelder?.harBeskyttelsesbehovFortrolig() ?: false
        val erStrengtFortrolig = sakenGjelder?.harBeskyttelsesbehovStrengtFortrolig() ?: false
        val erEgenAnsatt = sakenGjelderFoedselsnummer?.let { egenAnsattService.erEgenAnsatt(it) } ?: false

        return KlagebehandlingDetaljerView(
            id = klagebehandling.id,
            klageInnsendtdato = klagebehandling.innsendt,
            fraNAVEnhet = klagebehandling.avsenderEnhetFoersteinstans,
            fraNAVEnhetNavn = enhetNavn,
            fraSaksbehandlerident = klagebehandling.avsenderSaksbehandleridentFoersteinstans,
            mottattFoersteinstans = klagebehandling.mottattFoersteinstans,
            sakenGjelder = klagebehandling.sakenGjelder.mapToView(),
            klager = klagebehandling.klager.mapToView(),
            sakenGjelderFoedselsnummer = sakenGjelderFoedselsnummer,
            sakenGjelderNavn = sakenGjelder.mapNavnToView(),
            sakenGjelderKjoenn = sakenGjelder?.kjoenn,
            sakenGjelderVirksomhetsnummer = sakenGjelderVirksomhetsnummer,
            sakenGjelderVirksomhetsnavn = sakenGjelderVirksomhet?.navn?.sammensattNavn(),
            klagerFoedselsnummer = klagerFoedselsnummer,
            klagerVirksomhetsnummer = klagerVirksomhetsnummer,
            klagerVirksomhetsnavn = klagerVirksomhet?.navn?.sammensattNavn(),
            klagerNavn = klager.mapNavnToView(),
            klagerKjoenn = klager?.kjoenn,
            tema = klagebehandling.tema.id,
            type = klagebehandling.type.id,
            mottatt = klagebehandling.mottattKlageinstans.toLocalDate(),
            mottattKlageinstans = klagebehandling.mottattKlageinstans.toLocalDate(),
            tildelt = klagebehandling.tildeling?.tidspunkt?.toLocalDate(),
            avsluttetAvSaksbehandlerDate = klagebehandling.avsluttetAvSaksbehandler?.toLocalDate(),
            isAvsluttetAvSaksbehandler = klagebehandling.avsluttetAvSaksbehandler != null,
            frist = klagebehandling.frist,
            tildeltSaksbehandlerident = klagebehandling.tildeling?.saksbehandlerident,
            tildeltSaksbehandler = berikSaksbehandler(klagebehandling.tildeling?.saksbehandlerident),
            medunderskriverident = klagebehandling.medunderskriver?.saksbehandlerident,
            medunderskriver = berikSaksbehandler(klagebehandling.medunderskriver?.saksbehandlerident),
            medunderskriverFlyt = klagebehandling.medunderskriverFlyt,
            datoSendtMedunderskriver = klagebehandling.medunderskriver?.tidspunkt?.toLocalDate(),
            hjemler = klagebehandling.hjemler.map { it.id },
            modified = klagebehandling.modified,
            created = klagebehandling.created,
            resultat = klagebehandling.vedtak?.mapToVedtakView(),
            kommentarFraFoersteinstans = klagebehandling.kommentarFraFoersteinstans,
            tilknyttedeDokumenter = klagebehandling.saksdokumenter.map {
                TilknyttetDokument(
                    journalpostId = it.journalpostId,
                    dokumentInfoId = it.dokumentInfoId
                )
            }.toSet(),
            egenAnsatt = erEgenAnsatt,
            fortrolig = erFortrolig,
            strengtFortrolig = erStrengtFortrolig
        )
    }

    private fun berikSaksbehandler(saksbehandlerident: String?): SaksbehandlerRefView? {
        return saksbehandlerident?.let {
            SaksbehandlerRefView(it, saksbehandlerRepository.getNameForSaksbehandler(it))
        }
    }

    private fun SakenGjelder.mapToView(): KlagebehandlingDetaljerView.SakenGjelderView {
        if (erPerson()) {
            val person = pdlFacade.getPersonInfo(partId.value)
            return KlagebehandlingDetaljerView.SakenGjelderView(
                person = KlagebehandlingDetaljerView.PersonView(
                    foedselsnummer = person.foedselsnr,
                    navn = person.mapNavnToView(),
                    kjoenn = person.kjoenn
                ), virksomhet = null
            )
        } else {
            return KlagebehandlingDetaljerView.SakenGjelderView(
                person = null, virksomhet = KlagebehandlingDetaljerView.VirksomhetView(
                    virksomhetsnummer = partId.value,
                    navn = eregClient.hentOrganisasjon(partId.value)?.navn?.sammensattNavn()
                )
            )
        }
    }

    private fun Klager.mapToView(): KlagebehandlingDetaljerView.KlagerView {
        if (erPerson()) {
            val person = pdlFacade.getPersonInfo(partId.value)
            return KlagebehandlingDetaljerView.KlagerView(
                person = KlagebehandlingDetaljerView.PersonView(
                    foedselsnummer = person.foedselsnr,
                    navn = person.mapNavnToView(),
                    kjoenn = person.kjoenn
                ), virksomhet = null
            )
        } else {
            return KlagebehandlingDetaljerView.KlagerView(
                person = null, virksomhet = KlagebehandlingDetaljerView.VirksomhetView(
                    virksomhetsnummer = partId.value,
                    navn = eregClient.hentOrganisasjon(partId.value)?.navn?.sammensattNavn()
                )
            )
        }
    }

    fun Vedtak.mapToVedtakView(): VedtakView {
        return VedtakView(
            id = id,
            utfall = utfall?.id,
            grunn = grunn?.id,
            hjemler = hjemler.map { it.id }.toSet(),
            brevMottakere = brevmottakere.map { mapBrevmottaker(it) }.toSet(),
            file = getVedleggView(opplastet, mellomlagerId, dokumentEnhetId),
            opplastet = opplastet
        )
    }

    fun getVedleggView(opplastet: LocalDateTime?, mellomlagerId: String?, dokumentEnhetId: UUID?): VedleggView? {
        return if (opplastet != null) {
            if (dokumentEnhetId != null && kabalDocumentGateway.isHovedDokumentOpplasted(dokumentEnhetId)) {
                mapDokumentMetadataToVedleggView(
                    kabalDocumentGateway.getMetadataOmHovedDokument(dokumentEnhetId)!!,
                )
            } else if (mellomlagerId != null) {
                mapDokumentInnholdOgTittelToVedleggView(
                    fileApiService.getUploadedDocument(mellomlagerId), opplastet
                )
            } else null
        } else null
    }

    fun mapDokumentInnholdOgTittelToVedleggView(
        dokumentInnholdOgTittel: DokumentInnholdOgTittel,
        opplastet: LocalDateTime
    ): VedleggView {
        return VedleggView(
            dokumentInnholdOgTittel.title,
            dokumentInnholdOgTittel.content.size.toLong(),
            opplastet
        )
    }

    fun mapDokumentMetadataToVedleggView(
        dokumentMetadata: DokumentMetadata
    ): VedleggView {
        return VedleggView(
            dokumentMetadata.title,
            dokumentMetadata.size,
            dokumentMetadata.opplastet
        )
    }

    private fun mapBrevmottaker(it: BrevMottaker) = BrevMottakerView(
        it.partId.type.id,
        it.partId.value,
        it.rolle.id
    )

    private fun foedselsnummer(partId: PartId) =
        if (partId.type == PartIdType.PERSON) {
            partId.value
        } else {
            null
        }

    private fun virksomhetsnummer(partId: PartId) =
        if (partId.type == PartIdType.VIRKSOMHET) {
            partId.value
        } else {
            null
        }

    private fun Person?.mapNavnToView(): KlagebehandlingDetaljerView.NavnView? =
        if (this != null) {
            KlagebehandlingDetaljerView.NavnView(
                fornavn = fornavn,
                mellomnavn = mellomnavn,
                etternavn = etternavn
            )
        } else {
            null
        }

    fun mapKlagebehandlingToKlagebehandlingEditableFieldsView(klagebehandling: Klagebehandling): KlagebehandlingEditedView {
        return KlagebehandlingEditedView(klagebehandling.modified)
    }

    fun mapToVedleggEditedView(klagebehandling: Klagebehandling): VedleggEditedView {
        val vedtak = klagebehandling.getVedtakOrException()
        return VedleggEditedView(
            klagebehandling.modified,
            file = getVedleggView(vedtak.opplastet, vedtak.mellomlagerId, vedtak.dokumentEnhetId),
        )
    }

    fun mapToKlagebehandlingFullfoertView(klagebehandling: Klagebehandling): KlagebehandlingFullfoertView {
        return KlagebehandlingFullfoertView(
            modified = klagebehandling.modified,
            ferdigstilt = klagebehandling.avsluttetAvSaksbehandler != null
        )
    }

    fun mapToMedunderskriverFlytResponse(klagebehandling: Klagebehandling): MedunderskriverFlytResponse {
        return MedunderskriverFlytResponse(
            klagebehandling.modified,
            klagebehandling.medunderskriverFlyt
        )
    }

    fun mapToMedunderskriverInfoView(klagebehandling: Klagebehandling): MedunderskriverInfoView {
        return MedunderskriverInfoView(
            klagebehandling.medunderskriver?.let { berikSaksbehandler(klagebehandling.medunderskriver!!.saksbehandlerident) },
            klagebehandling.medunderskriverFlyt
        )
    }
}

