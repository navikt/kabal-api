package no.nav.klage.oppgave.service

import no.nav.klage.dokument.api.mapper.DokumentMapper
import no.nav.klage.dokument.api.view.MottakerInput
import no.nav.klage.dokument.domain.dokumenterunderarbeid.Brevmottaker
import no.nav.klage.dokument.exceptions.DokumentValidationException
import no.nav.klage.dokument.service.DokumentUnderArbeidService
import no.nav.klage.dokument.service.KabalJsonToPdfService
import no.nav.klage.kodeverk.Enhet
import no.nav.klage.kodeverk.TimeUnitType
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.BehandlingWithVarsletBehandlingstid
import no.nav.klage.oppgave.domain.behandling.embedded.MottakerNavn
import no.nav.klage.oppgave.domain.behandling.embedded.MottakerPartId
import no.nav.klage.oppgave.domain.behandling.embedded.VarsletBehandlingstid
import no.nav.klage.oppgave.domain.behandling.historikk.VarsletBehandlingstidHistorikk
import no.nav.klage.oppgave.domain.behandling.subentities.ForlengetBehandlingstidDraft
import no.nav.klage.oppgave.exceptions.InvalidProperty
import no.nav.klage.oppgave.exceptions.SectionedValidationErrorWithDetailsException
import no.nav.klage.oppgave.exceptions.ValidationSection
import no.nav.klage.oppgave.repositories.ForlengetBehandlingstidDraftRepository
import no.nav.klage.oppgave.util.findDateBasedOnTimeUnitTypeAndUnits
import no.nav.klage.oppgave.util.getLogger
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
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun getOrCreateForlengetBehandlingstidDraft(behandlingId: UUID): ForlengetBehandlingstidDraftView {
        val behandling = getBehandlingForUpdate(behandlingId = behandlingId)

        if (behandling is BehandlingWithVarsletBehandlingstid) {
            if (behandling.forlengetBehandlingstidDraft == null) {
                behandling.forlengetBehandlingstidDraft = ForlengetBehandlingstidDraft()
                setDefaultForlengetbehandlingstidDraftValues(behandling)
            }
        } else {
            error("Behandling har ikke varslet behandlingstid")
        }

        return behandling.forlengetBehandlingstidDraft!!.toView(behandling = behandling)
    }

    fun setTitle(behandlingId: UUID, input: ForlengetBehandlingstidTitleInput): ForlengetBehandlingstidDraftView {
        val behandling = getBehandlingWithForlengetBehandlingstidDraft(behandlingId = behandlingId)
        if (behandling.forlengetBehandlingstidDraft!!.doNotSendLetter) {
            error("Kan ikke legge til tittel når brev ikke skal sendes ut")
        }
        behandling.forlengetBehandlingstidDraft!!.title = input.title
        return behandling.forlengetBehandlingstidDraft!!.toView(behandling = behandling as Behandling)
    }

    fun setFullmektigFritekst(
        behandlingId: UUID,
        input: ForlengetBehandlingstidFullmektigFritekstInput
    ): ForlengetBehandlingstidDraftView {
        val behandling = getBehandlingWithForlengetBehandlingstidDraft(behandlingId = behandlingId)
        if (behandling.forlengetBehandlingstidDraft!!.doNotSendLetter) {
            error("Kan ikke legge til navn på fullmektig i brevet når brev ikke skal sendes ut")
        }
        behandling.forlengetBehandlingstidDraft!!.fullmektigFritekst = input.fullmektigFritekst
        return behandling.forlengetBehandlingstidDraft!!.toView(behandling = behandling as Behandling)
    }

    fun setCustomText(
        behandlingId: UUID,
        input: ForlengetBehandlingstidCustomTextInput
    ): ForlengetBehandlingstidDraftView {
        val behandling = getBehandlingWithForlengetBehandlingstidDraft(behandlingId = behandlingId)
        if (behandling.forlengetBehandlingstidDraft!!.doNotSendLetter) {
            error("Kan ikke legge til fritekst når brev ikke skal sendes ut")
        }
        behandling.forlengetBehandlingstidDraft!!.customText = input.customText
        return behandling.forlengetBehandlingstidDraft!!.toView(behandling = behandling as Behandling)
    }

    fun setReason(behandlingId: UUID, input: ForlengetBehandlingstidReasonInput): ForlengetBehandlingstidDraftView {
        val behandling = getBehandlingWithForlengetBehandlingstidDraft(behandlingId = behandlingId)
        if (behandling.forlengetBehandlingstidDraft!!.doNotSendLetter) {
            error("Kan ikke legge til årsak til lengre saksbehandlingstid når brev ikke skal sendes ut")
        }
        behandling.forlengetBehandlingstidDraft!!.reason = input.reason
        return behandling.forlengetBehandlingstidDraft!!.toView(behandling = behandling as Behandling)
    }

    fun setBehandlingstidUnits(
        behandlingId: UUID,
        input: ForlengetBehandlingstidVarsletBehandlingstidUnitsInput
    ): ForlengetBehandlingstidDraftView {
        val behandling = getBehandlingWithForlengetBehandlingstidDraft(behandlingId = behandlingId)

        behandling.forlengetBehandlingstidDraft!!.varsletBehandlingstidUnits =
            input.varsletBehandlingstidUnits
        behandling.forlengetBehandlingstidDraft!!.varsletFrist = null

        return behandling.forlengetBehandlingstidDraft!!.toView(behandling = behandling as Behandling)
    }

    fun setBehandlingstidUnitTypeId(
        behandlingId: UUID,
        input: ForlengetBehandlingstidVarsletBehandlingstidUnitTypeIdInput
    ): ForlengetBehandlingstidDraftView {
        val behandling = getBehandlingWithForlengetBehandlingstidDraft(behandlingId = behandlingId)

        behandling.forlengetBehandlingstidDraft!!.varsletBehandlingstidUnitType =
            TimeUnitType.of(input.varsletBehandlingstidUnitTypeId)

        return behandling.forlengetBehandlingstidDraft!!.toView(behandling = behandling as Behandling)
    }

    fun setBehandlingstidDate(
        behandlingId: UUID,
        input: ForlengetBehandlingstidBehandlingstidDateInput
    ): ForlengetBehandlingstidDraftView {
        val behandling = getBehandlingWithForlengetBehandlingstidDraft(behandlingId = behandlingId)
        behandling.forlengetBehandlingstidDraft!!.varsletFrist = input.behandlingstidDate
        behandling.forlengetBehandlingstidDraft!!.varsletBehandlingstidUnits = null
        return behandling.forlengetBehandlingstidDraft!!.toView(behandling = behandling as Behandling)
    }

    fun setPreviousBehandlingstidInfo(
        behandlingId: UUID,
        input: ForlengetBehandlingstidPreviousBehandlingstidInfoInput
    ): ForlengetBehandlingstidDraftView {
        val behandling = getBehandlingWithForlengetBehandlingstidDraft(behandlingId = behandlingId)
        if (behandling.forlengetBehandlingstidDraft!!.doNotSendLetter) {
            error("Kan ikke legge til info om forrige behandlingstid når brev ikke skal sendes ut")
        }
        behandling.forlengetBehandlingstidDraft!!.previousBehandlingstidInfo = input.previousBehandlingstidInfo
        return behandling.forlengetBehandlingstidDraft!!.toView(behandling = behandling as Behandling)
    }

    fun setReasonNoLetter(
        behandlingId: UUID,
        input: ForlengetBehandlingstidReasonNoLetterInput
    ): ForlengetBehandlingstidDraftView {
        val behandling = getBehandlingWithForlengetBehandlingstidDraft(behandlingId = behandlingId)
        if (!behandling.forlengetBehandlingstidDraft!!.doNotSendLetter) {
            error("Kan ikke legge til begrunnelse for å ikke sende brev når brev skal sendes ut")
        }
        behandling.forlengetBehandlingstidDraft!!.reasonNoLetter = input.reasonNoLetter
        return behandling.forlengetBehandlingstidDraft!!.toView(behandling = behandling as Behandling)
    }

    fun setDoNotSendLetter(
        behandlingId: UUID,
        input: ForlengetBehandlingstidDoNotSendLetterInput
    ): ForlengetBehandlingstidDraftView {
        val behandling = getBehandlingWithForlengetBehandlingstidDraft(behandlingId = behandlingId)
        behandling.forlengetBehandlingstidDraft!!.doNotSendLetter = input.doNotSendLetter
        if (!input.doNotSendLetter) {
            behandling.forlengetBehandlingstidDraft!!.varselTypeIsOriginal = false
        }
        return behandling.forlengetBehandlingstidDraft!!.toView(behandling = behandling as Behandling)
    }

    fun setVarselTypeIsOriginal(
        behandlingId: UUID,
        input: ForlengetBehandlingstidVarselTypeIsOriginal
    ): ForlengetBehandlingstidDraftView {
        val behandling = getBehandlingWithForlengetBehandlingstidDraft(behandlingId = behandlingId)
        if (input.varselTypeIsOriginal && behandling.varsletBehandlingstid != null) {
            error("Kan ikke sette opprinnelig varslet frist når varslet frist fins fra før.")
        }
        if (!behandling.forlengetBehandlingstidDraft!!.doNotSendLetter) {
            error("Kan ikke endre varseltype når brev skal sendes ut")
        }
        behandling.forlengetBehandlingstidDraft!!.varselTypeIsOriginal = input.varselTypeIsOriginal
        return behandling.forlengetBehandlingstidDraft!!.toView(behandling = behandling as Behandling)
    }

    private fun removeLetterValues(behandling: BehandlingWithVarsletBehandlingstid) {
        behandling.forlengetBehandlingstidDraft!!.title = null
        behandling.forlengetBehandlingstidDraft!!.receivers.clear()
        behandling.forlengetBehandlingstidDraft!!.fullmektigFritekst = null
        behandling.forlengetBehandlingstidDraft!!.customText = null
        behandling.forlengetBehandlingstidDraft!!.reason = null
        behandling.forlengetBehandlingstidDraft!!.previousBehandlingstidInfo = null
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

        if (behandling.forlengetBehandlingstidDraft!!.doNotSendLetter) {
            error("Kan ikke legge til mottakere når brev ikke skal sendes ut")
        }

        dokumentUnderArbeidService.validateMottakerList(
            mottakerInput = input,
            systemContext = false,
        )

        behandling.forlengetBehandlingstidDraft!!.receivers.clear()

        input.mottakerList.forEach { mottaker ->
            val (markLocalPrint, forceCentralPrint) = dokumentUnderArbeidService.getPreferredHandling(
                identifikator = mottaker.identifikator,
                handling = mottaker.handling,
                isAddressOverridden = mottaker.overriddenAddress != null,
                sakenGjelderFnr = behandling.sakenGjelder.partId.value,
                tema = behandling.ytelse.toTema(),
                systemContext = false,
            )

            val technicalPartId =
                mottaker.id ?: behandling.getTechnicalIdFromPart(identifikator = mottaker.identifikator)

            val getAddressFromFullmektig = mottaker.identifikator == null
            if (getAddressFromFullmektig && behandling.prosessfullmektig?.id != technicalPartId) {
                throw DokumentValidationException("Kun fullmektig kan brukes som mottaker uten identifikator.")
            }

            if (behandling.forlengetBehandlingstidDraft!!.receivers.none { it.identifikator == mottaker.identifikator }) {
                behandling.forlengetBehandlingstidDraft!!.receivers.add(
                    Brevmottaker(
                        technicalPartId = technicalPartId,
                        navn = mottaker.navn,
                        identifikator = mottaker.identifikator,
                        localPrint = markLocalPrint,
                        forceCentralPrint = forceCentralPrint,
                        address = dokumentUnderArbeidService.getDokumentUnderArbeidAdresse(
                            overrideAddress = mottaker.overriddenAddress,
                            getAddressFromFullmektig = getAddressFromFullmektig,
                            fullmektig = behandling.prosessfullmektig,
                        )
                    )
                )
            }
        }
        return behandling.forlengetBehandlingstidDraft!!.toView(behandling = behandling)
    }

    private fun setDefaultForlengetbehandlingstidDraftValues(behandling: Behandling) {
        val fullmektigFritekst = if (behandling.prosessfullmektig != null) {
            val name = behandling.prosessfullmektig!!.partId?.value?.let {
                partSearchService.searchPart(
                    identifikator = it,
                    systemUserContext = true
                ).name
            } ?: behandling.prosessfullmektig?.navn
            name
        } else null

        behandling as BehandlingWithVarsletBehandlingstid

        behandling.forlengetBehandlingstidDraft!!.title = "Klageinstans orienterer om forlenget behandlingstid"

        val previousBehandlingstidInfo = getVarsletBehandlingstidInfo(
            varsletBehandlingstid = behandling.varsletBehandlingstid,
            varsletBehandlingstidHistorikk = behandling.varsletBehandlingstidHistorikk,
        )

        behandling.forlengetBehandlingstidDraft!!.previousBehandlingstidInfo = previousBehandlingstidInfo

        behandling.forlengetBehandlingstidDraft!!.fullmektigFritekst = fullmektigFritekst
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

        val validationErrors = mutableListOf<InvalidProperty>()

        validationErrors.addAll(getCommonValidationErrors(behandling))

        behandling as BehandlingWithVarsletBehandlingstid

        if (behandling.forlengetBehandlingstidDraft!!.doNotSendLetter) {
            validationErrors += InvalidProperty(
                field = "doNotSendLetter",
                reason = "Kan ikke hente pdf når brev ikke skal sendes ut"
            )
        }

        if (validationErrors.isNotEmpty()) {
            throw SectionedValidationErrorWithDetailsException(
                title = "Validation error",
                sections = listOf(
                    ValidationSection(
                        section = "forlengetBehandlingstidDraft",
                        properties = validationErrors,
                    )
                )
            )
        }

        val sakenGjelderName = partSearchService.searchPart(
            identifikator = behandling.sakenGjelder.partId.value,
            systemUserContext = true
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
                    systemUserContext = true
                ).name
            } else {
                sakenGjelderName
            },
            ytelse = behandling.ytelse,
            fullmektigFritekst = forlengetBehandlingstidDraft.fullmektigFritekst,
            behandlingstidUnits = forlengetBehandlingstidDraft.varsletBehandlingstidUnits,
            behandlingstidUnitType = forlengetBehandlingstidDraft.varsletBehandlingstidUnitType,
            behandlingstidDate = forlengetBehandlingstidDraft.varsletFrist,
            avsenderEnhetId = Enhet.E4291.navn,
            type = behandling.type,
            mottattKlageinstans = behandling.mottattKlageinstans.toLocalDate(),
            previousBehandlingstidInfo = forlengetBehandlingstidDraft.previousBehandlingstidInfo,
            reason = forlengetBehandlingstidDraft.reason,
            customText = forlengetBehandlingstidDraft.customText,
        )
    }

    private fun getCommonValidationErrors(
        behandling: Behandling,
    ): List<InvalidProperty> {
        val validationErrors = mutableListOf<InvalidProperty>()
        if (behandling !is BehandlingWithVarsletBehandlingstid) {
            throw SectionedValidationErrorWithDetailsException(
                title = "Validation error",
                sections = listOf(
                    ValidationSection(
                        section = "behandling",
                        properties = listOf(
                            InvalidProperty(
                                field = "behandling",
                                reason = "Behandling har ikke varslet behandlingstid"
                            )
                        )
                    )
                )
            )
        }

        if (behandling.forlengetBehandlingstidDraft == null) {
            throw SectionedValidationErrorWithDetailsException(
                title = "Validation error",
                sections = listOf(
                    ValidationSection(
                        section = "behandling",
                        properties = listOf(
                            InvalidProperty(
                                field = "forlengetBehandlingstidDraft",
                                reason = "Forlenget behandlingstidutkast mangler"
                            )
                        )
                    )
                )
            )
        }

        if (behandling.forlengetBehandlingstidDraft!!.varsletFrist == null &&
            behandling.forlengetBehandlingstidDraft!!.varsletBehandlingstidUnits == null
        ) {
            validationErrors += InvalidProperty(
                field = "behandlingstid",
                reason = "Trenger enten dato eller antall uker/måneder"
            )
        } else {
            val fromDate =
                if (behandling.forlengetBehandlingstidDraft!!.varselTypeIsOriginal) behandling.mottattKlageinstans.toLocalDate() else LocalDate.now()

            val currentFrist =
                behandling.forlengetBehandlingstidDraft!!.varsletFrist ?: findDateBasedOnTimeUnitTypeAndUnits(
                    timeUnitType = behandling.forlengetBehandlingstidDraft!!.varsletBehandlingstidUnitType,
                    units = behandling.forlengetBehandlingstidDraft!!.varsletBehandlingstidUnits!!,
                    fromDate = fromDate,
                )

            validationErrors.addAll(
                validateNewFrist(
                    newFrist = currentFrist,
                    oldFrist = behandling.varsletBehandlingstid?.varsletFrist,
                )
            )
        }

        return validationErrors
    }

    fun completeDraft(behandlingId: UUID) {
        val behandling = getBehandlingForUpdate(behandlingId = behandlingId)

        val validationErrors = mutableListOf<InvalidProperty>()

        validationErrors.addAll(getCommonValidationErrors(behandling))

        behandling as BehandlingWithVarsletBehandlingstid

        if (behandling.forlengetBehandlingstidDraft!!.doNotSendLetter) {
            if (behandling.forlengetBehandlingstidDraft!!.reasonNoLetter.isNullOrBlank()) {
                validationErrors += InvalidProperty(
                    field = "reasonNoLetter",
                    reason = "Trenger oppgitt begrunnelse for å ikke sende brev"
                )
            }
        } else {
            if (behandling.forlengetBehandlingstidDraft!!.receivers.isEmpty()) {
                validationErrors += InvalidProperty(
                    field = "mottakere",
                    reason = "Mangler mottakere"
                )
            }
            if (behandling.forlengetBehandlingstidDraft!!.title.isNullOrBlank()) {
                validationErrors += InvalidProperty(
                    field = "title",
                    reason = "Trenger tittel på brevet"
                )
            }
        }

        if (validationErrors.isNotEmpty()) {
            throw SectionedValidationErrorWithDetailsException(
                title = "Validation error",
                sections = listOf(
                    ValidationSection(
                        section = "forlengetBehandlingstidDraft",
                        properties = validationErrors,
                    )
                )
            )
        }

        if (!behandling.forlengetBehandlingstidDraft!!.doNotSendLetter) {
            behandling.forlengetBehandlingstidDraft!!.reasonNoLetter = null

            dokumentUnderArbeidService.createAndFinalizeForlengetBehandlingstidDokumentUnderArbeid(
                behandling = behandling,
                forlengetBehandlingstidDraft = behandling.forlengetBehandlingstidDraft!!,
            )
        } else {
            removeLetterValues(behandling)
        }

        behandlingService.setVarsletFrist(
            varsletFrist = behandling.forlengetBehandlingstidDraft!!.varsletFrist,
            varsletBehandlingstidUnits = behandling.forlengetBehandlingstidDraft!!.varsletBehandlingstidUnits,
            varsletBehandlingstidUnitType = behandling.forlengetBehandlingstidDraft!!.varsletBehandlingstidUnitType,
            behandlingId = behandling.id,
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
            },
            doNotSendLetter = behandling.forlengetBehandlingstidDraft!!.doNotSendLetter,
            reasonNoLetter = behandling.forlengetBehandlingstidDraft!!.reasonNoLetter,
            varselType = if (behandling.forlengetBehandlingstidDraft!!.varselTypeIsOriginal) VarsletBehandlingstid.VarselType.OPPRINNELIG else VarsletBehandlingstid.VarselType.FORLENGET,
            fromDate = if (behandling.forlengetBehandlingstidDraft!!.varselTypeIsOriginal) behandling.mottattKlageinstans.toLocalDate() else LocalDate.now()
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

    private fun getVarsletBehandlingstidInfo(
        varsletBehandlingstid: VarsletBehandlingstid?,
        varsletBehandlingstidHistorikk: MutableSet<VarsletBehandlingstidHistorikk>
    ): String? {
        return if (varsletBehandlingstid != null) {
            val lastVarsletBehandlingstid = varsletBehandlingstidHistorikk.maxByOrNull { it.tidspunkt }
            if (lastVarsletBehandlingstid?.varsletBehandlingstid != null) {
                val lastBehandlingstidHadLetter = !lastVarsletBehandlingstid.varsletBehandlingstid!!.doNotSendLetter

                val lastBehandlingstidHadUnitsAndType =
                    (lastVarsletBehandlingstid.varsletBehandlingstid!!.varsletBehandlingstidUnits != null &&
                            lastVarsletBehandlingstid.varsletBehandlingstid!!.varsletBehandlingstidUnitType != null)

                val lastBehandlingstidHadFrist = lastVarsletBehandlingstid.varsletBehandlingstid!!.varsletFrist != null

                val previousDate = getFormattedDate(lastVarsletBehandlingstid.tidspunkt.toLocalDate())

                val lastVarsletBehandlingstidText = if (lastBehandlingstidHadUnitsAndType) {
                    getvarsletBehandlingstidText(lastVarsletBehandlingstid.varsletBehandlingstid!!)
                } else if (lastBehandlingstidHadFrist) {
                    getFormattedDate(lastVarsletBehandlingstid.varsletBehandlingstid!!.varsletFrist!!)
                } else {
                    //Should not happen
                    logger.error(
                        "Varslet behandlingstid har verken units og type eller frist. Skal ikke skje. VarsletBehandlingstid: $lastVarsletBehandlingstid"
                    )
                    return null
                }

                if (lastBehandlingstidHadLetter) {
                    "I brev fra klageinstansen sendt $previousDate fikk du informasjon om at forventet behandlingstid var $lastVarsletBehandlingstidText"
                } else {
                    "Den $previousDate ble forventet saksbehandlingstid endret til $lastVarsletBehandlingstidText"
                }
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
            behandlingstid = ForlengetBehandlingstidVarsletBehandlingstidView(
                varsletBehandlingstidUnits = varsletBehandlingstidUnits,
                varsletBehandlingstidUnitTypeId = varsletBehandlingstidUnitType.id,
                varsletFrist = varsletFrist,
                calculatedFrist = getCalculatedFrist(
                    draft = this,
                    mottattKlageinstans = behandling.mottattKlageinstans.toLocalDate()
                )
            ),
            reasonNoLetter = reasonNoLetter,
            doNotSendLetter = doNotSendLetter,
            receivers = receivers.map {
                dokumentMapper.toDokumentViewMottaker(
                    technicalPartId = it.technicalPartId,
                    identifikator = it.identifikator,
                    navn = it.navn,
                    address = it.address,
                    localPrint = it.localPrint,
                    forceCentralPrint = it.forceCentralPrint,
                    behandling = behandling
                )
            },
            timesPreviouslyExtended = behandling.getTimesPreviouslyExtended(),
            varselTypeIsOriginal = varselTypeIsOriginal,
        )
    }

    private fun getCalculatedFrist(draft: ForlengetBehandlingstidDraft, mottattKlageinstans: LocalDate): LocalDate? {
        return if (draft.varsletFrist != null) {
            draft.varsletFrist
        } else if (draft.varsletBehandlingstidUnits != null) {
            val fromDate = if (draft.varselTypeIsOriginal) mottattKlageinstans else LocalDate.now()
            findDateBasedOnTimeUnitTypeAndUnits(
                timeUnitType = draft.varsletBehandlingstidUnitType,
                units = draft.varsletBehandlingstidUnits!!,
                fromDate = fromDate,
            )
        } else null
    }

    private fun validateNewFrist(newFrist: LocalDate, oldFrist: LocalDate?): List<InvalidProperty> {
        val validationErrors = mutableListOf<InvalidProperty>()

        if (newFrist.isAfter(LocalDate.now().plusMonths(4))) {
            validationErrors += InvalidProperty(
                field = "behandlingstid",
                reason = "Ny frist er lengre frem i tid enn 4 måneder"
            )
        }

        if (oldFrist != null) {
            if (newFrist.isBefore(oldFrist)) {
                validationErrors += InvalidProperty(
                    field = "behandlingstid",
                    reason = "Ny frist er tidligere enn tidligere angitt frist"
                )
            } else if (newFrist == oldFrist) {
                validationErrors += InvalidProperty(
                    field = "behandlingstid",
                    reason = "Ny frist er den samme som tidligere angitt frist"
                )
            }
        }
        return validationErrors
    }
}