package no.nav.klage.oppgave.service

import no.nav.klage.dokument.api.mapper.DokumentMapper
import no.nav.klage.dokument.api.view.DokumentView
import no.nav.klage.dokument.domain.dokumenterunderarbeid.Svarbrev
import no.nav.klage.dokument.service.DokumentUnderArbeidService
import no.nav.klage.kodeverk.Enhet
import no.nav.klage.kodeverk.TimeUnitType
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.hjemmel.ytelseToHjemler
import no.nav.klage.oppgave.api.mapper.BehandlingMapper
import no.nav.klage.oppgave.api.view.kabin.*
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.BehandlingWithMottakDokument
import no.nav.klage.oppgave.domain.behandling.BehandlingWithVarsletBehandlingstid
import no.nav.klage.oppgave.domain.behandling.embedded.MottakerNavn
import no.nav.klage.oppgave.domain.behandling.embedded.MottakerPartId
import no.nav.klage.oppgave.domain.behandling.embedded.VarsletBehandlingstid
import no.nav.klage.oppgave.domain.behandling.subentities.getMottakDokumentType
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
    private val gosysOppgaveService: GosysOppgaveService,
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

    fun getGjenopptaksmuligheter(partIdValue: String): List<Mulighet> {
        behandlingService.checkLesetilgangForPerson(partIdValue)
        return behandlingService.getGjenopptaksmuligheterByPartIdValue(partIdValue = partIdValue)
            .map { it.toMulighet(mulighetType = Type.BEGJAERING_OM_GJENOPPTAK) }
    }

    fun createBehandlingFromPreviousKabalBehandling(input: CreateBehandlingBasedOnKabinInputWithPreviousKabalBehandling): CreatedBehandlingResponse {
        val behandling = mottakService.createMottakAndBehandlingFromKabinInputWithPreviousKabalBehandling(input = input)

        setSaksbehandlerAndCreateSvarbrev(
            behandling = behandling,
            saksbehandlerIdent = input.saksbehandlerIdent,
            svarbrevInput = input.svarbrevInput,
        )

        return CreatedBehandlingResponse(behandlingId = behandling.id)
    }

    fun createAnkeFromCompleteKabinInput(input: CreateAnkeBasedOnCompleteKabinInput): CreatedBehandlingResponse {
        val behandling = mottakService.createAnkeMottakFromCompleteKabinInput(input = input)

        setSaksbehandlerAndCreateSvarbrev(
            behandling = behandling,
            saksbehandlerIdent = input.saksbehandlerIdent,
            svarbrevInput = input.svarbrevInput,
        )

        return CreatedBehandlingResponse(behandlingId = behandling.id)
    }

    fun createBehandlingBasedOnJournalpost(input: CreateBehandlingBasedOnJournalpostInput): CreatedBehandlingResponse {
        val behandling = mottakService.createBehandlingBasedOnJournalpost(input = input)

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
        svarbrevInput: SvarbrevInput,
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
        if (!svarbrevInput.doNotSendLetter) {
            dokumentUnderArbeidService.createAndFinalizeDokumentUnderArbeidFromSvarbrev(
                svarbrev = svarbrevInput.toSvarbrev(behandling = behandling),
                behandling = behandling,
                //Hardkodes til KA Oslo
                avsenderEnhetId = Enhet.E4291.navn,
            )
        }

        //TODO: remove check after client adjusts.
        if (!svarbrevInput.doNotSendLetter || svarbrevInput.reasonNoLetter != null) {
            behandlingService.setVarsletFrist(
                varsletBehandlingstidUnitType = getTimeUnitType(
                    varsletBehandlingstidUnitTypeId = svarbrevInput.varsletBehandlingstidUnitTypeId,
                    varsletBehandlingstidUnitType = svarbrevInput.varsletBehandlingstidUnitType
                ),
                varsletBehandlingstidUnits = svarbrevInput.varsletBehandlingstidUnits,
                behandlingId = behandling.id,
                systemUserContext = false,
                mottakere = svarbrevInput.receivers.map {
                    if (it.identifikator != null) {
                        MottakerPartId(
                            value = getPartIdFromIdentifikator(it.identifikator)
                        )
                    } else if (it.navn != null) {
                        MottakerNavn(
                            value = it.navn
                        )
                    } else throw IllegalArgumentException("Missing values in receiver: $it")
                },
                fromDate = behandling.mottattKlageinstans.toLocalDate(),
                varselType = VarsletBehandlingstid.VarselType.OPPRINNELIG,
                varsletFrist = null,
                doNotSendLetter = svarbrevInput.doNotSendLetter,
                reasonNoLetter = svarbrevInput.reasonNoLetter,
            )
            if (behandling.gosysOppgaveId != null) {
                gosysOppgaveService.updateInternalFristInGosysOppgave(
                    behandling = behandling,
                    systemContext = false,
                    throwExceptionIfFerdigstilt = false,
                )
            }
        }
    }

    private fun SvarbrevInput.toSvarbrev(behandling: Behandling): Svarbrev {
        return Svarbrev(
            title = title,
            receivers = receivers.map { receiver ->
                Svarbrev.Receiver(
                    identifikator = receiver.identifikator,
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

        return getCreatedBehandlingStatusForKabin(behandling = behandling)
    }

    private fun getCreatedBehandlingStatusForKabin(
        behandling: Behandling,
    ): CreatedBehandlingStatusForKabin {
        if (behandling !is BehandlingWithMottakDokument || behandling !is BehandlingWithVarsletBehandlingstid) {
            error("Unsupported type")
        }
        val dokumentUnderArbeid =
            dokumentUnderArbeidService.getSvarbrevAsOpplastetDokumentUnderArbeidAsHoveddokument(behandlingId = behandling.id)

        val dokumentView: DokumentView? = if (dokumentUnderArbeid != null) {
            dokumentMapper.mapToDokumentView(
                dokumentUnderArbeid = dokumentUnderArbeid,
                behandling = behandling,
                journalpost = null,
                smartEditorDocument = null,
            )
        } else null

        return CreatedBehandlingStatusForKabin(
            typeId = behandling.type.id,
            ytelseId = behandling.ytelse.id,
            sakenGjelder = behandlingMapper.getSakenGjelderViewWithUtsendingskanal(behandling = behandling)
                .toKabinPartView(),
            klager = behandlingMapper.getPartViewWithUtsendingskanal(
                technicalPartId = behandling.klager.id,
                partId = behandling.klager.partId,
                behandling = behandling,
                navn = null,
                address = null,
            ).toKabinPartView(),
            fullmektig = if (behandling.prosessfullmektig?.partId != null) {
                val prosessfullmektig = behandling.prosessfullmektig!!
                behandlingMapper.getPartViewWithUtsendingskanal(
                    technicalPartId = prosessfullmektig.id,
                    partId = prosessfullmektig.partId,
                    behandling = behandling,
                    navn = prosessfullmektig.navn,
                    address = prosessfullmektig.address
                ).toKabinPartView()
            } else null,
            mottattKlageinstans = behandling.mottattKlageinstans.toLocalDate(),
            mottattVedtaksinstans = null,
            frist = behandling.frist!!,
            varsletFrist = behandling.varsletBehandlingstid?.varsletFrist,
            varsletFristUnits = behandling.varsletBehandlingstid?.varsletBehandlingstidUnits,
            varsletFristUnitTypeId = behandling.varsletBehandlingstid?.varsletBehandlingstidUnitType?.id,
            fagsakId = behandling.fagsakId,
            fagsystemId = behandling.fagsystem.id,
            journalpost = dokumentService.getDokumentReferanse(
                journalpostId = behandling.mottakDokument.find { it.type == behandling.type.getMottakDokumentType() }!!.journalpostId,
                behandling = behandling
            ),
            tildeltSaksbehandler = behandling.tildeling?.saksbehandlerident?.let {
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

        //Exclude hjemler where utfases = true
        val relevantHjemler = ytelseToHjemler[ytelse]!!.filter { !it.utfases }.map { it.hjemmel }
        val filteredHjemmelList = hjemler.filter { it in relevantHjemler }

        return Mulighet(
            behandlingId = id,
            ytelseId = ytelse.id,
            hjemmelIdList = filteredHjemmelList.map { it.id },
            vedtakDate = ferdigstilling!!.avsluttetAvSaksbehandler,
            sakenGjelder = behandlingMapper.getSakenGjelderViewWithUtsendingskanal(behandling = this).toKabinPartView(),
            klager = behandlingMapper.getPartViewWithUtsendingskanal(
                technicalPartId = klager.id,
                partId = klager.partId,
                behandling = this,
                navn = null,
                address = null,
            )
                .toKabinPartView(),
            fullmektig = if (prosessfullmektig?.partId != null) {
                behandlingMapper.getPartViewWithUtsendingskanal(
                    technicalPartId = prosessfullmektig!!.id,
                    partId = prosessfullmektig!!.partId,
                    behandling = this,
                    navn = prosessfullmektig!!.navn,
                    address = prosessfullmektig!!.address,
                ).toKabinPartView()
            } else null,
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
            gosysOppgaveRequired = gosysOppgaveRequired,
        )
    }
}