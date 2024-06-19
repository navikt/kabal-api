package no.nav.klage.oppgave.service


import io.micrometer.core.instrument.MeterRegistry
import no.nav.klage.dokument.service.DokumentUnderArbeidService
import no.nav.klage.kodeverk.*
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.ytelseTilHjemler
import no.nav.klage.oppgave.api.view.OversendtKlageAnkeV3
import no.nav.klage.oppgave.api.view.OversendtKlageV2
import no.nav.klage.oppgave.api.view.kabin.CreateAnkeBasedOnCompleteKabinInput
import no.nav.klage.oppgave.api.view.kabin.CreateAnkeBasedOnKabinInput
import no.nav.klage.oppgave.api.view.kabin.CreateKlageBasedOnKabinInput
import no.nav.klage.oppgave.api.view.kabin.SvarbrevInput
import no.nav.klage.oppgave.api.view.toMottak
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.norg2.Norg2Client
import no.nav.klage.oppgave.clients.pdl.PdlFacade
import no.nav.klage.oppgave.config.incrementMottattKlageAnke
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.domain.klage.AnkebehandlingSetters.setVarsletFrist
import no.nav.klage.oppgave.domain.klage.KlagebehandlingSetters.setVarsletFrist
import no.nav.klage.oppgave.domain.kodeverk.LovligeTyper
import no.nav.klage.oppgave.exceptions.DuplicateOversendelseException
import no.nav.klage.oppgave.exceptions.JournalpostNotFoundException
import no.nav.klage.oppgave.exceptions.OversendtKlageNotValidException
import no.nav.klage.oppgave.exceptions.PreviousBehandlingNotFinalizedException
import no.nav.klage.oppgave.gateway.AzureGateway
import no.nav.klage.oppgave.repositories.AnkebehandlingRepository
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.repositories.KlagebehandlingRepository
import no.nav.klage.oppgave.repositories.MottakRepository
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.klage.oppgave.util.isValidFnrOrDnr
import no.nav.klage.oppgave.util.isValidOrgnr
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@Service
class MottakService(
    environment: Environment,
    private val mottakRepository: MottakRepository,
    private val klagebehandlingRepository: KlagebehandlingRepository,
    private val ankebehandlingRepository: AnkebehandlingRepository,
    private val behandlingRepository: BehandlingRepository,
    private val dokumentService: DokumentService,
    private val norg2Client: Norg2Client,
    private val azureGateway: AzureGateway,
    private val meterRegistry: MeterRegistry,
    private val createBehandlingFromMottak: CreateBehandlingFromMottak,
    private val pdlFacade: PdlFacade,
    private val eregClient: EregClient,
    private val dokumentUnderArbeidService: DokumentUnderArbeidService,
    private val svarbrevSettingsService: SvarbrevSettingsService,
    @Value("\${SYSTEMBRUKER_IDENT}") private val systembruker: String,
) {

    private val lovligeTyperIMottakV2 = LovligeTyper.lovligeTyper(environment)


    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    @Transactional
    fun createMottakForKlageV2(oversendtKlage: OversendtKlageV2) {
        secureLogger.debug("Prøver å lagre oversendtKlageV2: {}", oversendtKlage)
        oversendtKlage.validate()

        val mottak = mottakRepository.save(oversendtKlage.toMottak())

        secureLogger.debug("Har lagret følgende mottak basert på en oversendtKlage: {}", mottak)
        logger.debug("Har lagret mottak {}, publiserer nå event", mottak.id)

        createBehandlingFromMottak.createBehandling(mottak)

        updateMetrics(
            kilde = oversendtKlage.kilde.name,
            ytelse = oversendtKlage.ytelse.navn,
            type = oversendtKlage.type.navn,
        )
    }

    @Transactional
    fun createMottakForKlageAnkeV3(oversendtKlageAnke: OversendtKlageAnkeV3) {
        secureLogger.debug("Prøver å lagre oversendtKlageAnkeV3: {}", oversendtKlageAnke)

        val mottak = validateAndSaveMottak(oversendtKlageAnke)

        secureLogger.debug("Har lagret følgende mottak basert på en oversendtKlageAnke: {}", mottak)
        logger.debug("Har lagret mottak {}, publiserer nå event", mottak.id)

        val behandling = createBehandlingFromMottak.createBehandling(mottak)

        try {
            val svarbrevSettings = svarbrevSettingsService.getSvarbrevSettings(ytelse = mottak.ytelse)

            dokumentUnderArbeidService.createDokumentUnderArbeidFromSvarbrevInput(
                behandling = behandling,
                svarbrevInput = SvarbrevInput(
                    title = "NAV orienterer om saksbehandlingen",
                    receivers = listOf(
                        SvarbrevInput.Receiver(
                            id = behandling.sakenGjelder.partId.value,
                            handling = SvarbrevInput.Receiver.HandlingEnum.AUTO,
                            overriddenAddress = null
                        )
                    ),
                    fullmektigFritekst = null,
                    varsletBehandlingstidWeeks = svarbrevSettings.behandlingstidWeeks,
                    type = behandling.type,
                    customText = svarbrevSettings.customText,
                )
            )

            val varsletFrist = behandling.mottattKlageinstans.toLocalDate().plusWeeks(svarbrevSettings.behandlingstidWeeks.toLong())
            when (behandling) {
                is Klagebehandling -> behandling.setVarsletFrist(
                    nyVerdi = varsletFrist,
                    saksbehandlerident = systembruker,
                )
                is Ankebehandling -> behandling.setVarsletFrist(
                    nyVerdi = varsletFrist,
                    saksbehandlerident = systembruker,
                )
            }

        } catch (e: Exception) {
            logger.error("Feil ved opprettelse av svarbrev.", e)
        }

        updateMetrics(
            kilde = oversendtKlageAnke.kilde.name,
            ytelse = oversendtKlageAnke.ytelse.navn,
            type = oversendtKlageAnke.type.navn,
        )
    }

    @Transactional
    fun createMottakForKlageAnkeV3ForE2ETests(oversendtKlageAnke: OversendtKlageAnkeV3): Behandling {
        secureLogger.debug("Prøver å lagre oversendtKlageAnkeV3 for E2E-tests: {}", oversendtKlageAnke)

        val mottak = validateAndSaveMottak(oversendtKlageAnke)

        secureLogger.debug("Har lagret følgende mottak basert på en oversendtKlageAnkeV3: {}", mottak)
        logger.debug("Har lagret mottak {}, publiserer nå event", mottak.id)

        meterRegistry.incrementMottattKlageAnke(
            oversendtKlageAnke.kilde.name,
            oversendtKlageAnke.ytelse.navn,
            oversendtKlageAnke.type.navn
        )

        return createBehandlingFromMottak.createBehandling(mottak)
    }

    private fun updateMetrics(
        kilde: String,
        ytelse: String,
        type: String
    ) {
        meterRegistry.incrementMottattKlageAnke(
            kildesystem = kilde,
            ytelse = ytelse,
            type = type
        )
    }

    private fun validateAndSaveMottak(oversendtKlageAnke: OversendtKlageAnkeV3): Mottak {
        oversendtKlageAnke.validate()

        val mottak =
            when (oversendtKlageAnke.type) {
                Type.KLAGE -> mottakRepository.save(oversendtKlageAnke.toMottak())
                Type.ANKE -> {
                    val previousHandledKlage =
                        klagebehandlingRepository.findByKildeReferanseAndYtelseAndFeilregistreringIsNull(
                            oversendtKlageAnke.kildeReferanse,
                            oversendtKlageAnke.ytelse
                        )
                    if (previousHandledKlage != null) {
                        logger.debug("Fant tidligere behandlet klage i Kabal, med id {}", previousHandledKlage.id)
                        if (oversendtKlageAnke.dvhReferanse != previousHandledKlage.dvhReferanse) {
                            val message =
                                "Tidligere behandlet klage har annen dvhReferanse enn innsendt anke."
                            logger.warn(message)
                            throw OversendtKlageNotValidException(message)
                        }
                        mottakRepository.save(oversendtKlageAnke.toMottak(previousHandledKlage.id))
                    } else {
                        mottakRepository.save(oversendtKlageAnke.toMottak())
                    }
                }

                Type.ANKE_I_TRYGDERETTEN -> TODO()
            }
        return mottak
    }

    @Transactional
    fun createAnkeMottakAndBehandlingFromKabinInput(input: CreateAnkeBasedOnKabinInput): Behandling {
        val sourceBehandlingId = input.sourceBehandlingId
        logger.debug("Prøver å lagre anke basert på Kabin-input med sourceBehandlingId {}", sourceBehandlingId)
        val sourceBehandling = behandlingRepository.findById(sourceBehandlingId).get()

        validateAnkeCreationBasedOnSourceBehandling(
            sourceBehandling = sourceBehandling,
            ankeJournalpostId = input.ankeDocumentJournalpostId
        )

        val mottak = mottakRepository.save(sourceBehandling.toAnkeMottak(input))

        val behandling = createBehandlingFromMottak.createBehandling(mottak)

        updateMetrics(
            kilde = mottak.fagsystem.navn,
            ytelse = mottak.ytelse.navn,
            type = mottak.type.navn,
        )

        logger.debug(
            "Har lagret mottak {}, basert på innsendt behandlingId: {} fra Kabin",
            mottak.id,
            sourceBehandlingId
        )

        return behandling
    }

    @Transactional
    fun createAnkeMottakFromCompleteKabinInput(input: CreateAnkeBasedOnCompleteKabinInput): Behandling {
        secureLogger.debug("Prøver å lage mottak fra anke fra Kabin: {}", input)

        input.validate()

        val mottak = mottakRepository.save(input.toMottak())

        secureLogger.debug("Har lagret følgende mottak basert på en CreateAnkeBasedOnCompleteKabinInput: {}", mottak)
        logger.debug("Har lagret mottak {}, publiserer nå event", mottak.id)

        val behandling = createBehandlingFromMottak.createBehandling(mottak)

        updateMetrics(
            kilde = mottak.fagsystem.name,
            ytelse = mottak.ytelse.navn,
            type = mottak.type.navn,
        )

        return behandling
    }

    @Transactional
    fun createKlageMottakFromKabinInput(klageInput: CreateKlageBasedOnKabinInput): UUID {
        secureLogger.debug("Prøver å lage mottak fra klage fra Kabin: {}", klageInput)

        klageInput.validate()

        val mottak = mottakRepository.save(klageInput.toMottak())

        secureLogger.debug("Har lagret følgende mottak basert på en CreateKlageBasedOnKabinInput: {}", mottak)
        logger.debug("Har lagret mottak {}, publiserer nå event", mottak.id)

        val behandling = createBehandlingFromMottak.createBehandling(mottak)

        updateMetrics(
            kilde = mottak.fagsystem.name,
            ytelse = mottak.ytelse.navn,
            type = mottak.type.navn,
        )

        return behandling.id
    }

    private fun validateAnkeCreationBasedOnSourceBehandling(
        sourceBehandling: Behandling,
        ankeJournalpostId: String,
    ) {
        if (sourceBehandling.avsluttet == null) {
            throw PreviousBehandlingNotFinalizedException("Behandling med id ${sourceBehandling.id} er ikke fullført")
        }
        validateDocumentNotAlreadyUsed(ankeJournalpostId, sourceBehandling.sakenGjelder.partId.value)
    }

    private fun validateDocumentNotAlreadyUsed(journalpostId: String, sakenGjelder: String) {
        if (getUsedJournalpostIdList(sakenGjelder).any { it == journalpostId }) {
            val message =
                "Journalpost med id $journalpostId har allerede blitt brukt for å opprette klage/anke"
            logger.warn(message)
            throw DuplicateOversendelseException(message)
        }
    }

    fun getUsedJournalpostIdList(sakenGjelder: String): List<String> {
        return mottakRepository.findBySakenGjelderOrKlager(sakenGjelder)
            .asSequence()
            .filter {
                when (it.type) {
                    Type.KLAGE -> klagebehandlingRepository.findByMottakId(it.id)?.feilregistrering == null
                    Type.ANKE -> ankebehandlingRepository.findByMottakId(it.id)?.feilregistrering == null
                    Type.ANKE_I_TRYGDERETTEN -> true//Ikke relevant for AnkeITrygderetten
                }
            }
            .flatMap { it.mottakDokument }
            .filter { it.type in listOf(MottakDokumentType.BRUKERS_ANKE, MottakDokumentType.BRUKERS_KLAGE) }
            .map { it.journalpostId }.toSet().toList()
    }

    fun OversendtKlageV2.validate() {
        validateDuplicate(kilde, kildeReferanse, type)
        validateYtelseAndHjemler(ytelse, hjemler)
        validateJournalpostList(tilknyttedeJournalposter.map { it.journalpostId })
        validatePartId(klager.id.toPartId())
        sakenGjelder?.run { validatePartId(sakenGjelder.id.toPartId()) }
        validateType(type)
        validateEnhet(avsenderEnhet)
        validateKildeReferanse(kildeReferanse)
        validateDateNotInFuture(mottattFoersteinstans, ::mottattFoersteinstans.name)
        validateDateNotInFuture(innsendtTilNav, ::innsendtTilNav.name)
        validateOptionalDateTimeNotInFuture(oversendtKaDato, ::oversendtKaDato.name)
        validateSaksbehandler(avsenderSaksbehandlerIdent, avsenderEnhet)
    }

    fun OversendtKlageAnkeV3.validate() {
        validateYtelseAndHjemler(ytelse, hjemler)
        validateDuplicate(kilde, kildeReferanse, type)
        validateJournalpostList(tilknyttedeJournalposter.map { it.journalpostId })
        validatePartId(klager.id.toPartId())
        sakenGjelder?.run { validatePartId(sakenGjelder.id.toPartId()) }
        validateDateNotInFuture(brukersHenvendelseMottattNavDato, ::brukersHenvendelseMottattNavDato.name)
        validateDateNotInFuture(innsendtTilNav, ::innsendtTilNav.name)
        validateDateNotInFuture(sakMottattKaDato, ::sakMottattKaDato.name)
        validateKildeReferanse(kildeReferanse)
        validateEnhet(forrigeBehandlendeEnhet)
    }

    fun CreateKlageBasedOnKabinInput.validate() {
        validateDocumentNotAlreadyUsed(klageJournalpostId, sakenGjelder.value)
        validateYtelseAndHjemler(
            ytelse = Ytelse.of(ytelseId),
            hjemler = hjemmelIdList.map { Hjemmel.of(it) }
        )
        validateDuplicate(Fagsystem.of(fagsystemId), kildereferanse, Type.KLAGE)
        validateJournalpostList(listOf(klageJournalpostId))
        klager?.toPartId()?.let { validatePartId(it) }
        validatePartId(sakenGjelder.toPartId())
        fullmektig?.let { validatePartId(it.toPartId()) }
        validateDateNotInFuture(brukersHenvendelseMottattNav, ::brukersHenvendelseMottattNav.name)
        validateDateNotInFuture(sakMottattKa, ::sakMottattKa.name)
        validateKildeReferanse(kildereferanse)
        validateEnhet(forrigeBehandlendeEnhet)
    }

    fun CreateAnkeBasedOnCompleteKabinInput.validate() {
        validateDocumentNotAlreadyUsed(ankeJournalpostId, sakenGjelder.value)
        validateYtelseAndHjemler(
            ytelse = Ytelse.of(ytelseId),
            hjemler = hjemmelIdList.map { Hjemmel.of(it) }
        )
        validateDuplicate(Fagsystem.of(fagsystemId), kildereferanse, Type.ANKE)
        validateJournalpostList(listOf(ankeJournalpostId))
        klager?.toPartId()?.let { validatePartId(it) }
        validatePartId(sakenGjelder.toPartId())
        fullmektig?.let { validatePartId(it.toPartId()) }
        validateDateNotInFuture(mottattNav, ::mottattNav.name)
        validateKildeReferanse(kildereferanse)
        validateEnhet(forrigeBehandlendeEnhet)
    }

    fun behandlingIsDuplicate(fagsystem: Fagsystem, kildeReferanse: String, type: Type): Boolean {
        return isBehandlingDuplicate(
            fagsystem = fagsystem,
            kildeReferanse = kildeReferanse,
            type = type
        )
    }

    private fun validateDuplicate(fagsystem: Fagsystem, kildeReferanse: String, type: Type) {
        if (isBehandlingDuplicate(
                fagsystem = fagsystem,
                kildeReferanse = kildeReferanse,
                type = type
            )
        ) {
            val message =
                "Kunne ikke lagre oversendelse grunnet duplikat: kildesystem ${fagsystem.name}, kildereferanse: $kildeReferanse og type: $type"
            logger.warn(message)
            throw DuplicateOversendelseException(message)
        }
    }

    private fun isBehandlingDuplicate(fagsystem: Fagsystem, kildeReferanse: String, type: Type): Boolean {
        val potentialDuplicate = behandlingRepository.findByFagsystemAndKildeReferanseAndFeilregistreringIsNullAndType(
            fagsystem = fagsystem,
            kildeReferanse = kildeReferanse,
            type = type,
        )

        return (potentialDuplicate.any {
            it.utfall !in listOf(Utfall.RETUR, Utfall.OPPHEVET)
        })
    }

    private fun validateOptionalDateTimeNotInFuture(inputDateTime: LocalDateTime?, parameterName: String) {
        if (inputDateTime != null && LocalDateTime.now().isBefore(inputDateTime))
            throw OversendtKlageNotValidException("$parameterName kan ikke være i fremtiden, innsendt dato var $inputDateTime.")
    }

    private fun validateDateNotInFuture(inputDate: LocalDate?, parameterName: String) {
        if (inputDate != null && LocalDate.now().isBefore(inputDate))
            throw OversendtKlageNotValidException("$parameterName kan ikke være i fremtiden, innsendt dato var $inputDate.")
    }

    private fun validateKildeReferanse(kildeReferanse: String) {
        if (kildeReferanse.isEmpty())
            throw OversendtKlageNotValidException("Kildereferanse kan ikke være en tom streng.")
    }

    private fun validateYtelseAndHjemler(ytelse: Ytelse, hjemler: Collection<Hjemmel>?) {
        if (ytelse in ytelseTilHjemler.keys) {
            if (!hjemler.isNullOrEmpty()) {
                hjemler.forEach {
                    if (!ytelseTilHjemler[ytelse]!!.contains(it)) {
                        throw OversendtKlageNotValidException("Behandling med ytelse ${ytelse.navn} kan ikke registreres med hjemmel $it. Ta kontakt med team klage dersom du mener hjemmelen skal være mulig å bruke for denne ytelsen.")
                    }
                }
            }
        } else {
            throw OversendtKlageNotValidException("Behandling med ytelse ${ytelse.navn} kan ikke registreres. Ta kontakt med team klage dersom du vil ta i bruk ytelsen.")
        }
    }

    private fun validateType(type: Type) {
        if (!lovligeTyperIMottakV2.contains(type)) {
            throw OversendtKlageNotValidException("Kabal kan ikke motta klager med type $type ennå")
        }
    }

    private fun validateSaksbehandler(saksbehandlerident: String, enhetNr: String) {
        if (azureGateway.getPersonligDataOmSaksbehandlerMedIdent(saksbehandlerident).enhet.enhetId != enhetNr) {
            //throw OversendtKlageNotValidException("$saksbehandlerident er ikke saksbehandler i enhet $enhet")
            logger.warn("$saksbehandlerident er ikke saksbehandler i enhet $enhetNr")
        }
    }

    private fun validateEnhet(enhet: String) =
        try {
            norg2Client.fetchEnhet(enhet).navn
        } catch (e: RuntimeException) {
            logger.warn("Unable to validate enhet from oversendt klage: {}", enhet, e)
            throw OversendtKlageNotValidException("$enhet er ikke en gyldig NAV-enhet")
        }

    private fun validateJournalpostList(journalpostIdList: List<String>) =
        try {
            dokumentService.validateJournalpostsExistsAsSystembruker(journalpostIdList)
        } catch (e: JournalpostNotFoundException) {
            logger.warn("Unable to validate journalpost list from oversendt klage: {}", journalpostIdList, e)
            throw OversendtKlageNotValidException("$journalpostIdList inneholder en ugyldig journalpostreferanse")
        }

    private fun validatePartId(partId: PartId) {
        when (partId.type) {
            PartIdType.VIRKSOMHET -> {
                if (!isValidOrgnr(partId.value)) {
                    throw OversendtKlageNotValidException("Ugyldig organisasjonsnummer")
                }
                if (!eregClient.isOrganisasjonActive(partId.value)) {
                    throw OversendtKlageNotValidException("Organisasjonen har opphørt")
                }
            }

            PartIdType.PERSON -> {
                if (!isValidFnrOrDnr(partId.value)) {
                    throw OversendtKlageNotValidException("Ugyldig fødselsnummer")
                }

                if (!pdlFacade.personExists(partId.value)) {
                    throw OversendtKlageNotValidException("Personen fins ikke i PDL")
                }
            }
        }
    }

    private fun Behandling.toAnkeMottak(input: CreateAnkeBasedOnKabinInput): Mottak {
        val prosessfullmektig = if (input.fullmektig != null) {
            Prosessfullmektig(
                partId = PartId(
                    type = PartIdType.of(input.fullmektig.type.name),
                    value = input.fullmektig.value
                ),
                skalPartenMottaKopi = true
            )
        } else {
            null
        }

        val klager = if (input.klager != null) {
            Klager(
                partId = PartId(
                    type = PartIdType.of(input.klager.type.name),
                    value = input.klager.value
                ),
                prosessfullmektig = prosessfullmektig
            )
        } else {
            Klager(
                partId = PartId(
                    type = PartIdType.of(sakenGjelder.partId.type.name),
                    value = sakenGjelder.partId.value
                ),
                prosessfullmektig = prosessfullmektig
            )
        }

        val innsendtDokument =
            mutableSetOf(
                MottakDokument(
                    type = MottakDokumentType.BRUKERS_ANKE,
                    journalpostId = input.ankeDocumentJournalpostId
                )
            )

        val hjemmelCollection = input.hjemmelIdList.map { Hjemmel.of(it) }

        return Mottak(
            type = Type.ANKE,
            klager = klager,
            sakenGjelder = sakenGjelder,
            fagsystem = fagsystem,
            fagsakId = fagsakId,
            kildeReferanse = kildeReferanse,
            dvhReferanse = dvhReferanse,
            //Dette er søkehjemler
            hjemler = hjemmelCollection.map { MottakHjemmel(hjemmelId = it.id) }.toSet(),
            forrigeSaksbehandlerident = tildeling!!.saksbehandlerident,
            forrigeBehandlendeEnhet = tildeling!!.enhet!!,
            mottakDokument = innsendtDokument,
            innsendtDato = input.mottattNav,
            brukersHenvendelseMottattNavDato = input.mottattNav,
            sakMottattKaDato = input.mottattNav.atStartOfDay(),
            frist = input.frist,
            created = LocalDateTime.now(),
            modified = LocalDateTime.now(),
            ytelse = ytelse,
            kommentar = null,
            forrigeBehandlingId = id,
            innsynUrl = null,
            sentFrom = Mottak.Sender.KABIN,
        )
    }

    fun CreateKlageBasedOnKabinInput.toMottak(forrigeBehandlingId: UUID? = null): Mottak {
        val prosessfullmektig = if (fullmektig != null) {
            Prosessfullmektig(
                partId = fullmektig.toPartId(),
                skalPartenMottaKopi = true
            )
        } else {
            null
        }

        val klager = if (klager != null) {
            Klager(
                partId = klager.toPartId(),
                prosessfullmektig = prosessfullmektig
            )
        } else {
            Klager(
                partId = sakenGjelder.toPartId(),
                prosessfullmektig = prosessfullmektig
            )
        }

        return Mottak(
            type = Type.KLAGE,
            klager = klager,
            sakenGjelder = SakenGjelder(
                partId = sakenGjelder.toPartId(),
                //TODO ever used?
                skalMottaKopi = false
            ),
            innsynUrl = null,
            fagsystem = Fagsystem.of(fagsystemId),
            fagsakId = fagsakId,
            kildeReferanse = kildereferanse,
            dvhReferanse = null,
            hjemler = hjemmelIdList.map { MottakHjemmel(hjemmelId = it) }.toSet(),
            forrigeBehandlendeEnhet = forrigeBehandlendeEnhet,
            mottakDokument = mutableSetOf(
                MottakDokument(
                    type = MottakDokumentType.BRUKERS_KLAGE,
                    journalpostId = klageJournalpostId
                )
            ),
            innsendtDato = null,
            brukersHenvendelseMottattNavDato = brukersHenvendelseMottattNav,
            sakMottattKaDato = sakMottattKa.atStartOfDay(),
            frist = frist,
            ytelse = Ytelse.of(ytelseId),
            forrigeBehandlingId = forrigeBehandlingId,
            sentFrom = Mottak.Sender.KABIN,
            kommentar = null,
        )
    }

    fun CreateAnkeBasedOnCompleteKabinInput.toMottak(): Mottak {
        val prosessfullmektig = if (fullmektig != null) {
            Prosessfullmektig(
                partId = fullmektig.toPartId(),
                skalPartenMottaKopi = true
            )
        } else {
            null
        }

        val klager = if (klager != null) {
            Klager(
                partId = klager.toPartId(),
                prosessfullmektig = prosessfullmektig
            )
        } else {
            Klager(
                partId = sakenGjelder.toPartId(),
                prosessfullmektig = prosessfullmektig
            )
        }

        return Mottak(
            type = Type.ANKE,
            klager = klager,
            sakenGjelder = SakenGjelder(
                partId = sakenGjelder.toPartId(),
                //TODO ever used?
                skalMottaKopi = false
            ),
            innsynUrl = null,
            fagsystem = Fagsystem.of(fagsystemId),
            fagsakId = fagsakId,
            kildeReferanse = kildereferanse,
            dvhReferanse = null,
            hjemler = hjemmelIdList.map { MottakHjemmel(hjemmelId = it) }.toSet(),
            forrigeBehandlendeEnhet = forrigeBehandlendeEnhet,
            mottakDokument = mutableSetOf(
                MottakDokument(
                    type = MottakDokumentType.BRUKERS_ANKE,
                    journalpostId = ankeJournalpostId
                )
            ),
            innsendtDato = mottattNav,
            brukersHenvendelseMottattNavDato = mottattNav,
            sakMottattKaDato = mottattNav.atStartOfDay(),
            frist = frist,
            ytelse = Ytelse.of(ytelseId),
            forrigeBehandlingId = null,
            sentFrom = Mottak.Sender.KABIN,
            kommentar = null,
        )
    }

    fun getMottak(mottakId: UUID): Mottak = mottakRepository.getReferenceById(mottakId)
}
