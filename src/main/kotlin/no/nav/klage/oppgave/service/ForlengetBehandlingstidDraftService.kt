package no.nav.klage.oppgave.service

import no.nav.klage.dokument.api.mapper.DokumentMapper
import no.nav.klage.dokument.api.view.MottakerInput
import no.nav.klage.dokument.service.DokumentUnderArbeidService
import no.nav.klage.dokument.service.KabalJsonToPdfService
import no.nav.klage.kodeverk.Enhet
import no.nav.klage.kodeverk.TimeUnitType
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.repositories.ForlengetBehandlingstidDraftRepository
import no.nav.klage.oppgave.util.findDateBasedOnTimeUnitTypeAndUnits
import no.nav.klage.oppgave.util.getPartIdFromIdentifikator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

@Service
@Transactional
class ForlengetBehandlingstidDraftService(
    private val behandlingService: BehandlingService,
    private val partSearchService: PartSearchService,
    private val kabalJsonToPdfService: KabalJsonToPdfService,
    private val dokumentUnderArbeidService: DokumentUnderArbeidService,
    private val dokumentMapper: DokumentMapper,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val forlengetBehandlingstidDraftRepository: ForlengetBehandlingstidDraftRepository,
) {

    fun getOrCreateForlengetBehandlingstidDraft(behandlingId: UUID): ForlengetBehandlingstidDraftView {
        val behandling = getBehandlingForUpdate(behandlingId = behandlingId)

        if (behandling is BehandlingWithVarsletBehandlingstid) {
            if (behandling.forlengetBehandlingstidDraft == null) {
                behandling.forlengetBehandlingstidDraft = ForlengetBehandlingstidDraft()
            }
        } else {
            error("Behandling har ikke varslet behandlingstid")
        }

        behandling.forlengetBehandlingstidDraft!!.title = "Nav klageinstans orienterer om forlenget behandlingstid"

        val previousBehandlingstidInfo = getvarsletBehandlingstidInfo(
            varsletBehandlingstid = behandling.varsletBehandlingstid,
            varsletBehandlingstidHistorikk = behandling.varsletBehandlingstidHistorikk,
        )

        behandling.forlengetBehandlingstidDraft!!.previousBehandlingstidInfo = previousBehandlingstidInfo

        if (behandling.prosessfullmektig != null) {
            val name = behandling.prosessfullmektig!!.partId?.value?.let {
                partSearchService.searchPart(
                    identifikator = it,
                    skipAccessControl = true
                ).name
            } ?: behandling.prosessfullmektig?.navn
            behandling.forlengetBehandlingstidDraft!!.fullmektigFritekst = name
        }

        return behandling.forlengetBehandlingstidDraft!!.toView(behandling = behandling)
    }

    fun setTitle(behandlingId: UUID, input: ForlengetBehandlingstidTitleInput): ForlengetBehandlingstidDraftView {
        val behandling = getBehandlingWithForlengetBehandlingstidDraft(behandlingId = behandlingId)
        behandling.forlengetBehandlingstidDraft!!.title = input.title
        return behandling.forlengetBehandlingstidDraft!!.toView(behandling = behandling as Behandling)
    }

    fun setFullmektigFritekst(
        behandlingId: UUID,
        input: ForlengetBehandlingstidFullmektigFritekstInput
    ): ForlengetBehandlingstidDraftView {
        val behandling = getBehandlingWithForlengetBehandlingstidDraft(behandlingId = behandlingId)
        behandling.forlengetBehandlingstidDraft!!.fullmektigFritekst = input.fullmektigFritekst
        return behandling.forlengetBehandlingstidDraft!!.toView(behandling = behandling as Behandling)
    }

    fun setCustomText(
        behandlingId: UUID,
        input: ForlengetBehandlingstidCustomTextInput
    ): ForlengetBehandlingstidDraftView {
        val behandling = getBehandlingWithForlengetBehandlingstidDraft(behandlingId = behandlingId)
        behandling.forlengetBehandlingstidDraft!!.customText = input.customText
        return behandling.forlengetBehandlingstidDraft!!.toView(behandling = behandling as Behandling)
    }

    fun setReason(behandlingId: UUID, input: ForlengetBehandlingstidReasonInput): ForlengetBehandlingstidDraftView {
        val behandling = getBehandlingWithForlengetBehandlingstidDraft(behandlingId = behandlingId)
        behandling.forlengetBehandlingstidDraft!!.reason = input.reason
        return behandling.forlengetBehandlingstidDraft!!.toView(behandling = behandling as Behandling)
    }

    fun setBehandlingstidUnits(
        behandlingId: UUID,
        input: ForlengetBehandlingstidVarsletBehandlingstidUnitsInput
    ): ForlengetBehandlingstidDraftView {
        val behandling = getBehandlingWithForlengetBehandlingstidDraft(behandlingId = behandlingId)

        validateNewFrist(
            newFrist = findDateBasedOnTimeUnitTypeAndUnits(
                timeUnitType = behandling.forlengetBehandlingstidDraft!!.behandlingstid.varsletBehandlingstidUnitType!!,
                units = input.varsletBehandlingstidUnits.toLong(),
                fromDate = LocalDate.now(),
            ),
            oldFrist = behandling.varsletBehandlingstid?.varsletFrist,
        )

        behandling.forlengetBehandlingstidDraft!!.behandlingstid.varsletBehandlingstidUnits =
            input.varsletBehandlingstidUnits
        deleteVarsletFristIfNeeded(behandling.forlengetBehandlingstidDraft!!.behandlingstid)
        return behandling.forlengetBehandlingstidDraft!!.toView(behandling = behandling as Behandling)
    }

    fun setBehandlingstidUnitTypeId(
        behandlingId: UUID,
        input: ForlengetBehandlingstidVarsletBehandlingstidUnitTypeIdInput
    ): ForlengetBehandlingstidDraftView {
        val behandling = getBehandlingWithForlengetBehandlingstidDraft(behandlingId = behandlingId)
        if (behandling.forlengetBehandlingstidDraft!!.behandlingstid.varsletBehandlingstidUnits != null) {
            validateNewFrist(
                newFrist = findDateBasedOnTimeUnitTypeAndUnits(
                    timeUnitType = TimeUnitType.of(input.varsletBehandlingstidUnitTypeId),
                    units = behandling.forlengetBehandlingstidDraft!!.behandlingstid.varsletBehandlingstidUnits!!.toLong(),
                    fromDate = LocalDate.now(),
                ),
                oldFrist = behandling.varsletBehandlingstid?.varsletFrist,
            )
        }
        behandling.forlengetBehandlingstidDraft!!.behandlingstid.varsletBehandlingstidUnitType =
            TimeUnitType.of(input.varsletBehandlingstidUnitTypeId)
        deleteVarsletFristIfNeeded(behandling.forlengetBehandlingstidDraft!!.behandlingstid)
        return behandling.forlengetBehandlingstidDraft!!.toView(behandling = behandling as Behandling)
    }

    fun setBehandlingstidDate(
        behandlingId: UUID,
        input: ForlengetBehandlingstidBehandlingstidDateInput
    ): ForlengetBehandlingstidDraftView {
        val behandling = getBehandlingWithForlengetBehandlingstidDraft(behandlingId = behandlingId)
        validateNewFrist(newFrist = input.behandlingstidDate, oldFrist = behandling.varsletBehandlingstid?.varsletFrist)
        behandling.forlengetBehandlingstidDraft!!.behandlingstid.varsletFrist = input.behandlingstidDate
        behandling.forlengetBehandlingstidDraft!!.behandlingstid.varsletBehandlingstidUnits = null
        behandling.forlengetBehandlingstidDraft!!.behandlingstid.varsletBehandlingstidUnitType = TimeUnitType.WEEKS
        return behandling.forlengetBehandlingstidDraft!!.toView(behandling = behandling as Behandling)
    }

    fun setPreviousBehandlingstidInfo(
        behandlingId: UUID,
        input: ForlengetBehandlingstidPreviousBehandlingstidInfoInput
    ): ForlengetBehandlingstidDraftView {
        val behandling = getBehandlingWithForlengetBehandlingstidDraft(behandlingId = behandlingId)
        behandling.forlengetBehandlingstidDraft!!.previousBehandlingstidInfo = input.previousBehandlingstidInfo
        return behandling.forlengetBehandlingstidDraft!!.toView(behandling = behandling as Behandling)
    }

    fun setReceivers(
        behandlingId: UUID,
        input: MottakerInput
    ): ForlengetBehandlingstidDraftView {
        val behandling = getBehandlingForUpdate(behandlingId = behandlingId)

        if (behandling !is BehandlingWithVarsletBehandlingstid) {
            error("Behandling har ikke varslet behandlingstid")
        }

        if (behandling.forlengetBehandlingstidDraft == null) {
            error("Forlenget behandlingstidutkast mangler")
        }

        dokumentUnderArbeidService.validateMottakerList(
            mottakerInput = input,
            systemContext = false,
        )

        behandling.forlengetBehandlingstidDraft!!.receivers.clear()

        input.mottakerList.forEach {
            val (markLocalPrint, forceCentralPrint) = dokumentUnderArbeidService.getPreferredHandling(
                identifikator = it.id,
                handling = it.handling,
                isAddressOverridden = it.overriddenAddress != null,
                sakenGjelderFnr = behandling.sakenGjelder.partId.value,
                tema = behandling.ytelse.toTema(),
                systemContext = false,
            )

            behandling.forlengetBehandlingstidDraft!!.receivers.add(
                ForlengetBehandlingstidDraftReceiver(
                    navn = it.navn,
                    identifikator = it.id,
                    localPrint = markLocalPrint,
                    forceCentralPrint = forceCentralPrint,
                    address = dokumentUnderArbeidService.getDokumentUnderArbeidAdresse(it.overriddenAddress),
                )
            )
        }
        return behandling.forlengetBehandlingstidDraft!!.toView(behandling = behandling)
    }

    private fun getBehandlingWithForlengetBehandlingstidDraft(behandlingId: UUID): BehandlingWithVarsletBehandlingstid {
        val behandling = getBehandlingForUpdate(behandlingId = behandlingId)
        if (behandling !is BehandlingWithVarsletBehandlingstid) {
            error("Behandling har ikke varslet behandlingstid")
        }

        if (behandling.forlengetBehandlingstidDraft == null) {
            error("Forlenget behandlingstidutkast mangler")
        }

        return behandling
    }

    fun getPdf(behandlingId: UUID): ByteArray {
        val behandling = getBehandlingForUpdate(behandlingId = behandlingId)

        if (behandling !is BehandlingWithVarsletBehandlingstid) {
            error("Behandling har ikke varslet behandlingstid")
        }

        if (behandling.forlengetBehandlingstidDraft == null) {
            error("Forlenget behandlingstidutkast mangler")
        }

        val sakenGjelderName = partSearchService.searchPart(
            identifikator = behandling.sakenGjelder.partId.value,
            skipAccessControl = true
        ).name

        val forlengetBehandlingstidDraft = behandling.forlengetBehandlingstidDraft

        return kabalJsonToPdfService.getForlengetBehandlingstidPDF(
            title = forlengetBehandlingstidDraft?.title!!,
            sakenGjelderName = sakenGjelderName,
            sakenGjelderIdentifikator = behandling.sakenGjelder.partId.value,
            klagerIdentifikator = behandling.klager.partId.value,
            klagerName = if (behandling.klager.partId.value != behandling.sakenGjelder.partId.value) {
                partSearchService.searchPart(
                    identifikator = behandling.klager.partId.value,
                    skipAccessControl = true
                ).name
            } else {
                sakenGjelderName
            },
            ytelse = behandling.ytelse,
            fullmektigFritekst = forlengetBehandlingstidDraft.fullmektigFritekst,
            behandlingstidUnits = forlengetBehandlingstidDraft.behandlingstid.varsletBehandlingstidUnits,
            behandlingstidUnitType = forlengetBehandlingstidDraft.behandlingstid.varsletBehandlingstidUnitType,
            behandlingstidDate = forlengetBehandlingstidDraft.behandlingstid.varsletFrist,
            avsenderEnhetId = Enhet.E4291.navn,
            type = behandling.type,
            mottattKlageinstans = behandling.mottattKlageinstans.toLocalDate(),
            previousBehandlingstidInfo = forlengetBehandlingstidDraft.previousBehandlingstidInfo,
            reason = forlengetBehandlingstidDraft.reason,
            customText = forlengetBehandlingstidDraft.customText,
        )
    }

    fun completeDraft(behandlingId: UUID) {
        val behandling = getBehandlingForUpdate(behandlingId = behandlingId)

        if (behandling !is BehandlingWithVarsletBehandlingstid) {
            error("Behandling har ikke varslet behandlingstid")
        }

        if (behandling.forlengetBehandlingstidDraft == null) {
            error("Forlenget behandlingstidutkast mangler")
        }

        if (behandling.forlengetBehandlingstidDraft!!.receivers.isEmpty()) {
            error("Mangler mottakere")
        }

        if (behandling.forlengetBehandlingstidDraft!!.behandlingstid.varsletFrist == null &&
            behandling.forlengetBehandlingstidDraft!!.behandlingstid.varsletBehandlingstidUnits == null
        ) {
            error("Trenger enten dato eller antall uker/måneder")
        }

        dokumentUnderArbeidService.createAndFinalizeForlengetBehandlingstidDokumentUnderArbeid(
            behandling = behandling,
            forlengetBehandlingstidDraft = behandling.forlengetBehandlingstidDraft!!,
            systemContext = false,
        )

        behandlingService.setForlengetBehandlingstid(
            newVarsletBehandlingstid = behandling.forlengetBehandlingstidDraft!!.behandlingstid,
            behandling = behandling,
            systemUserContext = false,
            mottakere = behandling.forlengetBehandlingstidDraft!!.receivers.map {
                if (it.identifikator != null) {
                    MottakerPartId(
                        value = getPartIdFromIdentifikator(it.identifikator)
                    )
                } else if (it.navn != null) {
                    MottakerNavn(
                        value = it.navn
                    )
                } else throw IllegalArgumentException("Missing values in receiver: $it")
            }
        )
        //TODO: Possible to do in single operation?
        val idForDeletion = behandling.forlengetBehandlingstidDraft!!.id
        behandling.forlengetBehandlingstidDraft = null
        forlengetBehandlingstidDraftRepository.deleteById(idForDeletion)
    }

    private fun getBehandlingForUpdate(behandlingId: UUID): Behandling {
        return if (innloggetSaksbehandlerService.isKabalOppgavestyringAlleEnheter()) {
            behandlingService.getBehandlingForUpdate(
                behandlingId = behandlingId,
                ignoreCheckSkrivetilgang = true,
            )
        } else {
            behandlingService.getBehandlingForUpdate(behandlingId)
        }
    }

    private fun deleteVarsletFristIfNeeded(behandlingstid: VarsletBehandlingstid) {
        if (behandlingstid.varsletBehandlingstidUnitType != null && behandlingstid.varsletBehandlingstidUnits != null) {
            behandlingstid.varsletFrist = null
        }
    }

    private fun getvarsletBehandlingstidInfo(
        varsletBehandlingstid: VarsletBehandlingstid?,
        varsletBehandlingstidHistorikk: MutableSet<VarsletBehandlingstidHistorikk>
    ): String? {
        return if (varsletBehandlingstid != null) {
            val lastVarsletBehandlingstid = varsletBehandlingstidHistorikk.maxByOrNull { it.tidspunkt }

            if (lastVarsletBehandlingstid?.varsletBehandlingstid != null) {
                if (
                    lastVarsletBehandlingstid.varsletBehandlingstid!!.varsletBehandlingstidUnits != null &&
                    lastVarsletBehandlingstid.varsletBehandlingstid!!.varsletBehandlingstidUnitType != null
                ) {
                    val previousDate = getFormattedDate(lastVarsletBehandlingstid.tidspunkt.toLocalDate())
                    val varsletBehandlingstidText =
                        getvarsletBehandlingstidText(lastVarsletBehandlingstid.varsletBehandlingstid!!)
                    "I brev fra Nav klageinstans sendt $previousDate fikk du informasjon om at forventet behandlingstid var $varsletBehandlingstidText"
                } else if (lastVarsletBehandlingstid.varsletBehandlingstid!!.varsletFrist != null) {
                    val previousDate = getFormattedDate(lastVarsletBehandlingstid.tidspunkt.toLocalDate())
                    val varsletFristDate =
                        getFormattedDate(lastVarsletBehandlingstid.varsletBehandlingstid!!.varsletFrist!!)
                    "I brev fra Nav klageinstans sendt $previousDate fikk du informasjon om at forventet behandlingsfrist var $varsletFristDate"
                } else null
            } else null
        } else null
    }

    private fun getvarsletBehandlingstidText(varsletBehandlingstid: VarsletBehandlingstid): String {
        return varsletBehandlingstid.varsletBehandlingstidUnits.toString() + when (
            varsletBehandlingstid.varsletBehandlingstidUnitType!!
        ) {
            TimeUnitType.WEEKS -> {
                if (varsletBehandlingstid.varsletBehandlingstidUnits == 1) {
                    " uke"
                } else {
                    " uker"
                }
            }

            TimeUnitType.MONTHS -> {
                if (varsletBehandlingstid.varsletBehandlingstidUnits == 1) {
                    " måned"
                } else {
                    " måneder"
                }
            }
        }
    }

    private fun getFormattedDate(localDate: LocalDate): String {
        val formatter = DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.forLanguageTag("no"))
        return localDate.format(formatter)
    }

    private fun ForlengetBehandlingstidDraft.toView(behandling: Behandling): ForlengetBehandlingstidDraftView {
        return ForlengetBehandlingstidDraftView(
            title = title,
            fullmektigFritekst = fullmektigFritekst,
            customText = customText,
            reason = reason,
            previousBehandlingstidInfo = previousBehandlingstidInfo,
            behandlingstid = behandlingstid.toView(),
            receivers = receivers.map {
                dokumentMapper.toDokumentViewMottaker(
                    identifikator = it.identifikator,
                    navn = it.navn,
                    address = it.address,
                    localPrint = it.localPrint,
                    forceCentralPrint = it.forceCentralPrint,
                    behandling = behandling
                )
            }
        )
    }


    private fun VarsletBehandlingstid.toView(): ForlengetBehandlingstidVarsletBehandlingstidView {
        return ForlengetBehandlingstidVarsletBehandlingstidView(
            varsletBehandlingstidUnits = varsletBehandlingstidUnits,
            varsletBehandlingstidUnitTypeId = varsletBehandlingstidUnitType!!.id,
            varsletFrist = varsletFrist,
        )
    }

    private fun validateNewFrist(newFrist: LocalDate?, oldFrist: LocalDate?) {
        if (newFrist != null && oldFrist != null && newFrist.isBefore(oldFrist)) {
            error("Ny frist er tidligere enn tidligere angitt frist")
        }
    }
}