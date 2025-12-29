package no.nav.klage.oppgave.service

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.klage.dokument.api.view.AddressInput
import no.nav.klage.dokument.api.view.HandlingEnum
import no.nav.klage.dokument.domain.dokumenterunderarbeid.Brevmottaker
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsHoveddokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.Svarbrev
import no.nav.klage.dokument.repositories.DokumentUnderArbeidRepository
import no.nav.klage.dokument.service.DokumentUnderArbeidService
import no.nav.klage.dokument.service.KabalJsonToPdfService
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.kodeverk.Enhet
import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.api.view.BehandlingDetaljerView
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.embedded.MottakerNavn
import no.nav.klage.oppgave.domain.behandling.embedded.MottakerPartId
import no.nav.klage.oppgave.domain.behandling.embedded.VarsletBehandlingstid
import no.nav.klage.oppgave.domain.events.AutomaticSvarbrevEvent
import no.nav.klage.oppgave.domain.svarbrevsettings.SvarbrevSettings
import no.nav.klage.oppgave.repositories.AutomaticSvarbrevEventRepository
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getPartIdFromIdentifikator
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.time.LocalDateTime
import java.util.*

@Service
class AutomaticSvarbrevService(
    private val automaticSvarbrevEventRepository: AutomaticSvarbrevEventRepository,
    private val behandlingService: BehandlingService,
    private val svarbrevSettingsService: SvarbrevSettingsService,
    private val kabalJsonToPdfService: KabalJsonToPdfService,
    private val partSearchService: PartSearchService,
    private val dokumentUnderArbeidService: DokumentUnderArbeidService,
    @Value("\${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
    private val dokumentUnderArbeidRepository: DokumentUnderArbeidRepository,
    private val environment: Environment,
    private val taskListMerkantilService: TaskListMerkantilService,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val svarbrevTitle = "Klageinstans orienterer om saksbehandlingen"
    }

    @Scheduled(cron = "0 */2 * * * *", initialDelay = 60_000)
    @SchedulerLock(name = "handleAutomaticSvarbrevEvent")
    fun handleAutomaticSvarbrevEvents() {
        logSchedulerMessage(functionName = ::handleAutomaticSvarbrevEvents.name)
        val automaticSvarbrevEventList = automaticSvarbrevEventRepository.getAllByStatusInOrderByCreated(
            statuses = listOf(AutomaticSvarbrevEvent.AutomaticSvarbrevStatus.NOT_HANDLED)
        )
        automaticSvarbrevEventList.forEach {
            handleAutomaticSvarbrevEvent(automaticSvarbrevEvent = it)
        }
    }


    private fun handleAutomaticSvarbrevEvent(automaticSvarbrevEvent: AutomaticSvarbrevEvent) {
        logger.debug("AutomaticSvarbrevEvent: {}", automaticSvarbrevEvent.toString())
        val behandling = try {
            behandlingService.getBehandlingForReadWithoutCheckForAccess(automaticSvarbrevEvent.behandlingId)
        } catch (ex: Exception) {
            if (environment.activeProfiles.contains("dev-gcp")) {
                logger.debug("Missing behandling with id {} in dev, skipping", automaticSvarbrevEvent.behandlingId)
                automaticSvarbrevEventRepository.delete(automaticSvarbrevEvent)
                return
            } else {
                throw ex
            }
        }

        if (behandling.type != Type.KLAGE) {
            logger.error(
                "Automatisk svarbrev sendes bare for klage. Markerer event med id {} og behandling med id {} som fullført.",
                automaticSvarbrevEvent.id,
                behandling.id
            )
            automaticSvarbrevEvent.status = AutomaticSvarbrevEvent.AutomaticSvarbrevStatus.HANDLED
            automaticSvarbrevEvent.modified = LocalDateTime.now()
            automaticSvarbrevEventRepository.save(automaticSvarbrevEvent)
            return
        }

        val svarbrevSettings = svarbrevSettingsService.getSvarbrevSettingsForYtelseAndType(
            ytelse = behandling.ytelse,
            type = behandling.type
        )

        if (svarbrevSettings == null) {
            //Må skrive til merkantil-task.
            logger.error("Fant ikke svarbrevinnstillinger for ytelse ${behandling.ytelse} og type ${behandling.type}")
            taskListMerkantilService.createTaskForMerkantil(
                behandlingId = behandling.id,
                reason = "Fant ikke svarbrevinnstillinger for ytelse ${behandling.ytelse} og type ${behandling.type}"
            )
            automaticSvarbrevEvent.status = AutomaticSvarbrevEvent.AutomaticSvarbrevStatus.HANDLED
            automaticSvarbrevEvent.modified = LocalDateTime.now()
            automaticSvarbrevEventRepository.save(automaticSvarbrevEvent)
            return
        }

        if (svarbrevSettings.shouldSend) {
            logger.debug("Sender svarbrev for behandling {} i event {}", behandling.id, automaticSvarbrevEvent.id)

            val (receiver, technicalPartId) = getReceiver(behandling = behandling)
            val receiverValidationErrorMessage = getPotentialErrorMessage(receiver = receiver, behandling = behandling)
            if (receiverValidationErrorMessage != null) {
                //Dette gir de godkjente feilene vi kjenner til, og skal sendes videre til merkantil.
                logger.error(receiverValidationErrorMessage)
                taskListMerkantilService.createTaskForMerkantil(
                    behandlingId = behandling.id,
                    reason = receiverValidationErrorMessage
                )
                automaticSvarbrevEvent.status = AutomaticSvarbrevEvent.AutomaticSvarbrevStatus.HANDLED
                automaticSvarbrevEvent.modified = LocalDateTime.now()
                automaticSvarbrevEventRepository.save(automaticSvarbrevEvent)
                return
            }

            //Bare gjør dette om vi ikke har mellomlagret PDF
            if (automaticSvarbrevEvent.dokumentUnderArbeidId == null) {
                val bytes = getSvarbrevPDF(
                    behandling = behandling,
                    svarbrevSettings = svarbrevSettings,
                )

                val tmpFile = Files.createTempFile(null, null).toFile()
                tmpFile.writeBytes(bytes)

                val dokumentView = dokumentUnderArbeidService.createOpplastetDokumentUnderArbeid(
                    behandlingId = behandling.id,
                    dokumentTypeId = DokumentType.SVARBREV.id,
                    parentId = null,
                    file = tmpFile,
                    filename = svarbrevTitle,
                    utfoerendeIdent = systembrukerIdent,
                    systemContext = true
                )

                logger.debug(
                    "Svarbrev for behandling {} i event {} er lagret som dokument under arbeid med id {}",
                    behandling.id,
                    automaticSvarbrevEvent.id,
                    dokumentView.id
                )
                automaticSvarbrevEvent.dokumentUnderArbeidId = dokumentView.id
                automaticSvarbrevEvent.modified = LocalDateTime.now()
                automaticSvarbrevEventRepository.save(automaticSvarbrevEvent)
            }

            val dokumentUnderArbeid =
                dokumentUnderArbeidService.getDokumentUnderArbeid(automaticSvarbrevEvent.dokumentUnderArbeidId!!) as DokumentUnderArbeidAsHoveddokument

            if (!automaticSvarbrevEvent.receiversAreSet) {
                setReceiversInDokumentUnderArbeid(
                    dokumentUnderArbeid = dokumentUnderArbeid,
                    receiver = receiver,
                    technicalPartId = technicalPartId,
                    behandling = behandling,
                )
                automaticSvarbrevEvent.receiversAreSet = true
                automaticSvarbrevEvent.modified = LocalDateTime.now()
                automaticSvarbrevEventRepository.save(automaticSvarbrevEvent)
            }

            if (!automaticSvarbrevEvent.documentIsMarkedAsFinished) {
                dokumentUnderArbeidService.finnOgMarkerFerdigHovedDokument(
                    behandlingId = behandling.id,
                    dokumentId = dokumentUnderArbeid.id,
                    utfoerendeIdent = systembrukerIdent,
                    systemContext = true
                )
                automaticSvarbrevEvent.documentIsMarkedAsFinished = true
                automaticSvarbrevEvent.modified = LocalDateTime.now()
                automaticSvarbrevEventRepository.save(automaticSvarbrevEvent)
            }

            if (!automaticSvarbrevEvent.varsletFristIsSetInBehandling) {
                behandlingService.setVarsletFrist(
                    varsletBehandlingstidUnitType = svarbrevSettings.behandlingstidUnitType,
                    varsletBehandlingstidUnits = svarbrevSettings.behandlingstidUnits,
                    behandlingId = behandling.id,
                    systemUserContext = true,
                    mottakere = listOf(
                        if (receiver.identifikator != null) {
                            MottakerPartId(
                                value = getPartIdFromIdentifikator(receiver.identifikator)
                            )
                        } else if (receiver.navn != null) {
                            MottakerNavn(
                                value = receiver.navn
                            )
                        } else throw IllegalArgumentException("Missing values in receiver: $receiver")
                    ),
                    fromDate = behandling.mottattKlageinstans.toLocalDate(),
                    varselType = VarsletBehandlingstid.VarselType.OPPRINNELIG,
                    varsletFrist = null,
                    doNotSendLetter = false,
                    reasonNoLetter = null,
                )
                automaticSvarbrevEvent.varsletFristIsSetInBehandling = true
                automaticSvarbrevEvent.modified = LocalDateTime.now()
                automaticSvarbrevEventRepository.save(automaticSvarbrevEvent)
            }

            logger.debug("Svarbrev klargjort for utsending for behandling {}", behandling.id)
            automaticSvarbrevEvent.status = AutomaticSvarbrevEvent.AutomaticSvarbrevStatus.HANDLED
            automaticSvarbrevEvent.modified = LocalDateTime.now()
            automaticSvarbrevEventRepository.save(automaticSvarbrevEvent)
        } else {
            logger.debug("Svarbrev skal ikke sendes for behandling {}", behandling.id)
            automaticSvarbrevEvent.status = AutomaticSvarbrevEvent.AutomaticSvarbrevStatus.HANDLED
            automaticSvarbrevEvent.modified = LocalDateTime.now()
            automaticSvarbrevEventRepository.save(automaticSvarbrevEvent)
        }
    }

    private fun setReceiversInDokumentUnderArbeid(
        dokumentUnderArbeid: DokumentUnderArbeidAsHoveddokument,
        receiver: Svarbrev.Receiver,
        technicalPartId: UUID,
        behandling: Behandling,
    ) {
        dokumentUnderArbeid.brevmottakere.clear()

        val (markLocalPrint, forceCentralPrint) = dokumentUnderArbeidService.getPreferredHandling(
            identifikator = receiver.identifikator,
            handling = HandlingEnum.valueOf(receiver.handling.name),
            isAddressOverridden = receiver.overriddenAddress != null,
            sakenGjelderFnr = behandling.sakenGjelder.partId.value,
            tema = behandling.ytelse.toTema(),
            systemContext = true,
        )

        dokumentUnderArbeid.brevmottakere.add(
            Brevmottaker(
                technicalPartId = technicalPartId,
                identifikator = receiver.identifikator,
                localPrint = markLocalPrint,
                forceCentralPrint = forceCentralPrint,
                address = receiver.overriddenAddress?.let {
                    dokumentUnderArbeidService.getDokumentUnderArbeidAdresse(
                        overrideAddress = AddressInput(
                            adresselinje1 = it.adresselinje1,
                            adresselinje2 = it.adresselinje2,
                            adresselinje3 = it.adresselinje3,
                            landkode = it.landkode,
                            postnummer = it.postnummer
                        ),
                        //Already handled when creating the receiver
                        getAddressFromFullmektig = false,
                        fullmektig = null,
                    )
                },
                navn = receiver.navn,
            )
        )
        dokumentUnderArbeid.modified = LocalDateTime.now()
        dokumentUnderArbeidRepository.save(dokumentUnderArbeid)
    }

    private fun getPotentialErrorMessage(
        receiver: Svarbrev.Receiver,
        behandling: Behandling,
    ): String? {
        if (receiver.identifikator != null) {
            val part = partSearchService.searchPartWithUtsendingskanal(
                identifikator = receiver.identifikator,
                systemUserContext = true,
                sakenGjelderId = behandling.sakenGjelder.partId.value,
                tema = behandling.ytelse.toTema(),
                systemContext = true,
            )

            val (markLocalPrint, forceCentralPrint) = dokumentUnderArbeidService.getPreferredHandling(
                identifikator = receiver.identifikator,
                handling = HandlingEnum.valueOf(receiver.handling.name),
                isAddressOverridden = receiver.overriddenAddress != null,
                sakenGjelderFnr = behandling.sakenGjelder.partId.value,
                tema = behandling.ytelse.toTema(),
                systemContext = true,
            )

            if (documentWillGoToCentralPrint(forceCentralPrint = forceCentralPrint, localPrint = markLocalPrint, part = part)) {
                if (receiver.overriddenAddress == null && part.address == null) {
                    return "Mottaker ${part.name} mangler adresse i input og i PDL."
                }
            }

            when (part.type!!) {
                BehandlingDetaljerView.IdType.FNR -> if (part.statusList.any { it.status == BehandlingDetaljerView.PartStatus.Status.DEAD }) {
                    return "Mottaker ${part.name} er død, velg en annen mottaker."
                }

                BehandlingDetaljerView.IdType.ORGNR -> if (part.statusList.any { it.status == BehandlingDetaljerView.PartStatus.Status.DELETED }) {
                    return "Mottaker ${part.name} er avviklet, velg en annen mottaker."
                }
            }
        } else if (receiver.overriddenAddress == null && receiver.navn == null) {
            return "Adresse og navn må oppgis når id mangler."
        }
        return null
    }

    private fun documentWillGoToCentralPrint(
        forceCentralPrint: Boolean,
        localPrint: Boolean,
        part: BehandlingDetaljerView.SearchPartViewWithUtsendingskanal
    ): Boolean {
        return forceCentralPrint ||
                (!localPrint && part.utsendingskanal == BehandlingDetaljerView.Utsendingskanal.SENTRAL_UTSKRIFT)
    }

    private fun getSvarbrevPDF(
        behandling: Behandling,
        svarbrevSettings: SvarbrevSettings,
    ): ByteArray {
        return kabalJsonToPdfService.getSvarbrevPDF(
            svarbrev = Svarbrev(
                title = svarbrevTitle,
                receivers = emptyList(), //not needed for svarbrev pdf.
                fullmektigFritekst = null,
                varsletBehandlingstidUnits = svarbrevSettings.behandlingstidUnits,
                varsletBehandlingstidUnitType = svarbrevSettings.behandlingstidUnitType,
                type = behandling.type,
                initialCustomText = null,
                customText = svarbrevSettings.customText,
            ),
            mottattKlageinstans = behandling.mottattKlageinstans.toLocalDate(),
            sakenGjelderIdentifikator = behandling.sakenGjelder.partId.value,
            sakenGjelderName = partSearchService.searchPart(
                identifikator = behandling.sakenGjelder.partId.value,
                systemUserContext = true
            ).name,
            ytelse = behandling.ytelse,
            klagerIdentifikator = behandling.klager.partId.value,
            klagerName = partSearchService.searchPart(
                identifikator = behandling.klager.partId.value,
                systemUserContext = true,
            ).name,
            //Hardcode KA Oslo
            avsenderEnhetId = Enhet.E4291.navn,
        )

    }

    private fun getReceiver(behandling: Behandling): Pair<Svarbrev.Receiver, UUID> {
        return if (behandling.prosessfullmektig != null) {
            val prosessfullmektig = behandling.prosessfullmektig!!
            if (prosessfullmektig.partId != null) {
                Svarbrev.Receiver(
                    identifikator = prosessfullmektig.partId.value,
                    handling = Svarbrev.Receiver.HandlingEnum.AUTO,
                    overriddenAddress = null,
                    navn = null
                )
            } else {
                Svarbrev.Receiver(
                    identifikator = null,
                    handling = Svarbrev.Receiver.HandlingEnum.AUTO,
                    overriddenAddress = Svarbrev.Receiver.AddressInput(
                        adresselinje1 = prosessfullmektig.address!!.adresselinje1,
                        adresselinje2 = prosessfullmektig.address.adresselinje2,
                        adresselinje3 = prosessfullmektig.address.adresselinje3,
                        landkode = prosessfullmektig.address.landkode,
                        postnummer = prosessfullmektig.address.postnummer,
                    ),
                    navn = prosessfullmektig.navn
                )
            } to prosessfullmektig.id
        } else {
            Svarbrev.Receiver(
                identifikator = behandling.klager.partId.value,
                handling = Svarbrev.Receiver.HandlingEnum.AUTO,
                overriddenAddress = null,
                navn = null
            ) to behandling.klager.id
        }
    }

    private fun logSchedulerMessage(functionName: String) {
        logger.debug("$functionName is called by scheduler")
    }
}