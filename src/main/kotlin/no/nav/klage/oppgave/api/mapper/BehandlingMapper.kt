package no.nav.klage.oppgave.api.mapper


import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.api.view.kabin.KabinPartView
import no.nav.klage.oppgave.clients.egenansatt.EgenAnsattService
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.ereg.NoekkelInfoOmOrganisasjon
import no.nav.klage.oppgave.clients.krrproxy.DigitalKontaktinformasjon
import no.nav.klage.oppgave.clients.krrproxy.KrrProxyClient
import no.nav.klage.oppgave.clients.norg2.Norg2Client
import no.nav.klage.oppgave.clients.pdl.PdlFacade
import no.nav.klage.oppgave.clients.pdl.Person
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.service.KodeverkService
import no.nav.klage.oppgave.service.RegoppslagService
import no.nav.klage.oppgave.service.SaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class BehandlingMapper(
    private val pdlFacade: PdlFacade,
    private val egenAnsattService: EgenAnsattService,
    private val norg2Client: Norg2Client,
    private val eregClient: EregClient,
    private val saksbehandlerService: SaksbehandlerService,
    private val krrProxyClient: KrrProxyClient,
    private val kodeverkService: KodeverkService,
    private val regoppslagService: RegoppslagService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun mapBehandlingToBehandlingDetaljerView(behandling: Behandling): BehandlingDetaljerView {
        return when (behandling.type) {
            Type.KLAGE -> mapKlagebehandlingToBehandlingDetaljerView(behandling as Klagebehandling)
            Type.ANKE -> mapAnkebehandlingToBehandlingDetaljerView(behandling as Ankebehandling)
            Type.ANKE_I_TRYGDERETTEN -> mapAnkeITrygderettenbehandlingToBehandlingDetaljerView(behandling as AnkeITrygderettenbehandling)
        }
    }

    fun mapKlagebehandlingToBehandlingDetaljerView(klagebehandling: Klagebehandling): BehandlingDetaljerView {
        val enhetNavn = klagebehandling.avsenderEnhetFoersteinstans.let { norg2Client.fetchEnhet(it) }.navn

        return BehandlingDetaljerView(
            id = klagebehandling.id,
            fraNAVEnhet = klagebehandling.avsenderEnhetFoersteinstans,
            fraNAVEnhetNavn = enhetNavn,
            mottattVedtaksinstans = klagebehandling.mottattVedtaksinstans,
            sakenGjelder = getSakenGjelderView(klagebehandling.sakenGjelder),
            klager = getPartView(klagebehandling.klager.partId),
            prosessfullmektig = klagebehandling.klager.prosessfullmektig?.let { getPartView(it.partId) },
            temaId = klagebehandling.ytelse.toTema().id,
            ytelseId = klagebehandling.ytelse.id,
            typeId = klagebehandling.type.id,
            mottattKlageinstans = klagebehandling.mottattKlageinstans.toLocalDate(),
            tildelt = klagebehandling.tildeling?.tidspunkt?.toLocalDate(),
            avsluttetAvSaksbehandlerDate = klagebehandling.avsluttetAvSaksbehandler?.toLocalDate(),
            isAvsluttetAvSaksbehandler = klagebehandling.avsluttetAvSaksbehandler != null,
            frist = klagebehandling.frist,
            datoSendtMedunderskriver = klagebehandling.medunderskriver?.tidspunkt?.toLocalDate(),
            hjemmelIdList = klagebehandling.hjemler.map { it.id },
            modified = klagebehandling.modified,
            created = klagebehandling.created,
            resultat = klagebehandling.mapToVedtakView(),
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
            vergemaalEllerFremtidsfullmakt = klagebehandling.sakenGjelder.harVergemaalEllerFremtidsfullmakt(),
            dead = klagebehandling.sakenGjelder.getDead(),
            fullmakt = klagebehandling.sakenGjelder.isFullmakt(),
            kvalitetsvurderingReference = if (klagebehandling.feilregistrering == null) {
                BehandlingDetaljerView.KvalitetsvurderingReference(
                    id = klagebehandling.kakaKvalitetsvurderingId!!,
                    version = klagebehandling.kakaKvalitetsvurderingVersion,
                )
            } else null,
            sattPaaVent = klagebehandling.sattPaaVent,
            feilregistrering = klagebehandling.feilregistrering.toView(),
            fagsystemId = klagebehandling.fagsystem.id,
            relevantDocumentIdList = klagebehandling.saksdokumenter.map {
                it.dokumentInfoId
            }.toSet(),
            saksnummer = klagebehandling.fagsakId,
            rol = klagebehandling.toROLView(),
            medunderskriver = klagebehandling.toMedunderskriverView(),
            saksbehandler = klagebehandling.toSaksbehandlerView(),
            previousSaksbehandler = klagebehandling.toPreviousSaksbehandlerView(),
        )
    }

    private fun Behandling.toPreviousSaksbehandlerView(): SaksbehandlerView? {
        return previousSaksbehandlerident?.let {
            SaksbehandlerView(
                navIdent = it,
                navn = saksbehandlerService.getNameForIdentDefaultIfNull(it),
            )
        }
    }

    private fun Behandling.toSaksbehandlerView(): SaksbehandlerView? {
        return tildeling?.saksbehandlerident?.let {
            SaksbehandlerView(
                navIdent = it,
                navn = saksbehandlerService.getNameForIdentDefaultIfNull(it),
            )
        }
    }

    private fun Behandling.toROLView(): BehandlingDetaljerView.CombinedMedunderskriverAndROLView {
        return BehandlingDetaljerView.CombinedMedunderskriverAndROLView(
            employee = if (rolIdent != null) {
                SaksbehandlerView(
                    navIdent = rolIdent!!,
                    navn = saksbehandlerService.getNameForIdentDefaultIfNull(rolIdent!!),
                )
            } else null,
            flowState = rolFlowState,
        )
    }

    private fun Behandling.toMedunderskriverView(): BehandlingDetaljerView.CombinedMedunderskriverAndROLView {
        return BehandlingDetaljerView.CombinedMedunderskriverAndROLView(
            employee = if (medunderskriver?.saksbehandlerident != null) {
                SaksbehandlerView(
                    navIdent = medunderskriver?.saksbehandlerident!!,
                    navn = saksbehandlerService.getNameForIdentDefaultIfNull(medunderskriver?.saksbehandlerident!!),
                )
            } else null,
            flowState = medunderskriverFlowState,
        )
    }

    fun mapAnkebehandlingToBehandlingDetaljerView(ankebehandling: Ankebehandling): BehandlingDetaljerView {
        val forrigeEnhetNavn = ankebehandling.klageBehandlendeEnhet.let { norg2Client.fetchEnhet(it) }.navn

        return BehandlingDetaljerView(
            id = ankebehandling.id,
            fraNAVEnhet = ankebehandling.klageBehandlendeEnhet,
            fraNAVEnhetNavn = forrigeEnhetNavn,
            mottattVedtaksinstans = null,
            sakenGjelder = getSakenGjelderView(ankebehandling.sakenGjelder),
            klager = getPartView(ankebehandling.klager.partId),
            prosessfullmektig = ankebehandling.klager.prosessfullmektig?.let { getPartView(it.partId) },
            temaId = ankebehandling.ytelse.toTema().id,
            ytelseId = ankebehandling.ytelse.id,
            typeId = ankebehandling.type.id,
            mottattKlageinstans = ankebehandling.mottattKlageinstans.toLocalDate(),
            tildelt = ankebehandling.tildeling?.tidspunkt?.toLocalDate(),
            avsluttetAvSaksbehandlerDate = ankebehandling.avsluttetAvSaksbehandler?.toLocalDate(),
            isAvsluttetAvSaksbehandler = ankebehandling.avsluttetAvSaksbehandler != null,
            frist = ankebehandling.frist,
            datoSendtMedunderskriver = ankebehandling.medunderskriver?.tidspunkt?.toLocalDate(),
            hjemmelIdList = ankebehandling.hjemler.map { it.id },
            modified = ankebehandling.modified,
            created = ankebehandling.created,
            resultat = ankebehandling.mapToVedtakView(),
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
            vergemaalEllerFremtidsfullmakt = ankebehandling.sakenGjelder.harVergemaalEllerFremtidsfullmakt(),
            dead = ankebehandling.sakenGjelder.getDead(),
            fullmakt = ankebehandling.sakenGjelder.isFullmakt(),
            kvalitetsvurderingReference = if (ankebehandling.feilregistrering == null) {
                BehandlingDetaljerView.KvalitetsvurderingReference(
                    id = ankebehandling.kakaKvalitetsvurderingId!!,
                    version = ankebehandling.kakaKvalitetsvurderingVersion,
                )
            } else null,
            sattPaaVent = ankebehandling.sattPaaVent,
            feilregistrering = ankebehandling.feilregistrering.toView(),
            fagsystemId = ankebehandling.fagsystem.id,
            relevantDocumentIdList = ankebehandling.saksdokumenter.map {
                it.dokumentInfoId
            }.toSet(),
            saksnummer = ankebehandling.fagsakId,
            rol = ankebehandling.toROLView(),
            medunderskriver = ankebehandling.toMedunderskriverView(),
            saksbehandler = ankebehandling.toSaksbehandlerView(),
            previousSaksbehandler = ankebehandling.toPreviousSaksbehandlerView(),
        )
    }

    fun mapAnkeITrygderettenbehandlingToBehandlingDetaljerView(ankeITrygderettenbehandling: AnkeITrygderettenbehandling): BehandlingDetaljerView {
        return BehandlingDetaljerView(
            id = ankeITrygderettenbehandling.id,
            fraNAVEnhet = null,
            fraNAVEnhetNavn = null,
            mottattVedtaksinstans = null,
            sakenGjelder = getSakenGjelderView(ankeITrygderettenbehandling.sakenGjelder),
            klager = getPartView(ankeITrygderettenbehandling.klager.partId),
            prosessfullmektig = ankeITrygderettenbehandling.klager.prosessfullmektig?.let { getPartView(it.partId) },
            temaId = ankeITrygderettenbehandling.ytelse.toTema().id,
            ytelseId = ankeITrygderettenbehandling.ytelse.id,
            typeId = ankeITrygderettenbehandling.type.id,
            mottattKlageinstans = ankeITrygderettenbehandling.mottattKlageinstans.toLocalDate(),
            tildelt = ankeITrygderettenbehandling.tildeling?.tidspunkt?.toLocalDate(),
            avsluttetAvSaksbehandlerDate = ankeITrygderettenbehandling.avsluttetAvSaksbehandler?.toLocalDate(),
            isAvsluttetAvSaksbehandler = ankeITrygderettenbehandling.avsluttetAvSaksbehandler != null,
            frist = ankeITrygderettenbehandling.frist,
            datoSendtMedunderskriver = ankeITrygderettenbehandling.medunderskriver?.tidspunkt?.toLocalDate(),
            hjemmelIdList = ankeITrygderettenbehandling.hjemler.map { it.id },
            modified = ankeITrygderettenbehandling.modified,
            created = ankeITrygderettenbehandling.created,
            resultat = ankeITrygderettenbehandling.mapToVedtakView(),
            kommentarFraVedtaksinstans = null,
            tilknyttedeDokumenter = ankeITrygderettenbehandling.saksdokumenter.map {
                TilknyttetDokument(
                    journalpostId = it.journalpostId,
                    dokumentInfoId = it.dokumentInfoId
                )
            }.toSet(),
            egenAnsatt = ankeITrygderettenbehandling.sakenGjelder.erEgenAnsatt(),
            fortrolig = ankeITrygderettenbehandling.sakenGjelder.harBeskyttelsesbehovFortrolig(),
            strengtFortrolig = ankeITrygderettenbehandling.sakenGjelder.harBeskyttelsesbehovStrengtFortrolig(),
            vergemaalEllerFremtidsfullmakt = ankeITrygderettenbehandling.sakenGjelder.harVergemaalEllerFremtidsfullmakt(),
            dead = ankeITrygderettenbehandling.sakenGjelder.getDead(),
            fullmakt = ankeITrygderettenbehandling.sakenGjelder.isFullmakt(),
            kvalitetsvurderingReference = null,
            sattPaaVent = ankeITrygderettenbehandling.sattPaaVent,
            sendtTilTrygderetten = ankeITrygderettenbehandling.sendtTilTrygderetten,
            kjennelseMottatt = ankeITrygderettenbehandling.kjennelseMottatt,
            feilregistrering = ankeITrygderettenbehandling.feilregistrering.toView(),
            fagsystemId = ankeITrygderettenbehandling.fagsystem.id,
            relevantDocumentIdList = ankeITrygderettenbehandling.saksdokumenter.map {
                it.dokumentInfoId
            }.toSet(),
            saksnummer = ankeITrygderettenbehandling.fagsakId,
            rol = null,
            medunderskriver = ankeITrygderettenbehandling.toMedunderskriverView(),
            saksbehandler = ankeITrygderettenbehandling.toSaksbehandlerView(),
            previousSaksbehandler = ankeITrygderettenbehandling.toPreviousSaksbehandlerView(),
        )
    }

    fun getSakenGjelderView(sakenGjelder: SakenGjelder): BehandlingDetaljerView.SakenGjelderView {
        if (sakenGjelder.erPerson()) {
            val person = pdlFacade.getPersonInfo(sakenGjelder.partId.value)
            val krrInfo = krrProxyClient.getDigitalKontaktinformasjonForFnr(sakenGjelder.partId.value)
            return BehandlingDetaljerView.SakenGjelderView(
                id = person.foedselsnr,
                name = person.settSammenNavn(),
                sex = person.kjoenn?.let { BehandlingDetaljerView.Sex.valueOf(it) }
                    ?: BehandlingDetaljerView.Sex.UKJENT,
                type = BehandlingDetaljerView.IdType.FNR,
                available = person.doed == null,
                language = krrInfo?.spraak,
                statusList = getStatusList(person, krrInfo),
                address = regoppslagService.getAddressForPerson(fnr = person.foedselsnr),
            )
        } else {
            throw RuntimeException("We don't support where sakenGjelder is virksomhet")
        }
    }

    fun getPartView(partId: PartId): BehandlingDetaljerView.PartView {
        return getPartView(
            identificator = partId.value,
            isPerson = partId.isPerson()
        )
    }

    private fun getPartView(identificator: String, isPerson: Boolean): BehandlingDetaljerView.PartView {
        return if (isPerson) {
            val person = pdlFacade.getPersonInfo(identificator)
            val krrInfo = krrProxyClient.getDigitalKontaktinformasjonForFnr(identificator)
            BehandlingDetaljerView.PartView(
                id = person.foedselsnr,
                name = person.settSammenNavn(),
                type = BehandlingDetaljerView.IdType.FNR,
                available = person.doed == null,
                language = krrInfo?.spraak,
                statusList = getStatusList(person, krrInfo),
                address = regoppslagService.getAddressForPerson(fnr = person.foedselsnr),
            )
        } else {
            val organisasjon = eregClient.hentNoekkelInformasjonOmOrganisasjon(identificator)
            BehandlingDetaljerView.PartView(
                id = identificator,
                name = organisasjon.navn.sammensattnavn,
                type = BehandlingDetaljerView.IdType.ORGNR,
                available = organisasjon.isActive(),
                language = null,
                statusList = getStatusList(organisasjon),
                address = getAddress(organisasjon),
            )
        }
    }

    fun getAddress(organisasjon: NoekkelInfoOmOrganisasjon): BehandlingDetaljerView.Address {
        return BehandlingDetaljerView.Address(
            adresselinje1 = organisasjon.adresse.adresselinje1,
            adresselinje2 = organisasjon.adresse.adresselinje2,
            adresselinje3 = organisasjon.adresse.adresselinje3,
            landkode = organisasjon.adresse.landkode,
            postnummer = organisasjon.adresse.postnummer,
            poststed = organisasjon.adresse.poststed ?: kodeverkService.getPoststed(organisasjon.adresse.postnummer),
        )
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

    private fun SakenGjelder.harVergemaalEllerFremtidsfullmakt(): Boolean {
        return if (erVirksomhet()) {
            false
        } else {
            pdlFacade.getPersonInfo(partId.value).vergemaalEllerFremtidsfullmakt
        }
    }

    private fun SakenGjelder.getDead(): LocalDate? {
        return if (erVirksomhet()) {
            null
        } else {
            pdlFacade.getPersonInfo(partId.value).doed
        }
    }

    private fun SakenGjelder.isFullmakt(): Boolean {
        return if (erVirksomhet()) {
            false
        } else {
            pdlFacade.getPersonInfo(partId.value).fullmakt
        }
    }

    fun Behandling.mapToVedtakView(): VedtakView {
        return VedtakView(
            id = id,
            utfallId = utfall?.id,
            extraUtfallIdSet = extraUtfallSet.map { it.id }.toSet(),
            hjemmelIdSet = registreringshjemler.map { it.id }.toSet(),
        )
    }

    fun mapToBehandlingFullfoertView(behandling: Behandling): BehandlingFullfoertView {
        return BehandlingFullfoertView(
            modified = behandling.modified,
            isAvsluttetAvSaksbehandler = behandling.avsluttetAvSaksbehandler != null
        )
    }

    fun mapToMedunderskriverFlowStateResponse(behandling: Behandling): MedunderskriverFlowStateResponse {
        return MedunderskriverFlowStateResponse(
            employee = if (behandling.medunderskriver?.saksbehandlerident != null) {
                SaksbehandlerView(
                    navIdent = behandling.medunderskriver?.saksbehandlerident!!,
                    navn = saksbehandlerService.getNameForIdentDefaultIfNull(behandling.medunderskriver?.saksbehandlerident!!),
                )
            } else null,
            modified = behandling.modified,
            flowState = behandling.medunderskriverFlowState,
        )
    }

    fun mapToMedunderskriverWrapped(behandling: Behandling): MedunderskriverWrapped {
        return MedunderskriverWrapped(
            employee = if (behandling.medunderskriver?.saksbehandlerident != null) {
                SaksbehandlerView(
                    navIdent = behandling.medunderskriver?.saksbehandlerident!!,
                    navn = saksbehandlerService.getNameForIdentDefaultIfNull(behandling.medunderskriver?.saksbehandlerident!!),
                )
            } else null,
            modified = behandling.modified,
            flowState = behandling.medunderskriverFlowState,
        )
    }

    fun mapToMedunderskriverFlowStateView(behandling: Behandling): FlowStateView {
        return FlowStateView(
            flowState = behandling.medunderskriverFlowState
        )
    }

    fun mapToRolView(behandling: Behandling) = RolView(
        employee = if (behandling.rolIdent != null) {
            SaksbehandlerView(
                navIdent = behandling.rolIdent!!,
                navn = saksbehandlerService.getNameForIdentDefaultIfNull(behandling.rolIdent!!),
            )
        } else null,
        flowState = behandling.rolFlowState,
        modified = behandling.modified,
    )

    private fun Feilregistrering?.toView(): BehandlingDetaljerView.FeilregistreringView? {
        return this?.let {
            BehandlingDetaljerView.FeilregistreringView(
                feilregistrertAv = SaksbehandlerView(
                    navIdent = it.navIdent,
                    navn = saksbehandlerService.getNameForIdentDefaultIfNull(it.navIdent)
                ),
                registered = it.registered,
                reason = it.reason,
                fagsystemId = it.fagsystem.id
            )
        }
    }

    fun getStatusList(pdlPerson: Person, krrInfo: DigitalKontaktinformasjon?): List<BehandlingDetaljerView.PartStatus> {
        val statusList = mutableListOf<BehandlingDetaljerView.PartStatus>()

        if (pdlPerson.doed != null) {
            statusList += BehandlingDetaljerView.PartStatus(
                status = BehandlingDetaljerView.PartStatus.Status.DEAD,
                date = pdlPerson.doed,
            )
        }
        if (pdlPerson.fullmakt) {
            statusList += BehandlingDetaljerView.PartStatus(
                status = BehandlingDetaljerView.PartStatus.Status.FULLMAKT,
            )
        }
        if (pdlPerson.vergemaalEllerFremtidsfullmakt) {
            statusList += BehandlingDetaljerView.PartStatus(
                status = BehandlingDetaljerView.PartStatus.Status.VERGEMAAL,
            )
        }
        if (pdlPerson.harBeskyttelsesbehovFortrolig()) {
            statusList += BehandlingDetaljerView.PartStatus(
                status = BehandlingDetaljerView.PartStatus.Status.FORTROLIG,
            )
        }
        if (pdlPerson.harBeskyttelsesbehovStrengtFortrolig()) {
            statusList += BehandlingDetaljerView.PartStatus(
                status = BehandlingDetaljerView.PartStatus.Status.STRENGT_FORTROLIG,
            )
        }
        if (egenAnsattService.erEgenAnsatt(pdlPerson.foedselsnr)) {
            statusList += BehandlingDetaljerView.PartStatus(
                status = BehandlingDetaljerView.PartStatus.Status.EGEN_ANSATT,
            )
        }
        if (krrInfo?.reservert == true) {
            statusList += BehandlingDetaljerView.PartStatus(
                status = BehandlingDetaljerView.PartStatus.Status.RESERVERT_I_KRR,
            )
        }

        return statusList
    }

    fun getStatusList(organisasjon: NoekkelInfoOmOrganisasjon): List<BehandlingDetaljerView.PartStatus> {
        val statusList = mutableListOf<BehandlingDetaljerView.PartStatus>()

        if (!organisasjon.isActive()) {
            statusList.add(
                BehandlingDetaljerView.PartStatus(
                    status = BehandlingDetaljerView.PartStatus.Status.DELETED,
                    date = organisasjon.opphoersdato,
                )
            )
        }

        if (organisasjon.enhetstype == "DA") {
            statusList.add(
                BehandlingDetaljerView.PartStatus(
                    status = BehandlingDetaljerView.PartStatus.Status.DELT_ANSVAR,
                    date = null,
                )
            )
        }
        return statusList
    }
}