package no.nav.klage.oppgave.api.mapper


import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.clients.egenansatt.EgenAnsattService
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.kabaldocument.KabalDocumentGateway
import no.nav.klage.oppgave.clients.norg2.Norg2Client
import no.nav.klage.oppgave.clients.pdl.PdlFacade
import no.nav.klage.oppgave.clients.pdl.Person
import no.nav.klage.oppgave.domain.Behandling
import no.nav.klage.oppgave.domain.DokumentMetadata
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.repositories.SaksbehandlerRepository
import no.nav.klage.oppgave.util.getLogger
import org.springframework.stereotype.Service
import java.util.*

@Service
class BehandlingMapper(
    private val pdlFacade: PdlFacade,
    private val egenAnsattService: EgenAnsattService,
    private val norg2Client: Norg2Client,
    private val eregClient: EregClient,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val kabalDocumentGateway: KabalDocumentGateway
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun mapBehandlingToBehandlingDetaljerView(behandling: Behandling): BehandlingDetaljerView {
        return when (behandling.type) {
            Type.KLAGE -> mapKlagebehandlingToBehandlingDetaljerView(behandling as Klagebehandling)
            Type.ANKE -> mapAnkebehandlingToBehandlingDetaljerView(behandling as Ankebehandling)
        }
    }

    fun mapKlagebehandlingToBehandlingDetaljerView(klagebehandling: Klagebehandling): BehandlingDetaljerView {
        val enhetNavn = klagebehandling.avsenderEnhetFoersteinstans.let { norg2Client.fetchEnhet(it) }.navn

        return BehandlingDetaljerView(
            id = klagebehandling.id,
            klageInnsendtdato = klagebehandling.innsendt,
            fraNAVEnhet = klagebehandling.avsenderEnhetFoersteinstans,
            fraNAVEnhetNavn = enhetNavn,
            forrigeNAVEnhet = klagebehandling.avsenderEnhetFoersteinstans,
            forrigeNAVEnhetNavn = enhetNavn,
            fraSaksbehandlerident = klagebehandling.avsenderSaksbehandleridentFoersteinstans,
            forrigeSaksbehandlerident = klagebehandling.avsenderSaksbehandleridentFoersteinstans,
            forrigeVedtaksDato = null,
            mottattVedtaksinstans = klagebehandling.mottattVedtaksinstans,
            sakenGjelder = klagebehandling.sakenGjelder.getSakenGjelderView(),
            klager = klagebehandling.klager.getKlagerView(),
            tema = klagebehandling.ytelse.toTema().id,
            ytelse = klagebehandling.ytelse.id,
            type = klagebehandling.type.id,
            mottatt = klagebehandling.mottattKlageinstans.toLocalDate(),
            mottattKlageinstans = klagebehandling.mottattKlageinstans.toLocalDate(),
            tildelt = klagebehandling.tildeling?.tidspunkt?.toLocalDate(),
            avsluttetAvSaksbehandlerDate = klagebehandling.currentDelbehandling().avsluttetAvSaksbehandler?.toLocalDate(),
            isAvsluttetAvSaksbehandler = klagebehandling.currentDelbehandling().avsluttetAvSaksbehandler != null,
            frist = klagebehandling.frist,
            tildeltSaksbehandlerident = klagebehandling.tildeling?.saksbehandlerident,
            tildeltSaksbehandler = berikSaksbehandler(klagebehandling.tildeling?.saksbehandlerident),
            tildeltSaksbehandlerEnhet = klagebehandling.tildeling?.enhet,
            medunderskriverident = klagebehandling.currentDelbehandling().medunderskriver?.saksbehandlerident,
            medunderskriver = berikSaksbehandler(klagebehandling.currentDelbehandling().medunderskriver?.saksbehandlerident),
            medunderskriverFlyt = klagebehandling.currentDelbehandling().medunderskriverFlyt,
            datoSendtMedunderskriver = klagebehandling.currentDelbehandling().medunderskriver?.tidspunkt?.toLocalDate(),
            hjemler = klagebehandling.hjemler.map { it.id },
            modified = klagebehandling.modified,
            created = klagebehandling.created,
            resultat = klagebehandling.currentDelbehandling().mapToVedtakView(),
            kommentarFraVedtaksinstans = klagebehandling.kommentarFraFoersteinstans,
            tilknyttedeDokumenter = klagebehandling.saksdokumenter.map {
                TilknyttetDokument(
                    journalpostId = it.journalpostId,
                    dokumentInfoId = it.dokumentInfoId
                )
            }.toSet(),
            egenAnsatt = klagebehandling.sakenGjelder.erEgenAnsatt(),
            fortrolig = klagebehandling.sakenGjelder.harBeskyttelsesbehovFortrolig(),
            strengtFortrolig = klagebehandling.sakenGjelder.harBeskyttelsesbehovStrengtFortrolig(),
            kvalitetsvurderingId = klagebehandling.kakaKvalitetsvurderingId,
            isPossibleToUseDokumentUnderArbeid = klagebehandling.currentDelbehandling().avsluttetAvSaksbehandler != null || klagebehandling.currentDelbehandling().dokumentEnhetId == null,
            sattPaaVent = klagebehandling.sattPaaVent,
            brevMottakere = klagebehandling.mapToBrevMottakerViewList(),
        )
    }

    fun mapAnkebehandlingToBehandlingDetaljerView(ankebehandling: Ankebehandling): BehandlingDetaljerView {
        val forrigeEnhetNavn = ankebehandling.klageBehandlendeEnhet.let { norg2Client.fetchEnhet(it) }.navn

        return BehandlingDetaljerView(
            id = ankebehandling.id,
            klageInnsendtdato = ankebehandling.innsendt,
            fraNAVEnhet = ankebehandling.klageBehandlendeEnhet,
            fraNAVEnhetNavn = forrigeEnhetNavn,
            forrigeNAVEnhet = ankebehandling.klageBehandlendeEnhet,
            forrigeNAVEnhetNavn = forrigeEnhetNavn,
            mottattVedtaksinstans = null,
            sakenGjelder = ankebehandling.sakenGjelder.getSakenGjelderView(),
            klager = ankebehandling.klager.getKlagerView(),
            tema = ankebehandling.ytelse.toTema().id,
            ytelse = ankebehandling.ytelse.id,
            type = ankebehandling.type.id,
            mottatt = ankebehandling.mottattKlageinstans.toLocalDate(),
            mottattKlageinstans = ankebehandling.mottattKlageinstans.toLocalDate(),
            tildelt = ankebehandling.tildeling?.tidspunkt?.toLocalDate(),
            avsluttetAvSaksbehandlerDate = ankebehandling.currentDelbehandling().avsluttetAvSaksbehandler?.toLocalDate(),
            isAvsluttetAvSaksbehandler = ankebehandling.currentDelbehandling().avsluttetAvSaksbehandler != null,
            frist = ankebehandling.frist,
            tildeltSaksbehandlerident = ankebehandling.tildeling?.saksbehandlerident,
            tildeltSaksbehandler = berikSaksbehandler(ankebehandling.tildeling?.saksbehandlerident),
            tildeltSaksbehandlerEnhet = ankebehandling.tildeling?.enhet,
            medunderskriverident = ankebehandling.currentDelbehandling().medunderskriver?.saksbehandlerident,
            medunderskriver = berikSaksbehandler(ankebehandling.currentDelbehandling().medunderskriver?.saksbehandlerident),
            medunderskriverFlyt = ankebehandling.currentDelbehandling().medunderskriverFlyt,
            datoSendtMedunderskriver = ankebehandling.currentDelbehandling().medunderskriver?.tidspunkt?.toLocalDate(),
            hjemler = ankebehandling.hjemler.map { it.id },
            modified = ankebehandling.modified,
            created = ankebehandling.created,
            fraSaksbehandlerident = null,
            forrigeSaksbehandlerident = null,
            forrigeVedtaksDato = ankebehandling.klageVedtaksDato,
            resultat = ankebehandling.currentDelbehandling().mapToVedtakView(),
            kommentarFraVedtaksinstans = null,
            tilknyttedeDokumenter = ankebehandling.saksdokumenter.map {
                TilknyttetDokument(
                    journalpostId = it.journalpostId,
                    dokumentInfoId = it.dokumentInfoId
                )
            }.toSet(),
            egenAnsatt = ankebehandling.sakenGjelder.erEgenAnsatt(),
            fortrolig = ankebehandling.sakenGjelder.harBeskyttelsesbehovFortrolig(),
            strengtFortrolig = ankebehandling.sakenGjelder.harBeskyttelsesbehovStrengtFortrolig(),
            kvalitetsvurderingId = ankebehandling.kakaKvalitetsvurderingId,
            isPossibleToUseDokumentUnderArbeid = ankebehandling.currentDelbehandling().avsluttetAvSaksbehandler != null || ankebehandling.currentDelbehandling().dokumentEnhetId == null,
            sattPaaVent = ankebehandling.sattPaaVent,
            brevMottakere = ankebehandling.mapToBrevMottakerViewList(),
        )
    }

    private fun berikSaksbehandler(saksbehandlerident: String?): SaksbehandlerView? {
        return saksbehandlerident?.let {
            SaksbehandlerView(it, saksbehandlerRepository.getNameForSaksbehandler(it))
        }
    }

    private fun SakenGjelder.getSakenGjelderView(): BehandlingDetaljerView.SakenGjelderView {
        if (erPerson()) {
            val person = pdlFacade.getPersonInfo(partId.value)
            return BehandlingDetaljerView.SakenGjelderView(
                person = BehandlingDetaljerView.PersonView(
                    foedselsnummer = person.foedselsnr,
                    navn = person.mapNavnToView(),
                    kjoenn = person.kjoenn,
                ), virksomhet = null
            )
        } else {
            return BehandlingDetaljerView.SakenGjelderView(
                person = null,
                virksomhet = BehandlingDetaljerView.VirksomhetView(
                    virksomhetsnummer = partId.value,
                    navn = eregClient.hentOrganisasjon(partId.value)?.navn?.sammensattNavn()
                )
            )
        }
    }

    private fun Klager.getKlagerView(): BehandlingDetaljerView.KlagerView {
        if (erPerson()) {
            val person = pdlFacade.getPersonInfo(partId.value)
            return BehandlingDetaljerView.KlagerView(
                person = BehandlingDetaljerView.PersonView(
                    foedselsnummer = person.foedselsnr,
                    navn = person.mapNavnToView(),
                    kjoenn = person.kjoenn,
                ), virksomhet = null
            )
        } else {
            return BehandlingDetaljerView.KlagerView(
                person = null, virksomhet = BehandlingDetaljerView.VirksomhetView(
                    virksomhetsnummer = partId.value,
                    navn = eregClient.hentOrganisasjon(partId.value)?.navn?.sammensattNavn()
                )
            )
        }
    }

    private fun SakenGjelder.harBeskyttelsesbehovFortrolig(): Boolean {
        return if (erVirksomhet()) {
            false
        } else {
            pdlFacade.getPersonInfo(partId.value).harBeskyttelsesbehovFortrolig()
        }
    }

    private fun SakenGjelder.harBeskyttelsesbehovStrengtFortrolig(): Boolean {
        return if (erVirksomhet()) {
            false
        } else {
            pdlFacade.getPersonInfo(partId.value).harBeskyttelsesbehovStrengtFortrolig()
        }
    }

    private fun SakenGjelder.erEgenAnsatt(): Boolean {
        return if (erVirksomhet()) {
            false
        } else {
            egenAnsattService.erEgenAnsatt(partId.value)
        }
    }

    fun Delbehandling.mapToVedtakView(): VedtakView {
        return VedtakView(
            id = id,
            utfall = utfall?.id,
            hjemler = hjemler.map { it.id }.toSet(),
            file = getVedleggView(dokumentEnhetId)
        )
    }

    fun getVedleggView(dokumentEnhetId: UUID?): VedleggView? {
        return if (dokumentEnhetId != null && kabalDocumentGateway.isHovedDokumentUploaded(dokumentEnhetId)) {
            mapDokumentMetadataToVedleggView(
                kabalDocumentGateway.getMetadataOmHovedDokument(dokumentEnhetId)!!,
            )
        } else null
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

    private fun Person?.mapNavnToView(): BehandlingDetaljerView.NavnView? =
        if (this != null) {
            BehandlingDetaljerView.NavnView(
                fornavn = fornavn,
                mellomnavn = mellomnavn,
                etternavn = etternavn
            )
        } else {
            null
        }

    fun mapToBehandlingFullfoertView(behandling: Behandling): BehandlingFullfoertView {
        return BehandlingFullfoertView(
            modified = behandling.modified,
            isAvsluttetAvSaksbehandler = behandling.currentDelbehandling().avsluttetAvSaksbehandler != null
        )
    }

    fun mapToMedunderskriverFlytResponse(behandling: Behandling): MedunderskriverFlytResponse {
        return MedunderskriverFlytResponse(
            behandling.modified,
            behandling.currentDelbehandling().medunderskriverFlyt
        )
    }

    fun mapToMedunderskriverView(behandling: Behandling): MedunderskriverView {
        return MedunderskriverView(
            medunderskriver = behandling.currentDelbehandling().medunderskriver?.let { berikSaksbehandler(behandling.currentDelbehandling().medunderskriver!!.saksbehandlerident) }
        )
    }

    fun mapToMedunderskriverFlytView(behandling: Behandling): MedunderskriverFlytView {
        return MedunderskriverFlytView(
            medunderskriverFlyt = behandling.currentDelbehandling().medunderskriverFlyt
        )
    }

    private fun Behandling.mapToBrevMottakerViewList(): List<BrevMottakerView> {
        val brevMottakere = mutableListOf<BrevMottakerView>()
        if (klager.prosessfullmektig != null) {
            brevMottakere.add(
                klager.prosessfullmektig!!.getBrevMottakerView()
            )
            if (klager.prosessfullmektig!!.skalPartenMottaKopi) {
                brevMottakere.add(
                    klager.getBrevMottakerView()
                )
            }
        } else {
            brevMottakere.add(
                klager.getBrevMottakerView()
            )
        }
        if (sakenGjelder.partId != klager.partId && sakenGjelder.skalMottaKopi) {
            brevMottakere.add(
                sakenGjelder.getBrevMottakerView()
            )
        }

        return brevMottakere
    }

    private fun Klager.getBrevMottakerView() =
        BrevMottakerView(
            partId = partId.value,
            partIdType = partId.type.name,
            navn = partId.getNavn(),
            rolle = BrevMottagerRolle.KLAGER,
        )

    private fun SakenGjelder.getBrevMottakerView() =
        BrevMottakerView(
            partId = partId.value,
            partIdType = partId.type.name,
            navn = partId.getNavn(),
            rolle = BrevMottagerRolle.SAKEN_GJELDER,
        )

    private fun Prosessfullmektig.getBrevMottakerView() =
        BrevMottakerView(
            partId = partId.value,
            partIdType = partId.type.name,
            navn = partId.getNavn(),
            rolle = BrevMottagerRolle.PROSESSFULLMEKTIG,
        )

    private fun PartId.getNavn(): String? =
        if (type == PartIdType.PERSON) {
            pdlFacade.getPersonInfo(value).settSammenNavn()
        } else {
            eregClient.hentOrganisasjon(value)?.navn?.navnelinje1
        }
}