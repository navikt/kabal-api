package no.nav.klage.oppgave.service

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.klage.dokument.api.view.AddressInput
import no.nav.klage.dokument.api.view.HandlingEnum
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsHoveddokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAvsenderMottakerInfo
import no.nav.klage.dokument.domain.dokumenterunderarbeid.Svarbrev
import no.nav.klage.dokument.repositories.DokumentUnderArbeidRepository
import no.nav.klage.dokument.service.DokumentUnderArbeidService
import no.nav.klage.dokument.service.KabalJsonToPdfService
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.kodeverk.Enhet
import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.api.view.BehandlingDetaljerView
import no.nav.klage.oppgave.domain.events.AutomaticSvarbrevEvent
import no.nav.klage.oppgave.domain.klage.Behandling
import no.nav.klage.oppgave.domain.klage.MottakerNavn
import no.nav.klage.oppgave.domain.klage.MottakerPartId
import no.nav.klage.oppgave.domain.klage.SvarbrevSettings
import no.nav.klage.oppgave.repositories.AutomaticSvarbrevEventRepository
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getPartIdFromIdentifikator
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Files
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@Service
@Transactional
class AutomaticSvarbrevService(
    private val automaticSvarbrevEventRepository: AutomaticSvarbrevEventRepository,
    private val behandlingService: BehandlingService,
    private val svarbrevSettingsService: SvarbrevSettingsService,
    private val kabalJsonToPdfService: KabalJsonToPdfService,
    private val partSearchService: PartSearchService,
    private val dokumentUnderArbeidService: DokumentUnderArbeidService,
    @Value("\${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
    private val mottakService: MottakService,
    private val dokumentUnderArbeidRepository: DokumentUnderArbeidRepository,
    private val environment: Environment
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val svarbrevTitle = "Nav klageinstans orienterer om saksbehandlingen"
    }

    @Scheduled(timeUnit = TimeUnit.MINUTES, fixedDelay = 2, initialDelay = 2)
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
        val behandling = try {
            behandlingService.getBehandlingForReadWithoutCheckForAccess(automaticSvarbrevEvent.behandlingId)
        } catch (ex: Exception) {
            if (environment.activeProfiles.contains("dev-gcp")) {
                logger.debug("Missing behandling with id ${automaticSvarbrevEvent.behandlingId} in dev, skipping")
                automaticSvarbrevEvent.status = AutomaticSvarbrevEvent.AutomaticSvarbrevStatus.HANDLED
                automaticSvarbrevEvent.modified = LocalDateTime.now()
                automaticSvarbrevEventRepository.save(automaticSvarbrevEvent)
                return
            } else {
                throw ex
            }
        }

        if (behandling.type != Type.KLAGE) {
            logger.debug(
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
            mottakService.createTaskForMerkantil(
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

            val receiver = getReceiver(behandling = behandling)
            val receiverValidationError = validateReceiver(receiver)
            if (receiverValidationError != null) {
                //Dette gir de godkjente feilene vi kjenner til, og skal sendes videre til merkantil.
                logger.error(receiverValidationError)
                mottakService.createTaskForMerkantil(
                    behandlingId = behandling.id,
                    reason = receiverValidationError
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
                    receiver = receiver
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
                setReceiversInDokumentUnderArbeid(dokumentUnderArbeid, receiver, behandling)
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
                behandlingService.setOpprinneligVarsletFrist(
                    behandlingstidUnitType = svarbrevSettings.behandlingstidUnitType,
                    behandlingstidUnits = svarbrevSettings.behandlingstidUnits,
                    behandling = behandling,
                    systemUserContext = true,
                    mottakere = listOf(
                        if (receiver.id != null) {
                            MottakerPartId(
                                value = getPartIdFromIdentifikator(receiver.id)
                            )
                        } else if (receiver.navn != null) {
                            MottakerNavn(
                                value = receiver.navn
                            )
                        } else throw IllegalArgumentException("Missing values in receiver: $receiver")
                    )
                )
                automaticSvarbrevEvent.varsletFristIsSetInBehandling = true
                automaticSvarbrevEvent.modified = LocalDateTime.now()
                automaticSvarbrevEventRepository.save(automaticSvarbrevEvent)
            }

            logger.debug("Svarbrev klargjort for utsending for behandling {}", behandling.id)
            automaticSvarbrevEvent.status = AutomaticSvarbrevEvent.AutomaticSvarbrevStatus.HANDLED
            automaticSvarbrevEvent.modified = LocalDateTime.now()
            automaticSvarbrevEventRepository.save(automaticSvarbrevEvent)
        }
    }

    private fun setReceiversInDokumentUnderArbeid(
        dokumentUnderArbeid: DokumentUnderArbeidAsHoveddokument,
        receiver: Svarbrev.Receiver,
        behandling: Behandling
    ) {
        dokumentUnderArbeid.avsenderMottakerInfoSet.clear()

        val (markLocalPrint, forceCentralPrint) = dokumentUnderArbeidService.getPreferredHandling(
            identifikator = receiver.id,
            handling = HandlingEnum.valueOf(receiver.handling.name),
            isAddressOverridden = receiver.overriddenAddress != null,
            sakenGjelderFnr = behandling.sakenGjelder.partId.value,
            tema = behandling.ytelse.toTema(),
            systemContext = true

        )

        dokumentUnderArbeid.avsenderMottakerInfoSet.add(
            DokumentUnderArbeidAvsenderMottakerInfo(
                identifikator = receiver.id,
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
                        )
                    )
                },
                navn = receiver.navn,
            )
        )
        dokumentUnderArbeid.modified = LocalDateTime.now()
        dokumentUnderArbeidRepository.save(dokumentUnderArbeid)
    }

    private fun validateReceiver(
        receiver: Svarbrev.Receiver,
    ): String? {
        if (receiver.id != null) {
            val part = partSearchService.searchPart(
                identifikator = receiver.id,
                skipAccessControl = true
            )

            when (part.type) {
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

    private fun getSvarbrevPDF(
        behandling: Behandling,
        svarbrevSettings: SvarbrevSettings,
        receiver: Svarbrev.Receiver
    ): ByteArray {
        return kabalJsonToPdfService.getSvarbrevPDF(
            svarbrev = Svarbrev(
                title = svarbrevTitle,
                receivers = listOf(
                    receiver
                ),
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
                skipAccessControl = true
            ).name,
            ytelse = behandling.ytelse,
            klagerIdentifikator = behandling.klager.partId.value,
            klagerName = partSearchService.searchPart(
                identifikator = behandling.klager.partId.value,
                skipAccessControl = true,
            ).name,
            //Hardcode KA Oslo
            avsenderEnhetId = Enhet.E4291.navn,
        )

    }

    private fun getReceiver(behandling: Behandling): Svarbrev.Receiver {
        return if (behandling.prosessfullmektig != null) {
            val prosessfullmektig = behandling.prosessfullmektig!!
            if (prosessfullmektig.partId != null) {
                Svarbrev.Receiver(
                    id = prosessfullmektig.partId.value,
                    handling = Svarbrev.Receiver.HandlingEnum.AUTO,
                    overriddenAddress = null,
                    navn = null
                )
            } else {
                Svarbrev.Receiver(
                    id = null,
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
            }
        } else if (behandling.klager.partId.value != behandling.sakenGjelder.partId.value) {
            Svarbrev.Receiver(
                id = behandling.klager.partId.value,
                handling = Svarbrev.Receiver.HandlingEnum.AUTO,
                overriddenAddress = null,
                navn = null
            )
        } else {
            Svarbrev.Receiver(
                id = behandling.sakenGjelder.partId.value,
                handling = Svarbrev.Receiver.HandlingEnum.AUTO,
                overriddenAddress = null,
                navn = null
            )
        }
    }

    private fun logSchedulerMessage(functionName: String) {
        logger.debug("$functionName is called by scheduler")
    }
}