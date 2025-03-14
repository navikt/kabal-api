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
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.util.getPartIdFromIdentifikator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
    private val dokumentUnderArbeidService: DokumentUnderArbeidService,
    private val dokumentMapper: DokumentMapper,
) {

    fun getAnkemuligheter(partIdValue: String): List<Mulighet> {
        behandlingService.checkLesetilgangForPerson(partIdValue)
        return behandlingService.getAnkemuligheterByPartIdValue(partIdValue = partIdValue)
                .map { it.toMulighet(mulighetType = Type.ANKE) }
    }

    fun getOmgjoeringskravmuligheter(partIdValue: String): List<Mulighet> {
        behandlingService.checkLesetilgangForPerson(partIdValue)
        return behandlingService.getOmgjoeringskravmuligheterByPartIdValue(partIdValue = partIdValue)
            .map { it.toMulighet(mulighetType = Type.OMGJOERINGSKRAV) }
    }

    fun createBehandling(input: CreateBehandlingBasedOnKabinInput): CreatedBehandlingResponse {
        val behandling = mottakService.createMottakAndBehandlingFromKabinInput(input = input)

        if (input.gosysOppgaveId != null) {
            behandlingService.setGosysOppgaveIdFromKabin(
                behandlingId = behandling.id,
                gosysOppgaveId = input.gosysOppgaveId,
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

        if (input.gosysOppgaveId != null) {
            behandlingService.setGosysOppgaveIdFromKabin(
                behandlingId = behandling.id,
                gosysOppgaveId = input.gosysOppgaveId,
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

            behandlingService.setOpprinneligVarsletFrist(
                behandlingstidUnitType = getTimeUnitType(
                    varsletBehandlingstidUnitTypeId = svarbrevInput.varsletBehandlingstidUnitTypeId,
                    varsletBehandlingstidUnitType = svarbrevInput.varsletBehandlingstidUnitType
                ),
                behandlingstidUnits = svarbrevInput.varsletBehandlingstidUnits,
                behandling = behandling,
                systemUserContext = false,
                mottakere = svarbrevInput.receivers.map {
                    if (it.id != null) {
                        MottakerPartId(
                            value = getPartIdFromIdentifikator(it.id)
                        )
                    } else if (it.navn != null) {
                        MottakerNavn(
                            value = it.navn
                        )
                    } else throw IllegalArgumentException("Missing values in receiver: $it")
                }
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
                    navn = receiver.navn,
                )
            },
            fullmektigFritekst = fullmektigFritekst,
            varsletBehandlingstidUnits = varsletBehandlingstidUnits,
            varsletBehandlingstidUnitType = getTimeUnitType(
                varsletBehandlingstidUnitTypeId = varsletBehandlingstidUnitTypeId,
                varsletBehandlingstidUnitType = varsletBehandlingstidUnitType
            ),
            type = behandling.type,
            initialCustomText = initialCustomText,
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

    fun createKlage(
        input: CreateKlageBasedOnKabinInput
    ): CreatedBehandlingResponse {
        val behandling = mottakService.createKlageMottakFromKabinInput(klageInput = input)

        if (input.gosysOppgaveId != null) {
            behandlingService.setGosysOppgaveIdFromKabin(
                behandlingId = behandling.id,
                gosysOppgaveId = input.gosysOppgaveId,
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

    fun getCreatedBehandlingStatus(
        behandlingId: UUID
    ): CreatedBehandlingStatusForKabin {
        val behandling =
            behandlingService.getBehandlingForReadWithoutCheckForAccess(behandlingId = behandlingId)

        return when (behandling) {
            is Klagebehandling -> getCreatedKlagebehandlingStatusForKabin(
                klagebehandling = behandling
            )

            is Ankebehandling -> getCreatedAnkebehandlingStatusForKabin(
                ankebehandling = behandling
            )

            is Omgjoeringskravbehandling -> getCreatedOmgjoeringskravbehandlingStatusForKabin(
                omgjoeringskravbehandling = behandling
            )

            else -> error("Unsupported type")
        }
    }

    private fun getCreatedOmgjoeringskravbehandlingStatusForKabin(omgjoeringskravbehandling: Omgjoeringskravbehandling): CreatedBehandlingStatusForKabin {
        val mottak = mottakService.getMottak(omgjoeringskravbehandling.mottakId)

        val dokumentUnderArbeid =
            dokumentUnderArbeidService.getSvarbrevAsOpplastetDokumentUnderArbeidAsHoveddokument(behandlingId = omgjoeringskravbehandling.id)

        val dokumentView: DokumentView? = if (dokumentUnderArbeid != null) {
            dokumentMapper.mapToDokumentView(
                dokumentUnderArbeid = dokumentUnderArbeid,
                behandling = omgjoeringskravbehandling,
                journalpost = null,
                smartEditorDocument = null,
            )
        } else null

        return CreatedBehandlingStatusForKabin(
            typeId = Type.OMGJOERINGSKRAV.id,
            ytelseId = omgjoeringskravbehandling.ytelse.id,
            sakenGjelder = behandlingMapper.getSakenGjelderViewWithUtsendingskanal(behandling = omgjoeringskravbehandling)
                .toKabinPartView(),
            klager = behandlingMapper.getPartViewWithUtsendingskanal(
                partId = omgjoeringskravbehandling.klager.partId,
                behandling = omgjoeringskravbehandling,
                navn = null,
                address = null
            ).toKabinPartView(),
            fullmektig = omgjoeringskravbehandling.prosessfullmektig?.let {
                behandlingMapper.getPartViewWithUtsendingskanal(
                    partId = it.partId,
                    behandling = omgjoeringskravbehandling,
                    navn = it.navn,
                    address = it.address
                )
                    .toKabinPartView()
            },
            mottattKlageinstans = omgjoeringskravbehandling.mottattKlageinstans.toLocalDate(),
            mottattVedtaksinstans = null,
            frist = omgjoeringskravbehandling.frist!!,
            varsletFrist = omgjoeringskravbehandling.varsletBehandlingstid?.varsletFrist,
            varsletFristUnits = omgjoeringskravbehandling.varsletBehandlingstid?.varsletBehandlingstidUnits,
            varsletFristUnitTypeId = omgjoeringskravbehandling.varsletBehandlingstid?.varsletBehandlingstidUnitType?.id,
            fagsakId = omgjoeringskravbehandling.fagsakId,
            fagsystemId = omgjoeringskravbehandling.fagsystem.id,
            journalpost = dokumentService.getDokumentReferanse(
                journalpostId = mottak.mottakDokument.find { it.type == MottakDokumentType.BRUKERS_OMGJOERINGSKRAV }!!.journalpostId,
                behandling = omgjoeringskravbehandling
            ),
            tildeltSaksbehandler = omgjoeringskravbehandling.tildeling?.saksbehandlerident?.let {
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

    private fun getCreatedAnkebehandlingStatusForKabin(
        ankebehandling: Ankebehandling,
    ): CreatedBehandlingStatusForKabin {
        val mottak = mottakService.getMottak(ankebehandling.mottakId!!)

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

        return CreatedBehandlingStatusForKabin(
            typeId = Type.ANKE.id,
            ytelseId = ankebehandling.ytelse.id,
            sakenGjelder = behandlingMapper.getSakenGjelderViewWithUtsendingskanal(behandling = ankebehandling)
                .toKabinPartView(),
            klager = behandlingMapper.getPartViewWithUtsendingskanal(
                partId = ankebehandling.klager.partId,
                behandling = ankebehandling,
                navn = null,
                address = null,
            ).toKabinPartView(),
            fullmektig = ankebehandling.prosessfullmektig?.let {
                behandlingMapper.getPartViewWithUtsendingskanal(
                    partId = it.partId,
                    behandling = ankebehandling,
                    navn = it.navn,
                    address = it.address
                )
                    .toKabinPartView()
            },
            mottattKlageinstans = ankebehandling.mottattKlageinstans.toLocalDate(),
            mottattVedtaksinstans = null,
            frist = ankebehandling.frist!!,
            varsletFrist = ankebehandling.varsletBehandlingstid?.varsletFrist,
            varsletFristUnits = ankebehandling.varsletBehandlingstid?.varsletBehandlingstidUnits,
            varsletFristUnitTypeId = ankebehandling.varsletBehandlingstid?.varsletBehandlingstidUnitType?.id,
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
    ): CreatedBehandlingStatusForKabin {
        val mottak = mottakService.getMottak(mottakId = klagebehandling.mottakId)

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

        return CreatedBehandlingStatusForKabin(
            typeId = Type.KLAGE.id,
            ytelseId = klagebehandling.ytelse.id,
            sakenGjelder = behandlingMapper.getSakenGjelderViewWithUtsendingskanal(behandling = klagebehandling)
                .toKabinPartView(),
            klager = behandlingMapper.getPartViewWithUtsendingskanal(
                partId = klagebehandling.klager.partId,
                behandling = klagebehandling,
                navn = null,
                address = null,
            ).toKabinPartView(),
            fullmektig = klagebehandling.prosessfullmektig?.let {
                behandlingMapper.getPartViewWithUtsendingskanal(
                    partId = it.partId,
                    behandling = klagebehandling,
                    navn = it.navn,
                    address = it.address,
                )
                    .toKabinPartView()
            },
            mottattVedtaksinstans = klagebehandling.mottattVedtaksinstans,
            mottattKlageinstans = klagebehandling.mottattKlageinstans.toLocalDate(),
            frist = klagebehandling.frist!!,
            varsletFrist = klagebehandling.varsletBehandlingstid?.varsletFrist,
            varsletFristUnits = klagebehandling.varsletBehandlingstid?.varsletBehandlingstidUnits,
            varsletFristUnitTypeId = klagebehandling.varsletBehandlingstid?.varsletBehandlingstidUnitType?.id,
            fagsakId = klagebehandling.fagsakId,
            fagsystemId = klagebehandling.fagsystem.id,
            journalpost = dokumentService.getDokumentReferanse(
                journalpostId = mottak.mottakDokument.find { it.type == MottakDokumentType.BRUKERS_KLAGE }!!.journalpostId,
                behandling = klagebehandling
            ),
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

    private fun Behandling.toMulighet(mulighetType: Type): Mulighet {
        val ankebehandlingerBasedOnThisBehandling =
            ankebehandlingService.getAnkebehandlingerBasedOnSourceBehandlingId(sourceBehandlingId = id)

        return Mulighet(
            behandlingId = id,
            ytelseId = ytelse.id,
            hjemmelIdList = hjemler.map { it.id },
            vedtakDate = ferdigstilling!!.avsluttetAvSaksbehandler,
            sakenGjelder = behandlingMapper.getSakenGjelderViewWithUtsendingskanal(behandling = this).toKabinPartView(),
            klager = behandlingMapper.getPartViewWithUtsendingskanal(
                partId = klager.partId,
                behandling = this,
                navn = null,
                address = null,
            )
                .toKabinPartView(),
            fullmektig = prosessfullmektig?.let {
                behandlingMapper.getPartViewWithUtsendingskanal(
                    partId = it.partId,
                    behandling = this,
                    navn = it.navn,
                    address = it.address,
                ).toKabinPartView()
            },
            fagsakId = fagsakId,
            fagsystem = fagsystem,
            fagsystemId = fagsystem.id,
            klageBehandlendeEnhet = tildeling!!.enhet!!,
            tildeltSaksbehandlerIdent = tildeling!!.saksbehandlerident!!,
            tildeltSaksbehandlerNavn = saksbehandlerService.getNameForIdentDefaultIfNull(tildeling!!.saksbehandlerident!!),
            originalTypeId = type.id,
            typeId = mulighetType.id,
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