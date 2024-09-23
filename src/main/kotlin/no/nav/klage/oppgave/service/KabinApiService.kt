package no.nav.klage.oppgave.service

import no.nav.klage.dokument.api.mapper.DokumentMapper
import no.nav.klage.dokument.api.view.DokumentView
import no.nav.klage.dokument.domain.dokumenterunderarbeid.Svarbrev
import no.nav.klage.dokument.service.DokumentUnderArbeidService
import no.nav.klage.kodeverk.Enhet
import no.nav.klage.kodeverk.TimeUnitType
import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.api.mapper.BehandlingMapper
import no.nav.klage.oppgave.api.view.kabin.*
import no.nav.klage.oppgave.clients.klagefssproxy.KlageFssProxyClient
import no.nav.klage.oppgave.clients.klagefssproxy.domain.GetSakAppAccessInput
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.util.getPartIdFromIdentifikator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
@Transactional
class KabinApiService(
    private val behandlingMapper: BehandlingMapper,
    private val dokumentService: DokumentService,
    private val saksbehandlerService: SaksbehandlerService,
    private val mottakService: MottakService,
    private val ankebehandlingService: AnkebehandlingService,
    private val behandlingService: BehandlingService,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val klageFssProxyClient: KlageFssProxyClient,
    private val dokumentUnderArbeidService: DokumentUnderArbeidService,
    private val dokumentMapper: DokumentMapper,
) {

    fun getAnkemuligheterExcludingInfotrygd(partIdValue: String): List<Ankemulighet> {
        behandlingService.checkLesetilgangForPerson(partIdValue)
        return behandlingService.getAnkemuligheterByPartIdValue(partIdValue = partIdValue, includeFromInfotrygd = false)
                .map { it.toAnkemulighet() }
    }

    fun getOmgjoeringskravmuligheter(partIdValue: String): List<Ankemulighet> {
        behandlingService.checkLesetilgangForPerson(partIdValue)
        return behandlingService.getAnkemuligheterByPartIdValue(partIdValue = partIdValue, includeFromInfotrygd = true)
            .map { it.toAnkemulighet() }
    }

    fun createAnke(input: CreateAnkeBasedOnKabinInput): CreatedBehandlingResponse {
        val behandling = mottakService.createAnkeMottakAndBehandlingFromKabinInput(input = input)

        if (input.oppgaveId != null) {
            behandlingService.setOppgaveId(
                behandlingId = behandling.id,
                oppgaveId = input.oppgaveId,
                utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            )
        }

        setSaksbehandlerAndCreateSvarbrev(
            behandling = behandling,
            saksbehandlerIdent = input.saksbehandlerIdent,
            svarbrevInput = input.svarbrevInput,
        )

        return CreatedBehandlingResponse(behandlingId = behandling.id)
    }

    fun createBehandling(input: CreateBehandlingBasedOnKabinInput): CreatedBehandlingResponse {
        val behandling = mottakService.createMottakAndBehandlingFromKabinInput(input = input)

        if (input.oppgaveId != null) {
            behandlingService.setOppgaveId(
                behandlingId = behandling.id,
                oppgaveId = input.oppgaveId,
                utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            )
        }

        setSaksbehandlerAndCreateSvarbrev(
            behandling = behandling,
            saksbehandlerIdent = input.saksbehandlerIdent,
            svarbrevInput = input.svarbrevInput,
        )

        return CreatedBehandlingResponse(behandlingId = behandling.id)
    }

    fun createAnkeFromCompleteKabinInput(input: CreateAnkeBasedOnCompleteKabinInput): CreatedBehandlingResponse {
        val behandling = mottakService.createAnkeMottakFromCompleteKabinInput(input = input)

        if (input.oppgaveId != null) {
            behandlingService.setOppgaveId(
                behandlingId = behandling.id,
                oppgaveId = input.oppgaveId,
                utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            )
        }

        setSaksbehandlerAndCreateSvarbrev(
            behandling = behandling,
            saksbehandlerIdent = input.saksbehandlerIdent,
            svarbrevInput = input.svarbrevInput,
        )

        return CreatedBehandlingResponse(behandlingId = behandling.id)
    }

    private fun setSaksbehandlerAndCreateSvarbrev(
        behandling: Behandling,
        saksbehandlerIdent: String?,
        svarbrevInput: SvarbrevInput?,
    ) {
        if (saksbehandlerIdent != null) {
            behandlingService.setSaksbehandler(
                behandlingId = behandling.id,
                tildeltSaksbehandlerIdent = saksbehandlerIdent,
                enhetId = saksbehandlerService.getEnhetForSaksbehandler(
                    saksbehandlerIdent
                ).enhetId,
                fradelingReason = null,
                utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            )
        }

        //Create DokumentUnderArbeid from input based on svarbrevInput.
        if (svarbrevInput != null) {
            dokumentUnderArbeidService.createAndFinalizeDokumentUnderArbeidFromSvarbrev(
                svarbrev = svarbrevInput.toSvarbrev(behandling = behandling),
                behandling = behandling,
                //Hardkodes til KA Oslo
                avsenderEnhetId = Enhet.E4291.navn,
                systemContext = false,
            )

            behandlingService.setVarsletFrist(
                behandlingstidUnitType = getTimeUnitType(
                    varsletBehandlingstidUnitTypeId = svarbrevInput.varsletBehandlingstidUnitTypeId,
                    varsletBehandlingstidUnitType = svarbrevInput.varsletBehandlingstidUnitType
                ),
                behandlingstidUnits = svarbrevInput.varsletBehandlingstidUnits,
                behandling = behandling,
                systemUserContext = false,
                mottakere = svarbrevInput.receivers.map { getPartIdFromIdentifikator(it.id) }
            )
        }
    }

    private fun SvarbrevInput.toSvarbrev(behandling: Behandling): Svarbrev {
        return Svarbrev(
            title = title,
            receivers = receivers.map { receiver ->
                Svarbrev.Receiver(
                    id = receiver.id,
                    overriddenAddress = receiver.overriddenAddress?.let {
                        Svarbrev.Receiver.AddressInput(
                            adresselinje1 = it.adresselinje1,
                            adresselinje2 = it.adresselinje2,
                            adresselinje3 = it.adresselinje3,
                            landkode = it.landkode,
                            postnummer = it.postnummer,
                        )
                    },
                    handling = Svarbrev.Receiver.HandlingEnum.valueOf(receiver.handling.name),
                )
            },
            fullmektigFritekst = fullmektigFritekst,
            varsletBehandlingstidUnits = varsletBehandlingstidUnits,
            varsletBehandlingstidUnitType = getTimeUnitType(
                varsletBehandlingstidUnitTypeId = varsletBehandlingstidUnitTypeId,
                varsletBehandlingstidUnitType = varsletBehandlingstidUnitType
            ),
            type = behandling.type,
            customText = customText,
        )
    }

    private fun getTimeUnitType(
        varsletBehandlingstidUnitTypeId: String?,
        varsletBehandlingstidUnitType: TimeUnitType?
    ): TimeUnitType {
        return if (varsletBehandlingstidUnitTypeId != null) {
            TimeUnitType.of(varsletBehandlingstidUnitTypeId)
        } else {
            varsletBehandlingstidUnitType!!
        }
    }

    fun getCreatedAnkebehandlingStatus(
        behandlingId: UUID
    ): CreatedAnkebehandlingStatusForKabin {
        val ankebehandling =
            behandlingService.getBehandlingForReadWithoutCheckForAccess(behandlingId = behandlingId) as Ankebehandling
        val mottak = mottakService.getMottak(mottakId = ankebehandling.mottakId!!)

        return if (ankebehandling.sourceBehandlingId != null) {
            val sourceBehandling =
                behandlingService.getBehandlingAndCheckLeseTilgangForPerson(ankebehandling.sourceBehandlingId!!)
            getCreatedAnkebehandlingStatusForKabin(
                ankebehandling = ankebehandling,
                mottak = mottak,
                vedtakDate = sourceBehandling.ferdigstilling!!.avsluttetAvSaksbehandler,
            )
        } else {
            val klageInInfotrygd = klageFssProxyClient.getSakWithAppAccess(
                sakId = ankebehandling.kildeReferanse,
                input = GetSakAppAccessInput(saksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent())
            )
            getCreatedAnkebehandlingStatusForKabin(
                ankebehandling = ankebehandling,
                mottak = mottak,
                vedtakDate = klageInInfotrygd.vedtaksdato.atStartOfDay(),
            )
        }
    }

    fun createKlage(
        input: CreateKlageBasedOnKabinInput
    ): CreatedBehandlingResponse {
        val behandling = mottakService.createKlageMottakFromKabinInput(klageInput = input)

        if (input.oppgaveId != null) {
            behandlingService.setOppgaveId(
                behandlingId = behandling.id,
                oppgaveId = input.oppgaveId,
                utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            )
        }

        setSaksbehandlerAndCreateSvarbrev(
            behandling = behandling,
            saksbehandlerIdent = input.saksbehandlerIdent,
            svarbrevInput = input.svarbrevInput,
        )

        return CreatedBehandlingResponse(behandlingId = behandling.id)
    }

    fun getCreatedKlagebehandlingStatus(
        behandlingId: UUID
    ): CreatedKlagebehandlingStatusForKabin {
        val klagebehandling =
            behandlingService.getBehandlingForReadWithoutCheckForAccess(behandlingId = behandlingId) as Klagebehandling
        val mottak = mottakService.getMottak(mottakId = klagebehandling.mottakId)

        return getCreatedKlagebehandlingStatusForKabin(
            klagebehandling = klagebehandling,
            mottak = mottak
        )
    }

    private fun getCreatedAnkebehandlingStatusForKabin(
        ankebehandling: Ankebehandling,
        mottak: Mottak,
        vedtakDate: LocalDateTime,
    ): CreatedAnkebehandlingStatusForKabin {
        val dokumentUnderArbeid =
            dokumentUnderArbeidService.getSvarbrevAsOpplastetDokumentUnderArbeidAsHoveddokument(behandlingId = ankebehandling.id)

        val dokumentView: DokumentView? = if (dokumentUnderArbeid != null) {
            dokumentMapper.mapToDokumentView(
                dokumentUnderArbeid = dokumentUnderArbeid,
                behandling = ankebehandling,
                journalpost = null,
                smartEditorDocument = null,
            )
        } else null

        return CreatedAnkebehandlingStatusForKabin(
            typeId = Type.ANKE.id,
            ytelseId = ankebehandling.ytelse.id,
            vedtakDate = vedtakDate,
            sakenGjelder = behandlingMapper.getSakenGjelderViewWithUtsendingskanal(behandling = ankebehandling)
                .toKabinPartView(),
            klager = behandlingMapper.getPartViewWithUtsendingskanal(
                partId = ankebehandling.klager.partId,
                behandling = ankebehandling
            ).toKabinPartView(),
            fullmektig = ankebehandling.klager.prosessfullmektig?.let {
                behandlingMapper.getPartViewWithUtsendingskanal(partId = it.partId, behandling = ankebehandling)
                    .toKabinPartView()
            },
            mottattNav = ankebehandling.mottattKlageinstans.toLocalDate(),
            frist = ankebehandling.frist!!,
            varsletFrist = ankebehandling.varsletFrist,
            varsletFristUnits = ankebehandling.varsletBehandlingstidUnits,
            varsletFristUnitTypeId = ankebehandling.varsletBehandlingstidUnitType?.id,
            fagsakId = ankebehandling.fagsakId,
            fagsystemId = ankebehandling.fagsystem.id,
            journalpost = dokumentService.getDokumentReferanse(
                journalpostId = mottak.mottakDokument.find { it.type == MottakDokumentType.BRUKERS_ANKE }!!.journalpostId,
                behandling = ankebehandling
            ),
            tildeltSaksbehandler = ankebehandling.tildeling?.saksbehandlerident?.let {
                TildeltSaksbehandler(
                    navIdent = it,
                    navn = saksbehandlerService.getNameForIdentDefaultIfNull(it),
                )
            },
            svarbrev = dokumentView?.let { document ->
                KabinResponseSvarbrev(
                    dokumentUnderArbeidId = document.id,
                    title = document.tittel,
                    receivers = document.mottakerList.map { mottaker ->
                        KabinResponseSvarbrev.Receiver(
                            part = mottaker.part,
                            overriddenAddress = mottaker.overriddenAddress,
                            handling = mottaker.handling,
                        )
                    }
                )
            }
        )
    }

    private fun getCreatedKlagebehandlingStatusForKabin(
        klagebehandling: Klagebehandling,
        mottak: Mottak,
    ): CreatedKlagebehandlingStatusForKabin {
        val dokumentUnderArbeid =
            dokumentUnderArbeidService.getSvarbrevAsOpplastetDokumentUnderArbeidAsHoveddokument(behandlingId = klagebehandling.id)

        val dokumentView: DokumentView? = if (dokumentUnderArbeid != null) {
            dokumentMapper.mapToDokumentView(
                dokumentUnderArbeid = dokumentUnderArbeid,
                behandling = klagebehandling,
                journalpost = null,
                smartEditorDocument = null,
            )
        } else null

        return CreatedKlagebehandlingStatusForKabin(
            typeId = Type.KLAGE.id,
            ytelseId = klagebehandling.ytelse.id,
            sakenGjelder = behandlingMapper.getSakenGjelderViewWithUtsendingskanal(behandling = klagebehandling)
                .toKabinPartView(),
            klager = behandlingMapper.getPartViewWithUtsendingskanal(
                partId = klagebehandling.klager.partId,
                behandling = klagebehandling
            ).toKabinPartView(),
            fullmektig = klagebehandling.klager.prosessfullmektig?.let {
                behandlingMapper.getPartViewWithUtsendingskanal(partId = it.partId, behandling = klagebehandling)
                    .toKabinPartView()
            },
            mottattVedtaksinstans = klagebehandling.mottattVedtaksinstans,
            mottattKlageinstans = klagebehandling.mottattKlageinstans.toLocalDate(),
            frist = klagebehandling.frist!!,
            varsletFrist = klagebehandling.varsletFrist,
            varsletFristUnits = klagebehandling.varsletBehandlingstidUnits,
            varsletFristUnitTypeId = klagebehandling.varsletBehandlingstidUnitType?.id,
            fagsakId = klagebehandling.fagsakId,
            fagsystemId = klagebehandling.fagsystem.id,
            journalpost = dokumentService.getDokumentReferanse(
                journalpostId = mottak.mottakDokument.find { it.type == MottakDokumentType.BRUKERS_KLAGE }!!.journalpostId,
                behandling = klagebehandling
            ),
            kildereferanse = mottak.kildeReferanse,
            tildeltSaksbehandler = klagebehandling.tildeling?.saksbehandlerident?.let {
                TildeltSaksbehandler(
                    navIdent = it,
                    navn = saksbehandlerService.getNameForIdentDefaultIfNull(it),
                )
            },
            svarbrev = dokumentView?.let { document ->
                KabinResponseSvarbrev(
                    dokumentUnderArbeidId = document.id,
                    title = document.tittel,
                    receivers = document.mottakerList.map { mottaker ->
                        KabinResponseSvarbrev.Receiver(
                            part = mottaker.part,
                            overriddenAddress = mottaker.overriddenAddress,
                            handling = mottaker.handling,
                        )
                    }
                )
            }
        )
    }

    private fun Behandling.toAnkemulighet(): Ankemulighet {
        val ankebehandlingerBasedOnThisBehandling =
            ankebehandlingService.getAnkebehandlingerBasedOnSourceBehandlingId(sourceBehandlingId = id)

        return Ankemulighet(
            behandlingId = id,
            ytelseId = ytelse.id,
            hjemmelIdList = hjemler.map { it.id },
            vedtakDate = ferdigstilling!!.avsluttetAvSaksbehandler,
            sakenGjelder = behandlingMapper.getSakenGjelderViewWithUtsendingskanal(behandling = this).toKabinPartView(),
            klager = behandlingMapper.getPartViewWithUtsendingskanal(partId = klager.partId, behandling = this)
                .toKabinPartView(),
            fullmektig = klager.prosessfullmektig?.let {
                behandlingMapper.getPartViewWithUtsendingskanal(
                    partId = it.partId,
                    behandling = this
                ).toKabinPartView()
            },
            fagsakId = fagsakId,
            fagsystem = fagsystem,
            fagsystemId = fagsystem.id,
            klageBehandlendeEnhet = tildeling!!.enhet!!,
            tildeltSaksbehandlerIdent = tildeling!!.saksbehandlerident!!,
            tildeltSaksbehandlerNavn = saksbehandlerService.getNameForIdentDefaultIfNull(tildeling!!.saksbehandlerident!!),
            typeId = type.id,
            sourceOfExistingAnkebehandling = ankebehandlingerBasedOnThisBehandling.map {
                ExistingAnkebehandling(
                    id = it.id,
                    created = it.created,
                    completed = it.ferdigstilling?.avsluttetAvSaksbehandler,
                )
            },
        )
    }
}