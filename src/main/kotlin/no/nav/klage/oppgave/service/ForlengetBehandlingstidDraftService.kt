package no.nav.klage.oppgave.service

import no.nav.klage.dokument.api.mapper.DokumentMapper
import no.nav.klage.dokument.api.view.MottakerInput
import no.nav.klage.dokument.service.DokumentUnderArbeidService
import no.nav.klage.dokument.service.KabalJsonToPdfService
import no.nav.klage.kodeverk.Enhet
import no.nav.klage.kodeverk.TimeUnitType
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.domain.klage.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.*

@Service
@Transactional
class ForlengetBehandlingstidDraftService(
    private val behandlingService: BehandlingService,
    private val partSearchService: PartSearchService,
    private val kabalJsonToPdfService: KabalJsonToPdfService,
    private val dokumentUnderArbeidService: DokumentUnderArbeidService,
    private val dokumentMapper: DokumentMapper,
) {

    fun getOrCreateForlengetBehandlingstidDraft(behandlingId: UUID): ForlengetBehandlingstidDraftView {
        val behandling = behandlingService.getBehandlingForUpdate(behandlingId = behandlingId)

        if (behandling is BehandlingWithVarsletBehandlingstid) {
            if (behandling.forlengetBehandlingstidDraft == null) {
                behandling.forlengetBehandlingstidDraft = ForlengetBehandlingstidDraft()
            }
        } else {
            error("Behandling har ikke varslet behandlingstid")
        }

        behandling.forlengetBehandlingstidDraft!!.title = "Nav klageinstans orienterer om forlenget behandlingstid"

        if (behandling.varsletBehandlingstid != null) {
            val lastVarsletBehandlingstid = behandling.varsletBehandlingstidHistorikk.maxByOrNull { it.tidspunkt }

            if (lastVarsletBehandlingstid?.varsletBehandlingstid != null &&
                lastVarsletBehandlingstid.varsletBehandlingstid!!.varsletBehandlingstidUnits != null
            ) {
                //TODO format
//        "I brev fra Nav sendt 16. januar 2025 fikk du informasjon om at forventet behandlingstid var 6 uker."
                behandling.forlengetBehandlingstidDraft!!.previousBehandlingstidInfo =
                    "I brev fra Nav sendt ${lastVarsletBehandlingstid.tidspunkt} fikk du informasjon om at forventet behandlingstid var ${lastVarsletBehandlingstid.varsletBehandlingstid!!.varsletBehandlingstidUnits} ${lastVarsletBehandlingstid.varsletBehandlingstid!!.varsletBehandlingstidUnitType}"
            }
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
        behandling.forlengetBehandlingstidDraft!!.behandlingstid.varsletBehandlingstidUnits =
            input.varsletBehandlingstidUnits
        setVarsletFristBasedOnUnits(behandling.varsletBehandlingstid!!)
        return behandling.forlengetBehandlingstidDraft!!.toView(behandling = behandling as Behandling)
    }

    fun setBehandlingstidUnitTypeId(
        behandlingId: UUID,
        input: ForlengetBehandlingstidVarsletBehandlingstidUnitTypeIdInput
    ): ForlengetBehandlingstidDraftView {
        val behandling = getBehandlingWithForlengetBehandlingstidDraft(behandlingId = behandlingId)
        behandling.forlengetBehandlingstidDraft!!.behandlingstid.varsletBehandlingstidUnitType =
            TimeUnitType.of(input.varsletBehandlingstidUnitTypeId)
        setVarsletFristBasedOnUnits(behandling.varsletBehandlingstid!!)
        return behandling.forlengetBehandlingstidDraft!!.toView(behandling = behandling as Behandling)
    }

    fun setBehandlingstidDate(
        behandlingId: UUID,
        input: ForlengetBehandlingstidBehandlingstidDateInput
    ): ForlengetBehandlingstidDraftView {
        val behandling = getBehandlingWithForlengetBehandlingstidDraft(behandlingId = behandlingId)
        behandling.forlengetBehandlingstidDraft!!.behandlingstid.varsletFrist = input.behandlingstidDate
        behandling.forlengetBehandlingstidDraft!!.behandlingstid.varsletBehandlingstidUnits = null
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
        val behandling = behandlingService.getBehandlingForUpdate(behandlingId = behandlingId)

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

    private fun setVarsletFristBasedOnUnits(varsletBehandlingstid: VarsletBehandlingstid) {
        if (varsletBehandlingstid.varsletBehandlingstidUnits != null) {
            //Her velger vi å ta utgangspunkt i dagens dato. Ta det opp med funksjonell når vi har en demo.
            varsletBehandlingstid.varsletFrist = when (varsletBehandlingstid.varsletBehandlingstidUnitType) {
                TimeUnitType.WEEKS -> LocalDate.now()
                    .plusWeeks(varsletBehandlingstid.varsletBehandlingstidUnits!!.toLong())

                TimeUnitType.MONTHS -> LocalDate.now()
                    .plusMonths(varsletBehandlingstid.varsletBehandlingstidUnits!!.toLong())
            }
        }
    }

    private fun getBehandlingWithForlengetBehandlingstidDraft(behandlingId: UUID): BehandlingWithVarsletBehandlingstid {
        val behandling = behandlingService.getBehandlingForUpdate(behandlingId = behandlingId)
        if (behandling !is BehandlingWithVarsletBehandlingstid) {
            error("Behandling har ikke varslet behandlingstid")
        }

        if (behandling.forlengetBehandlingstidDraft == null) {
            error("Forlenget behandlingstidutkast mangler")
        }
        return behandling
    }

    fun ForlengetBehandlingstidDraft.toView(behandling: Behandling): ForlengetBehandlingstidDraftView {
        return ForlengetBehandlingstidDraftView(
            title = title,
            fullmektigFritekst = fullmektigFritekst,
            customText = customText,
            reason = reason,
            previousBehandlingstidInfo = previousBehandlingstidInfo,
            behandlingstid = getBehandlingstidView(behandlingstid),
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

    private fun getBehandlingstidView(varsletBehandlingstid: VarsletBehandlingstid): ForlengetBehandlingstidVarsletBehandlingstidView {
        return ForlengetBehandlingstidVarsletBehandlingstidView(
            varsletBehandlingstidUnits = varsletBehandlingstid.varsletBehandlingstidUnits,
            varsletBehandlingstidUnitTypeId = varsletBehandlingstid.varsletBehandlingstidUnitType.id,
            varsletFrist = varsletBehandlingstid.varsletFrist,
        )
    }

    private fun VarsletBehandlingstid.toView(): ForlengetBehandlingstidVarsletBehandlingstidView {
        return ForlengetBehandlingstidVarsletBehandlingstidView(
            varsletBehandlingstidUnits = varsletBehandlingstidUnits,
            varsletBehandlingstidUnitTypeId = varsletBehandlingstidUnitType.id,
            varsletFrist = varsletFrist,
        )
    }

    fun getPdf(behandlingId: UUID): ByteArray {
        val behandling = behandlingService.getBehandlingForUpdate(behandlingId = behandlingId)

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
            behandlingstidDate = forlengetBehandlingstidDraft.behandlingstid.varsletFrist.toString(),
            avsenderEnhetId = Enhet.E4291.navn,
            type = behandling.type,
            mottattKlageinstans = behandling.mottattKlageinstans.toLocalDate(),
            previousBehandlingstidInfo = forlengetBehandlingstidDraft.previousBehandlingstidInfo,
            reason = forlengetBehandlingstidDraft.reason,
            customText = forlengetBehandlingstidDraft.customText,
        )
    }
}