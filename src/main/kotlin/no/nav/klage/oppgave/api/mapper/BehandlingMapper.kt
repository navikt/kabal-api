package no.nav.klage.oppgave.api.mapper

import no.nav.klage.dokument.domain.dokumenterunderarbeid.Adresse
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.clients.egenansatt.EgenAnsattService
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.ereg.NoekkelInfoOmOrganisasjon
import no.nav.klage.oppgave.clients.krrproxy.DigitalKontaktinformasjon
import no.nav.klage.oppgave.clients.krrproxy.KrrProxyClient
import no.nav.klage.oppgave.clients.norg2.Enhet
import no.nav.klage.oppgave.clients.norg2.Norg2Client
import no.nav.klage.oppgave.domain.behandling.*
import no.nav.klage.oppgave.domain.behandling.embedded.Feilregistrering
import no.nav.klage.oppgave.domain.behandling.embedded.PartId
import no.nav.klage.oppgave.domain.behandling.embedded.SakenGjelder
import no.nav.klage.oppgave.domain.person.Person
import no.nav.klage.oppgave.service.*
import no.nav.klage.oppgave.util.getLogger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.*

@Service
@Transactional
class BehandlingMapper(
    private val egenAnsattService: EgenAnsattService,
    private val norg2Client: Norg2Client,
    private val eregClient: EregClient,
    private val saksbehandlerService: SaksbehandlerService,
    private val krrProxyClient: KrrProxyClient,
    private val kodeverkService: KodeverkService,
    private val regoppslagService: RegoppslagService,
    private val dokDistKanalService: DokDistKanalService,
    private val personService: PersonService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun mapBehandlingToBehandlingDetaljerView(behandling: Behandling): BehandlingDetaljerView {
        return when (behandling) {
            is Klagebehandling -> mapKlagebehandlingToBehandlingDetaljerView(behandling)
            is Ankebehandling -> mapAnkebehandlingToBehandlingDetaljerView(behandling)
            is AnkeITrygderettenbehandling -> mapAnkeITrygderettenbehandlingToBehandlingDetaljerView(behandling)
            is BehandlingEtterTrygderettenOpphevet -> mapBehandlingEtterTROpphevetToBehandlingDetaljerView(behandling)
            is Omgjoeringskravbehandling -> mapOmgjoeringskravbehandlingToBehandlingDetaljerView(behandling)
            is Gjenopptaksbehandling -> mapGjenopptaksbehandlingToBehandlingDetaljerView(behandling)
            is GjenopptakITrygderettenbehandling -> mapGjenopptakITrygderettenbehandlingToBehandlingDetaljerView(behandling)
        }
    }

    fun mapKlagebehandlingToBehandlingDetaljerView(klagebehandling: Klagebehandling): BehandlingDetaljerView {
        val enhetNavn = klagebehandling.avsenderEnhetFoersteinstans.let { norg2Client.fetchEnhet(it) }.navn

        return BehandlingDetaljerView(
            id = klagebehandling.id,
            fraNAVEnhet = klagebehandling.avsenderEnhetFoersteinstans,
            fraNAVEnhetNavn = enhetNavn,
            mottattVedtaksinstans = klagebehandling.mottattVedtaksinstans,
            sakenGjelder = getSakenGjelderViewWithUtsendingskanal(klagebehandling),
            klager = getPartViewWithUtsendingskanal(
                technicalPartId = klagebehandling.klager.id,
                partId = klagebehandling.klager.partId,
                behandling = klagebehandling,
                navn = null,
                address = null,
            ),
            prosessfullmektig = klagebehandling.prosessfullmektig?.let {
                getPartViewWithUtsendingskanal(
                    technicalPartId = it.id,
                    partId = it.partId,
                    behandling = klagebehandling,
                    navn = it.navn,
                    address = it.address,
                )
            },
            temaId = klagebehandling.ytelse.toTema().id,
            ytelseId = klagebehandling.ytelse.id,
            typeId = klagebehandling.type.id,
            mottattKlageinstans = klagebehandling.mottattKlageinstans.toLocalDate(),
            tildelt = klagebehandling.tildeling?.tidspunkt?.toLocalDate(),
            avsluttetAvSaksbehandlerDate = klagebehandling.ferdigstilling?.avsluttetAvSaksbehandler?.toLocalDate(),
            isAvsluttetAvSaksbehandler = klagebehandling.ferdigstilling != null,
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
            sikkerhetstiltak = klagebehandling.sakenGjelder.sikkerhetstiltak(),
            kvalitetsvurderingReference = if (klagebehandling.feilregistrering == null && klagebehandling.kakaKvalitetsvurderingId != null) {
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
            varsletFrist = klagebehandling.varsletBehandlingstid?.varsletFrist,
            gosysOppgaveId = klagebehandling.gosysOppgaveId,
            tilbakekreving = klagebehandling.tilbakekreving,
            timesPreviouslyExtended = klagebehandling.getTimesPreviouslyExtended(),
            requiresGosysOppgave = klagebehandling.gosysOppgaveRequired,
        )
    }

    fun mapOmgjoeringskravbehandlingToBehandlingDetaljerView(omgjoeringskravbehandling: Omgjoeringskravbehandling): BehandlingDetaljerView {
        val enhetNavn = omgjoeringskravbehandling.klageBehandlendeEnhet.let { norg2Client.fetchEnhet(it) }.navn

        return BehandlingDetaljerView(
            id = omgjoeringskravbehandling.id,
            fraNAVEnhet = omgjoeringskravbehandling.klageBehandlendeEnhet,
            fraNAVEnhetNavn = enhetNavn,
            sakenGjelder = getSakenGjelderViewWithUtsendingskanal(omgjoeringskravbehandling),
            klager = getPartViewWithUtsendingskanal(
                technicalPartId = omgjoeringskravbehandling.klager.id,
                partId = omgjoeringskravbehandling.klager.partId,
                behandling = omgjoeringskravbehandling,
                navn = null,
                address = null,
            ),
            prosessfullmektig = omgjoeringskravbehandling.prosessfullmektig?.let {
                getPartViewWithUtsendingskanal(
                    technicalPartId = it.id,
                    partId = it.partId,
                    behandling = omgjoeringskravbehandling,
                    navn = it.navn,
                    address = it.address,
                )
            },
            temaId = omgjoeringskravbehandling.ytelse.toTema().id,
            ytelseId = omgjoeringskravbehandling.ytelse.id,
            typeId = omgjoeringskravbehandling.type.id,
            mottattKlageinstans = omgjoeringskravbehandling.mottattKlageinstans.toLocalDate(),
            tildelt = omgjoeringskravbehandling.tildeling?.tidspunkt?.toLocalDate(),
            avsluttetAvSaksbehandlerDate = omgjoeringskravbehandling.ferdigstilling?.avsluttetAvSaksbehandler?.toLocalDate(),
            isAvsluttetAvSaksbehandler = omgjoeringskravbehandling.ferdigstilling != null,
            frist = omgjoeringskravbehandling.frist,
            datoSendtMedunderskriver = omgjoeringskravbehandling.medunderskriver?.tidspunkt?.toLocalDate(),
            hjemmelIdList = omgjoeringskravbehandling.hjemler.map { it.id },
            modified = omgjoeringskravbehandling.modified,
            created = omgjoeringskravbehandling.created,
            resultat = omgjoeringskravbehandling.mapToVedtakView(),
            tilknyttedeDokumenter = omgjoeringskravbehandling.saksdokumenter.map {
                TilknyttetDokument(
                    journalpostId = it.journalpostId,
                    dokumentInfoId = it.dokumentInfoId
                )
            }.toSet(),
            egenAnsatt = omgjoeringskravbehandling.sakenGjelder.erEgenAnsatt(),
            fortrolig = omgjoeringskravbehandling.sakenGjelder.harBeskyttelsesbehovFortrolig(),
            strengtFortrolig = omgjoeringskravbehandling.sakenGjelder.harBeskyttelsesbehovStrengtFortrolig(),
            vergemaalEllerFremtidsfullmakt = omgjoeringskravbehandling.sakenGjelder.harVergemaalEllerFremtidsfullmakt(),
            dead = omgjoeringskravbehandling.sakenGjelder.getDead(),
            sikkerhetstiltak = omgjoeringskravbehandling.sakenGjelder.sikkerhetstiltak(),
            kvalitetsvurderingReference = if (omgjoeringskravbehandling.feilregistrering == null && omgjoeringskravbehandling.kakaKvalitetsvurderingId != null) {
                BehandlingDetaljerView.KvalitetsvurderingReference(
                    id = omgjoeringskravbehandling.kakaKvalitetsvurderingId!!,
                    version = omgjoeringskravbehandling.kakaKvalitetsvurderingVersion,
                )
            } else null,
            sattPaaVent = omgjoeringskravbehandling.sattPaaVent,
            feilregistrering = omgjoeringskravbehandling.feilregistrering.toView(),
            fagsystemId = omgjoeringskravbehandling.fagsystem.id,
            relevantDocumentIdList = omgjoeringskravbehandling.saksdokumenter.map {
                it.dokumentInfoId
            }.toSet(),
            saksnummer = omgjoeringskravbehandling.fagsakId,
            rol = omgjoeringskravbehandling.toROLView(),
            medunderskriver = omgjoeringskravbehandling.toMedunderskriverView(),
            saksbehandler = omgjoeringskravbehandling.toSaksbehandlerView(),
            previousSaksbehandler = omgjoeringskravbehandling.toPreviousSaksbehandlerView(),
            varsletFrist = omgjoeringskravbehandling.varsletBehandlingstid?.varsletFrist,
            gosysOppgaveId = omgjoeringskravbehandling.gosysOppgaveId,
            kommentarFraVedtaksinstans = null,
            tilbakekreving = omgjoeringskravbehandling.tilbakekreving,
            timesPreviouslyExtended = omgjoeringskravbehandling.getTimesPreviouslyExtended(),
            requiresGosysOppgave = omgjoeringskravbehandling.gosysOppgaveRequired
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
            returnedFromROLDate = rolReturnedDate?.toLocalDate(),
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
            returnedFromROLDate = null,
        )
    }

    fun mapAnkebehandlingToBehandlingDetaljerView(ankebehandling: Ankebehandling): BehandlingDetaljerView {
        val forrigeEnhetNavn = ankebehandling.klageBehandlendeEnhet.let { norg2Client.fetchEnhet(it) }.navn

        return BehandlingDetaljerView(
            id = ankebehandling.id,
            fraNAVEnhet = ankebehandling.klageBehandlendeEnhet,
            fraNAVEnhetNavn = forrigeEnhetNavn,
            mottattVedtaksinstans = null,
            sakenGjelder = getSakenGjelderViewWithUtsendingskanal(ankebehandling),
            klager = getPartViewWithUtsendingskanal(
                technicalPartId = ankebehandling.klager.id,
                partId = ankebehandling.klager.partId,
                behandling = ankebehandling,
                navn = null,
                address = null,
            ),
            prosessfullmektig = ankebehandling.prosessfullmektig?.let {
                getPartViewWithUtsendingskanal(
                    technicalPartId = it.id,
                    partId = it.partId,
                    behandling = ankebehandling,
                    navn = it.navn,
                    address = it.address,
                )
            },
            temaId = ankebehandling.ytelse.toTema().id,
            ytelseId = ankebehandling.ytelse.id,
            typeId = ankebehandling.type.id,
            mottattKlageinstans = ankebehandling.mottattKlageinstans.toLocalDate(),
            tildelt = ankebehandling.tildeling?.tidspunkt?.toLocalDate(),
            avsluttetAvSaksbehandlerDate = ankebehandling.ferdigstilling?.avsluttetAvSaksbehandler?.toLocalDate(),
            isAvsluttetAvSaksbehandler = ankebehandling.ferdigstilling != null,
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
            sikkerhetstiltak = ankebehandling.sakenGjelder.sikkerhetstiltak(),
            kvalitetsvurderingReference = if (ankebehandling.feilregistrering == null && ankebehandling.kakaKvalitetsvurderingId != null) {
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
            varsletFrist = ankebehandling.varsletBehandlingstid?.varsletFrist,
            gosysOppgaveId = ankebehandling.gosysOppgaveId,
            tilbakekreving = ankebehandling.tilbakekreving,
            timesPreviouslyExtended = ankebehandling.getTimesPreviouslyExtended(),
            requiresGosysOppgave = ankebehandling.gosysOppgaveRequired,
        )
    }

    fun mapAnkeITrygderettenbehandlingToBehandlingDetaljerView(ankeITrygderettenbehandling: AnkeITrygderettenbehandling): BehandlingDetaljerView {
        return BehandlingDetaljerView(
            id = ankeITrygderettenbehandling.id,
            fraNAVEnhet = null,
            fraNAVEnhetNavn = null,
            mottattVedtaksinstans = null,
            sakenGjelder = getSakenGjelderViewWithUtsendingskanal(ankeITrygderettenbehandling),
            klager = getPartViewWithUtsendingskanal(
                technicalPartId = ankeITrygderettenbehandling.klager.id,
                partId = ankeITrygderettenbehandling.klager.partId,
                behandling = ankeITrygderettenbehandling,
                navn = null,
                address = null,
            ),
            prosessfullmektig = ankeITrygderettenbehandling.prosessfullmektig?.let {
                getPartViewWithUtsendingskanal(
                    technicalPartId = it.id,
                    partId = it.partId,
                    behandling = ankeITrygderettenbehandling,
                    navn = it.navn,
                    address = it.address,
                )
            },
            temaId = ankeITrygderettenbehandling.ytelse.toTema().id,
            ytelseId = ankeITrygderettenbehandling.ytelse.id,
            typeId = ankeITrygderettenbehandling.type.id,
            mottattKlageinstans = ankeITrygderettenbehandling.mottattKlageinstans.toLocalDate(),
            tildelt = ankeITrygderettenbehandling.tildeling?.tidspunkt?.toLocalDate(),
            avsluttetAvSaksbehandlerDate = ankeITrygderettenbehandling.ferdigstilling?.avsluttetAvSaksbehandler?.toLocalDate(),
            isAvsluttetAvSaksbehandler = ankeITrygderettenbehandling.ferdigstilling != null,
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
            sikkerhetstiltak = ankeITrygderettenbehandling.sakenGjelder.sikkerhetstiltak(),
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
            rol = ankeITrygderettenbehandling.toROLView(),
            medunderskriver = ankeITrygderettenbehandling.toMedunderskriverView(),
            saksbehandler = ankeITrygderettenbehandling.toSaksbehandlerView(),
            previousSaksbehandler = ankeITrygderettenbehandling.toPreviousSaksbehandlerView(),
            varsletFrist = null,
            gosysOppgaveId = ankeITrygderettenbehandling.gosysOppgaveId,
            tilbakekreving = ankeITrygderettenbehandling.tilbakekreving,
            timesPreviouslyExtended = ankeITrygderettenbehandling.getTimesPreviouslyExtended(),
            requiresGosysOppgave = ankeITrygderettenbehandling.gosysOppgaveRequired,
        )
    }

    fun mapBehandlingEtterTROpphevetToBehandlingDetaljerView(behandlingEtterTrygderettenOpphevet: BehandlingEtterTrygderettenOpphevet): BehandlingDetaljerView {
        val forrigeEnhetNavn =
            behandlingEtterTrygderettenOpphevet.ankeBehandlendeEnhet.let { norg2Client.fetchEnhet(it) }.navn

        return BehandlingDetaljerView(
            id = behandlingEtterTrygderettenOpphevet.id,
            fraNAVEnhet = behandlingEtterTrygderettenOpphevet.ankeBehandlendeEnhet,
            fraNAVEnhetNavn = forrigeEnhetNavn,
            mottattVedtaksinstans = null,
            sakenGjelder = getSakenGjelderViewWithUtsendingskanal(behandlingEtterTrygderettenOpphevet),
            klager = getPartViewWithUtsendingskanal(
                technicalPartId = behandlingEtterTrygderettenOpphevet.klager.id,
                partId = behandlingEtterTrygderettenOpphevet.klager.partId,
                behandling = behandlingEtterTrygderettenOpphevet,
                navn = null,
                address = null,
            ),
            prosessfullmektig = behandlingEtterTrygderettenOpphevet.prosessfullmektig?.let {
                getPartViewWithUtsendingskanal(
                    technicalPartId = it.id,
                    partId = it.partId,
                    behandling = behandlingEtterTrygderettenOpphevet,
                    navn = it.navn,
                    address = it.address,
                )
            },
            temaId = behandlingEtterTrygderettenOpphevet.ytelse.toTema().id,
            ytelseId = behandlingEtterTrygderettenOpphevet.ytelse.id,
            typeId = behandlingEtterTrygderettenOpphevet.type.id,
            mottattKlageinstans = behandlingEtterTrygderettenOpphevet.mottattKlageinstans.toLocalDate(),
            tildelt = behandlingEtterTrygderettenOpphevet.tildeling?.tidspunkt?.toLocalDate(),
            avsluttetAvSaksbehandlerDate = behandlingEtterTrygderettenOpphevet.ferdigstilling?.avsluttetAvSaksbehandler?.toLocalDate(),
            isAvsluttetAvSaksbehandler = behandlingEtterTrygderettenOpphevet.ferdigstilling != null,
            frist = behandlingEtterTrygderettenOpphevet.frist,
            datoSendtMedunderskriver = behandlingEtterTrygderettenOpphevet.medunderskriver?.tidspunkt?.toLocalDate(),
            hjemmelIdList = behandlingEtterTrygderettenOpphevet.hjemler.map { it.id },
            modified = behandlingEtterTrygderettenOpphevet.modified,
            created = behandlingEtterTrygderettenOpphevet.created,
            resultat = behandlingEtterTrygderettenOpphevet.mapToVedtakView(),
            kommentarFraVedtaksinstans = null,
            tilknyttedeDokumenter = behandlingEtterTrygderettenOpphevet.saksdokumenter.map {
                TilknyttetDokument(
                    journalpostId = it.journalpostId,
                    dokumentInfoId = it.dokumentInfoId
                )
            }.toSet(),
            egenAnsatt = behandlingEtterTrygderettenOpphevet.sakenGjelder.erEgenAnsatt(),
            fortrolig = behandlingEtterTrygderettenOpphevet.sakenGjelder.harBeskyttelsesbehovFortrolig(),
            strengtFortrolig = behandlingEtterTrygderettenOpphevet.sakenGjelder.harBeskyttelsesbehovStrengtFortrolig(),
            vergemaalEllerFremtidsfullmakt = behandlingEtterTrygderettenOpphevet.sakenGjelder.harVergemaalEllerFremtidsfullmakt(),
            dead = behandlingEtterTrygderettenOpphevet.sakenGjelder.getDead(),
            sikkerhetstiltak = behandlingEtterTrygderettenOpphevet.sakenGjelder.sikkerhetstiltak(),
            kvalitetsvurderingReference = if (behandlingEtterTrygderettenOpphevet.feilregistrering == null && behandlingEtterTrygderettenOpphevet.kakaKvalitetsvurderingId != null) {
                BehandlingDetaljerView.KvalitetsvurderingReference(
                    id = behandlingEtterTrygderettenOpphevet.kakaKvalitetsvurderingId!!,
                    version = behandlingEtterTrygderettenOpphevet.kakaKvalitetsvurderingVersion,
                )
            } else null,
            sattPaaVent = behandlingEtterTrygderettenOpphevet.sattPaaVent,
            feilregistrering = behandlingEtterTrygderettenOpphevet.feilregistrering.toView(),
            fagsystemId = behandlingEtterTrygderettenOpphevet.fagsystem.id,
            relevantDocumentIdList = behandlingEtterTrygderettenOpphevet.saksdokumenter.map {
                it.dokumentInfoId
            }.toSet(),
            saksnummer = behandlingEtterTrygderettenOpphevet.fagsakId,
            rol = behandlingEtterTrygderettenOpphevet.toROLView(),
            medunderskriver = behandlingEtterTrygderettenOpphevet.toMedunderskriverView(),
            saksbehandler = behandlingEtterTrygderettenOpphevet.toSaksbehandlerView(),
            previousSaksbehandler = behandlingEtterTrygderettenOpphevet.toPreviousSaksbehandlerView(),
            varsletFrist = behandlingEtterTrygderettenOpphevet.varsletBehandlingstid?.varsletFrist,
            kjennelseMottatt = behandlingEtterTrygderettenOpphevet.kjennelseMottatt,
            gosysOppgaveId = behandlingEtterTrygderettenOpphevet.gosysOppgaveId,
            tilbakekreving = behandlingEtterTrygderettenOpphevet.tilbakekreving,
            timesPreviouslyExtended = behandlingEtterTrygderettenOpphevet.getTimesPreviouslyExtended(),
            requiresGosysOppgave = behandlingEtterTrygderettenOpphevet.gosysOppgaveRequired
        )
    }

    fun mapGjenopptaksbehandlingToBehandlingDetaljerView(gjenopptaksbehandling: Gjenopptaksbehandling): BehandlingDetaljerView {
        val forrigeEnhetNavn = gjenopptaksbehandling.klageBehandlendeEnhet.let { norg2Client.fetchEnhet(it) }.navn

        return BehandlingDetaljerView(
            id = gjenopptaksbehandling.id,
            fraNAVEnhet = gjenopptaksbehandling.klageBehandlendeEnhet,
            fraNAVEnhetNavn = forrigeEnhetNavn,
            mottattVedtaksinstans = null,
            sakenGjelder = getSakenGjelderViewWithUtsendingskanal(gjenopptaksbehandling),
            klager = getPartViewWithUtsendingskanal(
                technicalPartId = gjenopptaksbehandling.klager.id,
                partId = gjenopptaksbehandling.klager.partId,
                behandling = gjenopptaksbehandling,
                navn = null,
                address = null,
            ),
            prosessfullmektig = gjenopptaksbehandling.prosessfullmektig?.let {
                getPartViewWithUtsendingskanal(
                    technicalPartId = it.id,
                    partId = it.partId,
                    behandling = gjenopptaksbehandling,
                    navn = it.navn,
                    address = it.address,
                )
            },
            temaId = gjenopptaksbehandling.ytelse.toTema().id,
            ytelseId = gjenopptaksbehandling.ytelse.id,
            typeId = gjenopptaksbehandling.type.id,
            mottattKlageinstans = gjenopptaksbehandling.mottattKlageinstans.toLocalDate(),
            tildelt = gjenopptaksbehandling.tildeling?.tidspunkt?.toLocalDate(),
            avsluttetAvSaksbehandlerDate = gjenopptaksbehandling.ferdigstilling?.avsluttetAvSaksbehandler?.toLocalDate(),
            isAvsluttetAvSaksbehandler = gjenopptaksbehandling.ferdigstilling != null,
            frist = gjenopptaksbehandling.frist,
            datoSendtMedunderskriver = gjenopptaksbehandling.medunderskriver?.tidspunkt?.toLocalDate(),
            hjemmelIdList = gjenopptaksbehandling.hjemler.map { it.id },
            modified = gjenopptaksbehandling.modified,
            created = gjenopptaksbehandling.created,
            resultat = gjenopptaksbehandling.mapToVedtakView(),
            kommentarFraVedtaksinstans = null,
            tilknyttedeDokumenter = gjenopptaksbehandling.saksdokumenter.map {
                TilknyttetDokument(
                    journalpostId = it.journalpostId,
                    dokumentInfoId = it.dokumentInfoId
                )
            }.toSet(),
            egenAnsatt = gjenopptaksbehandling.sakenGjelder.erEgenAnsatt(),
            fortrolig = gjenopptaksbehandling.sakenGjelder.harBeskyttelsesbehovFortrolig(),
            strengtFortrolig = gjenopptaksbehandling.sakenGjelder.harBeskyttelsesbehovStrengtFortrolig(),
            vergemaalEllerFremtidsfullmakt = gjenopptaksbehandling.sakenGjelder.harVergemaalEllerFremtidsfullmakt(),
            dead = gjenopptaksbehandling.sakenGjelder.getDead(),
            sikkerhetstiltak = gjenopptaksbehandling.sakenGjelder.sikkerhetstiltak(),
            kvalitetsvurderingReference = if (gjenopptaksbehandling.feilregistrering == null && gjenopptaksbehandling.kakaKvalitetsvurderingId != null) {
                BehandlingDetaljerView.KvalitetsvurderingReference(
                    id = gjenopptaksbehandling.kakaKvalitetsvurderingId!!,
                    version = gjenopptaksbehandling.kakaKvalitetsvurderingVersion,
                )
            } else null,
            sattPaaVent = gjenopptaksbehandling.sattPaaVent,
            feilregistrering = gjenopptaksbehandling.feilregistrering.toView(),
            fagsystemId = gjenopptaksbehandling.fagsystem.id,
            relevantDocumentIdList = gjenopptaksbehandling.saksdokumenter.map {
                it.dokumentInfoId
            }.toSet(),
            saksnummer = gjenopptaksbehandling.fagsakId,
            rol = gjenopptaksbehandling.toROLView(),
            medunderskriver = gjenopptaksbehandling.toMedunderskriverView(),
            saksbehandler = gjenopptaksbehandling.toSaksbehandlerView(),
            previousSaksbehandler = gjenopptaksbehandling.toPreviousSaksbehandlerView(),
            varsletFrist = gjenopptaksbehandling.varsletBehandlingstid?.varsletFrist,
            gosysOppgaveId = gjenopptaksbehandling.gosysOppgaveId,
            tilbakekreving = gjenopptaksbehandling.tilbakekreving,
            timesPreviouslyExtended = gjenopptaksbehandling.getTimesPreviouslyExtended(),
            requiresGosysOppgave = gjenopptaksbehandling.gosysOppgaveRequired,
        )
    }

    fun mapGjenopptakITrygderettenbehandlingToBehandlingDetaljerView(gjenopptakITrygderettenbehandling: GjenopptakITrygderettenbehandling): BehandlingDetaljerView {
        return BehandlingDetaljerView(
            id = gjenopptakITrygderettenbehandling.id,
            fraNAVEnhet = null,
            fraNAVEnhetNavn = null,
            mottattVedtaksinstans = null,
            sakenGjelder = getSakenGjelderViewWithUtsendingskanal(gjenopptakITrygderettenbehandling),
            klager = getPartViewWithUtsendingskanal(
                technicalPartId = gjenopptakITrygderettenbehandling.klager.id,
                partId = gjenopptakITrygderettenbehandling.klager.partId,
                behandling = gjenopptakITrygderettenbehandling,
                navn = null,
                address = null,
            ),
            prosessfullmektig = gjenopptakITrygderettenbehandling.prosessfullmektig?.let {
                getPartViewWithUtsendingskanal(
                    technicalPartId = it.id,
                    partId = it.partId,
                    behandling = gjenopptakITrygderettenbehandling,
                    navn = it.navn,
                    address = it.address,
                )
            },
            temaId = gjenopptakITrygderettenbehandling.ytelse.toTema().id,
            ytelseId = gjenopptakITrygderettenbehandling.ytelse.id,
            typeId = gjenopptakITrygderettenbehandling.type.id,
            mottattKlageinstans = gjenopptakITrygderettenbehandling.mottattKlageinstans.toLocalDate(),
            tildelt = gjenopptakITrygderettenbehandling.tildeling?.tidspunkt?.toLocalDate(),
            avsluttetAvSaksbehandlerDate = gjenopptakITrygderettenbehandling.ferdigstilling?.avsluttetAvSaksbehandler?.toLocalDate(),
            isAvsluttetAvSaksbehandler = gjenopptakITrygderettenbehandling.ferdigstilling != null,
            frist = gjenopptakITrygderettenbehandling.frist,
            datoSendtMedunderskriver = gjenopptakITrygderettenbehandling.medunderskriver?.tidspunkt?.toLocalDate(),
            hjemmelIdList = gjenopptakITrygderettenbehandling.hjemler.map { it.id },
            modified = gjenopptakITrygderettenbehandling.modified,
            created = gjenopptakITrygderettenbehandling.created,
            resultat = gjenopptakITrygderettenbehandling.mapToVedtakView(),
            kommentarFraVedtaksinstans = null,
            tilknyttedeDokumenter = gjenopptakITrygderettenbehandling.saksdokumenter.map {
                TilknyttetDokument(
                    journalpostId = it.journalpostId,
                    dokumentInfoId = it.dokumentInfoId
                )
            }.toSet(),
            egenAnsatt = gjenopptakITrygderettenbehandling.sakenGjelder.erEgenAnsatt(),
            fortrolig = gjenopptakITrygderettenbehandling.sakenGjelder.harBeskyttelsesbehovFortrolig(),
            strengtFortrolig = gjenopptakITrygderettenbehandling.sakenGjelder.harBeskyttelsesbehovStrengtFortrolig(),
            vergemaalEllerFremtidsfullmakt = gjenopptakITrygderettenbehandling.sakenGjelder.harVergemaalEllerFremtidsfullmakt(),
            dead = gjenopptakITrygderettenbehandling.sakenGjelder.getDead(),
            sikkerhetstiltak = gjenopptakITrygderettenbehandling.sakenGjelder.sikkerhetstiltak(),
            kvalitetsvurderingReference = null,
            sattPaaVent = gjenopptakITrygderettenbehandling.sattPaaVent,
            sendtTilTrygderetten = gjenopptakITrygderettenbehandling.sendtTilTrygderetten,
            kjennelseMottatt = gjenopptakITrygderettenbehandling.kjennelseMottatt,
            feilregistrering = gjenopptakITrygderettenbehandling.feilregistrering.toView(),
            fagsystemId = gjenopptakITrygderettenbehandling.fagsystem.id,
            relevantDocumentIdList = gjenopptakITrygderettenbehandling.saksdokumenter.map {
                it.dokumentInfoId
            }.toSet(),
            saksnummer = gjenopptakITrygderettenbehandling.fagsakId,
            rol = gjenopptakITrygderettenbehandling.toROLView(),
            medunderskriver = gjenopptakITrygderettenbehandling.toMedunderskriverView(),
            saksbehandler = gjenopptakITrygderettenbehandling.toSaksbehandlerView(),
            previousSaksbehandler = gjenopptakITrygderettenbehandling.toPreviousSaksbehandlerView(),
            varsletFrist = null,
            gosysOppgaveId = gjenopptakITrygderettenbehandling.gosysOppgaveId,
            tilbakekreving = gjenopptakITrygderettenbehandling.tilbakekreving,
            timesPreviouslyExtended = gjenopptakITrygderettenbehandling.getTimesPreviouslyExtended(),
            requiresGosysOppgave = gjenopptakITrygderettenbehandling.gosysOppgaveRequired,
        )
    }

    fun getSakenGjelderView(sakenGjelder: SakenGjelder): BehandlingDetaljerView.SakenGjelderView {
        if (sakenGjelder.erPerson()) {
            val person = personService.getPersonInfo(sakenGjelder.partId.value)
            val krrInfo = krrProxyClient.getDigitalKontaktinformasjonForFnrOnBehalfOf(sakenGjelder.partId.value)
            return BehandlingDetaljerView.SakenGjelderView(
                id = sakenGjelder.id,
                identifikator = person.foedselsnr,
                name = person.settSammenNavn(),
                sex = person.kjoenn?.let { BehandlingDetaljerView.Sex.valueOf(it) }
                    ?: BehandlingDetaljerView.Sex.UKJENT,
                type = BehandlingDetaljerView.IdType.FNR,
                available = person.doed == null,
                language = krrInfo?.spraak,
                statusList = getStatusList(person, krrInfo),
                address = regoppslagService.getAddressForPersonOnBehalfOf(fnr = person.foedselsnr),
            )
        } else {
            throw RuntimeException("We don't support where sakenGjelder is virksomhet")
        }
    }

    fun getSakenGjelderViewWithUtsendingskanal(behandling: Behandling): BehandlingDetaljerView.SakenGjelderViewWithUtsendingskanal {
        val sakenGjelder = behandling.sakenGjelder
        if (sakenGjelder.erPerson()) {
            val person = personService.getPersonInfo(sakenGjelder.partId.value)
            val krrInfo = krrProxyClient.getDigitalKontaktinformasjonForFnrOnBehalfOf(sakenGjelder.partId.value)
            val utsendingskanal = dokDistKanalService.getUtsendingskanal(
                mottakerId = sakenGjelder.partId.value,
                brukerId = sakenGjelder.partId.value,
                tema = behandling.ytelse.toTema(),
                saksbehandlerContext = true
            )
            return BehandlingDetaljerView.SakenGjelderViewWithUtsendingskanal(
                id = sakenGjelder.id,
                identifikator = person.foedselsnr,
                name = person.settSammenNavn(),
                sex = person.kjoenn?.let { BehandlingDetaljerView.Sex.valueOf(it) }
                    ?: BehandlingDetaljerView.Sex.UKJENT,
                type = BehandlingDetaljerView.IdType.FNR,
                available = person.doed == null,
                language = krrInfo?.spraak,
                statusList = getStatusList(person, krrInfo),
                address = regoppslagService.getAddressForPersonOnBehalfOf(fnr = person.foedselsnr),
                utsendingskanal = utsendingskanal
            )
        } else {
            throw RuntimeException("We don't support where sakenGjelder is virksomhet")
        }
    }

    fun getAvsenderPartView(partId: PartId, technicalPartId: UUID): BehandlingDetaljerView.PartView {
        return getAvsenderPartView(
            identifier = partId.value,
            isPerson = partId.isPerson(),
            technicalPartId = technicalPartId,
        )
    }

    private fun getAvsenderPartView(identifier: String, isPerson: Boolean, technicalPartId: UUID): BehandlingDetaljerView.PartView {
        return if (isPerson) {
            val person = personService.getPersonInfo(identifier)
            val krrInfo = krrProxyClient.getDigitalKontaktinformasjonForFnrOnBehalfOf(identifier)
            BehandlingDetaljerView.PartView(
                id = technicalPartId,
                identifikator = person.foedselsnr,
                name = person.settSammenNavn(),
                type = BehandlingDetaljerView.IdType.FNR,
                available = person.doed == null,
                language = krrInfo?.spraak,
                statusList = getStatusList(person, krrInfo),
                address = regoppslagService.getAddressForPersonOnBehalfOf(fnr = person.foedselsnr),
            )
        } else {
            val organisasjon = eregClient.hentNoekkelInformasjonOmOrganisasjon(identifier)
            BehandlingDetaljerView.PartView(
                id = technicalPartId,
                identifikator = identifier,
                name = organisasjon.navn.sammensattnavn,
                type = BehandlingDetaljerView.IdType.ORGNR,
                available = organisasjon.isActive(),
                language = null,
                statusList = getStatusList(organisasjon),
                address = getAddress(organisasjon),
            )
        }
    }

    fun getPartViewWithUtsendingskanal(
        technicalPartId: UUID,
        partId: PartId?,
        behandling: Behandling,
        navn: String?,
        address: Adresse?
    ): BehandlingDetaljerView.PartViewWithUtsendingskanal {
        val utsendingskanal = dokDistKanalService.getUtsendingskanal(
            mottakerId = partId?.value,
            brukerId = behandling.sakenGjelder.partId.value,
            tema = behandling.ytelse.toTema(),
            saksbehandlerContext = true,
        )

        return if (partId != null) {
            val isPerson = partId.isPerson()
            val identifier = partId.value

            if (isPerson) {
                val person = personService.getPersonInfo(identifier)
                val krrInfo = krrProxyClient.getDigitalKontaktinformasjonForFnrOnBehalfOf(identifier)
                BehandlingDetaljerView.PartViewWithUtsendingskanal(
                    id = technicalPartId,
                    identifikator = person.foedselsnr,
                    name = person.settSammenNavn(),
                    type = BehandlingDetaljerView.IdType.FNR,
                    available = person.doed == null,
                    language = krrInfo?.spraak,
                    statusList = getStatusList(person, krrInfo),
                    address = regoppslagService.getAddressForPersonOnBehalfOf(fnr = person.foedselsnr),
                    utsendingskanal = utsendingskanal
                )
            } else {
                val organisasjon = eregClient.hentNoekkelInformasjonOmOrganisasjon(identifier)
                BehandlingDetaljerView.PartViewWithUtsendingskanal(
                    id = technicalPartId,
                    identifikator = identifier,
                    name = organisasjon.navn.sammensattnavn,
                    type = BehandlingDetaljerView.IdType.ORGNR,
                    available = organisasjon.isActive(),
                    language = null,
                    statusList = getStatusList(organisasjon),
                    address = getAddress(organisasjon),
                    utsendingskanal = utsendingskanal,
                )
            }
        } else {
            BehandlingDetaljerView.PartViewWithUtsendingskanal(
                id = technicalPartId,
                identifikator = null,
                name = navn!!,
                type = null,
                available = true,
                language = null,
                statusList = listOf(),
                address = BehandlingDetaljerView.Address(
                    adresselinje1 = address!!.adresselinje1,
                    adresselinje2 = address.adresselinje2,
                    adresselinje3 = address.adresselinje3,
                    landkode = address.landkode,
                    postnummer = address.postnummer,
                    poststed = address.poststed,
                ),
                utsendingskanal = utsendingskanal,
            )
        }
    }

    fun getAddress(organisasjon: NoekkelInfoOmOrganisasjon): BehandlingDetaljerView.Address? {
        if (organisasjon.adresse == null) return null

        val poststed = if (organisasjon.adresse.landkode == "NO") {
            organisasjon.adresse.postnummer?.let { kodeverkService.getPoststed(it) }
        } else null

        return BehandlingDetaljerView.Address(
            adresselinje1 = organisasjon.adresse.adresselinje1,
            adresselinje2 = organisasjon.adresse.adresselinje2,
            adresselinje3 = organisasjon.adresse.adresselinje3,
            landkode = organisasjon.adresse.landkode,
            postnummer = organisasjon.adresse.postnummer,
            poststed = poststed,
        )
    }

    private fun SakenGjelder.harBeskyttelsesbehovFortrolig(): Boolean {
        return if (erVirksomhet()) {
            false
        } else {
            personService.getPersonInfo(partId.value).harBeskyttelsesbehovFortrolig()
        }
    }

    private fun SakenGjelder.harBeskyttelsesbehovStrengtFortrolig(): Boolean {
        return if (erVirksomhet()) {
            false
        } else {
            personService.getPersonInfo(partId.value).harBeskyttelsesbehovStrengtFortrolig()
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
            personService.getPersonInfo(partId.value).vergemaalEllerFremtidsfullmakt
        }
    }

    private fun SakenGjelder.getDead(): LocalDate? {
        return if (erVirksomhet()) {
            null
        } else {
            personService.getPersonInfo(partId.value).doed
        }
    }

    private fun SakenGjelder.sikkerhetstiltak(): BehandlingDetaljerView.Sikkerhetstiltak? {
        return if (erVirksomhet()) {
            null
        } else {
            val sikkerhetstiltak = personService.getPersonInfo(partId.value).sikkerhetstiltak
            if (sikkerhetstiltak != null && sikkerhetstiltak.gyldigFraOgMed <= LocalDate.now() && sikkerhetstiltak.gyldigTilOgMed >= LocalDate.now()) {
                BehandlingDetaljerView.Sikkerhetstiltak(
                    tiltakstype = BehandlingDetaljerView.Sikkerhetstiltak.Tiltakstype.valueOf(sikkerhetstiltak.tiltakstype.name),
                    beskrivelse = sikkerhetstiltak.beskrivelse,
                    gyldigFraOgMed = sikkerhetstiltak.gyldigFraOgMed,
                    gyldigTilOgMed = sikkerhetstiltak.gyldigTilOgMed,
                )
            } else null
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
            isAvsluttetAvSaksbehandler = behandling.ferdigstilling != null
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

        if (organisasjon.isDeltAnsvar()) {
            statusList.add(
                BehandlingDetaljerView.PartStatus(
                    status = BehandlingDetaljerView.PartStatus.Status.DELT_ANSVAR,
                    date = null,
                )
            )
        }
        return statusList
    }

    fun mapBehandlingToOppgaveView(behandling: Behandling): OppgaveView {
        return OppgaveView(
            id = behandling.id.toString(),
            typeId = behandling.type.id,
            ytelseId = behandling.ytelse.id,
            hjemmelIdList = behandling.hjemler.map { it.id },
            registreringshjemmelIdList = behandling.registreringshjemler.map { it.id },
            frist = behandling.frist,
            mottatt = behandling.mottattKlageinstans.toLocalDate(),
            medunderskriver = behandling.toMedunderskriverView(),
            rol = behandling.toROLView(),
            utfallId = behandling.utfall?.id,
            avsluttetAvSaksbehandlerDate = behandling.ferdigstilling?.avsluttetAvSaksbehandler?.toLocalDate(),
            isAvsluttetAvSaksbehandler = behandling.ferdigstilling != null,
            tildeltSaksbehandlerident = behandling.tildeling?.saksbehandlerident,
            ageKA = behandling.toAgeInDays(),
            sattPaaVent = behandling.toSattPaaVent(),
            feilregistrert = behandling.feilregistrering?.registered,
            fagsystemId = behandling.fagsystem.id,
            saksnummer = behandling.fagsakId,
            previousSaksbehandler = behandling.toPreviousSaksbehandlerView(),
            datoSendtTilTR = if (behandling is AnkeITrygderettenbehandling) behandling.sendtTilTrygderetten.toLocalDate() else null,
            varsletFrist = if (behandling is BehandlingWithVarsletBehandlingstid) {
                behandling.varsletBehandlingstid?.varsletFrist
            } else null,
            timesPreviouslyExtended = behandling.getTimesPreviouslyExtended(),
        )
    }

    private fun Behandling.toSattPaaVent(): OppgaveView.SattPaaVent? {
        return if (sattPaaVent != null) {
            OppgaveView.SattPaaVent(
                from = sattPaaVent!!.from,
                to = sattPaaVent!!.to,
                isExpired = sattPaaVent!!.to.isBefore(LocalDate.now()),
                reason = sattPaaVent!!.reason,
                reasonId = sattPaaVent!!.reasonId,
            )
        } else null
    }
}

fun toEnhetView(enhet: Enhet): EnhetView = EnhetView(
    enhetsnr = enhet.enhetsnr,
    navn = enhet.navn,
)