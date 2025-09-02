package no.nav.klage.oppgave.service


import io.micrometer.core.instrument.MeterRegistry
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Utfall
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.ytelseToHjemler
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.api.view.kabin.*
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.norg2.Norg2Client
import no.nav.klage.oppgave.config.incrementMottattKlageAnke
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.embedded.Klager
import no.nav.klage.oppgave.domain.behandling.embedded.PartId
import no.nav.klage.oppgave.domain.behandling.embedded.Prosessfullmektig
import no.nav.klage.oppgave.domain.behandling.embedded.SakenGjelder
import no.nav.klage.oppgave.domain.kodeverk.LovligeTyper
import no.nav.klage.oppgave.domain.mottak.Mottak
import no.nav.klage.oppgave.domain.mottak.MottakDokument
import no.nav.klage.oppgave.domain.mottak.MottakDokumentType
import no.nav.klage.oppgave.domain.mottak.MottakHjemmel
import no.nav.klage.oppgave.exceptions.DuplicateOversendelseException
import no.nav.klage.oppgave.exceptions.JournalpostNotFoundException
import no.nav.klage.oppgave.exceptions.OversendtKlageNotValidException
import no.nav.klage.oppgave.exceptions.PreviousBehandlingNotFinalizedException
import no.nav.klage.oppgave.gateway.AzureGateway
import no.nav.klage.oppgave.repositories.*
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getTeamLogger
import no.nav.klage.oppgave.util.isValidFnrOrDnr
import no.nav.klage.oppgave.util.isValidOrgnr
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
    private val omgjoeringskravbehandlingRepository: OmgjoeringskravbehandlingRepository,
    private val behandlingRepository: BehandlingRepository,
    private val dokumentService: DokumentService,
    private val norg2Client: Norg2Client,
    private val azureGateway: AzureGateway,
    private val meterRegistry: MeterRegistry,
    private val createBehandlingFromMottak: CreateBehandlingFromMottak,
    private val personService: PersonService,
    private val eregClient: EregClient,
) {

    private val lovligeTyperIMottakV2 = LovligeTyper.lovligeTyper(environment)


    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val teamLogger = getTeamLogger()
    }

    @Transactional
    fun createMottakForKlageV2(oversendtKlage: OversendtKlageV2): Behandling {
        logger.debug("Prøver å lagre oversendtKlageV2. Se team-logs for detaljer.")
        teamLogger.debug("Prøver å lagre oversendtKlageV2: {}", oversendtKlage)
        oversendtKlage.validate()

        val mottak = mottakRepository.save(oversendtKlage.toMottak())

        logger.debug("Har opprettet mottak med id {}", mottak.id)

        val behandling = createBehandlingFromMottak.createBehandling(mottak)

        updateMetrics(
            kilde = oversendtKlage.kilde.name,
            ytelse = oversendtKlage.ytelse.navn,
            type = oversendtKlage.type.navn,
        )

        return behandling
    }

    @Transactional
    fun createMottakForKlageAnkeV3(oversendtKlageAnke: OversendtKlageAnkeV3): Behandling {
        logger.debug("Prøver å lagre oversendtKlageAnkeV3. Se team-logs for detaljer.")
        teamLogger.debug("Prøver å lagre oversendtKlageAnkeV3: {}", oversendtKlageAnke)

        val mottak = validateAndSaveMottak(oversendtKlageAnke)

        logger.debug("Har opprettet mottak med id {}", mottak.id)

        val behandling = createBehandlingFromMottak.createBehandling(mottak)

        updateMetrics(
            kilde = oversendtKlageAnke.kilde.name,
            ytelse = oversendtKlageAnke.ytelse.navn,
            type = oversendtKlageAnke.type.navn,
        )
        return behandling
    }

    @Transactional
    fun createMottakForKlageAnkeV4(oversendtKlageAnke: OversendtKlageAnkeV4): Behandling {
        logger.debug("Prøver å lagre oversendtKlageAnkeV4. Se team-logs for detaljer.")
        teamLogger.debug("Prøver å lagre oversendtKlageAnkeV4: {}", oversendtKlageAnke)

        val mottak = validateAndSaveMottak(oversendtKlageAnke)

        logger.debug("Har opprettet mottak med id {}", mottak.id)

        val behandling = createBehandlingFromMottak.createBehandling(mottak)

        updateMetrics(
            kilde = oversendtKlageAnke.fagsak.fagsystem.name,
            ytelse = oversendtKlageAnke.ytelse.navn,
            type = oversendtKlageAnke.type.name.lowercase()
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
        )
        return behandling
    }

    fun validateAnkeITrygderettenV1(input: OversendtAnkeITrygderettenV1) {
        validateYtelseAndHjemler(input.ytelse, input.hjemler)
        validatePartId(input.klager.id.toPartId())
        validateJournalpostList(input.tilknyttedeJournalposter.map { it.journalpostId })
        input.sakenGjelder?.run { validatePartId(input.sakenGjelder.id.toPartId()) }
        validateOptionalDateTimeNotInFuture(
            input.sakMottattKaTidspunkt,
            OversendtAnkeITrygderettenV1::sakMottattKaTidspunkt.name
        )
        validateOptionalDateTimeNotInFuture(
            input.sendtTilTrygderetten,
            OversendtAnkeITrygderettenV1::sendtTilTrygderetten.name
        )
        validateKildeReferanse(input.kildeReferanse)
        if (input.dvhReferanse != null && input.dvhReferanse.isBlank()) {
            throw OversendtKlageNotValidException("DVHReferanse kan ikke være en tom streng.")
        }

        if (isBehandlingDuplicate(
                fagsystem = input.fagsak.fagsystem,
                kildeReferanse = input.kildeReferanse,
                type = Type.ANKE_I_TRYGDERETTEN
            )
        ) {
            val message =
                "Kunne ikke lagre oversendelse grunnet duplikat: kilde ${input.fagsak.fagsystem.name} og kildereferanse: ${input.kildeReferanse}"
            logger.warn(message)
            throw DuplicateOversendelseException(message)
        }
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
                            if (oversendtKlageAnke.fagsak.fagsystem != Fagsystem.PP01) {
                                val message =
                                    "Tidligere behandlet klage har annen dvhReferanse enn innsendt anke."
                                logger.warn(message)
                                throw OversendtKlageNotValidException(message)
                            } else {
                                logger.debug("dvhReferanse ${oversendtKlageAnke.dvhReferanse} matcher ikke med klage. Godtar, fordi det er anke fra Pesys.")
                            }
                        }
                        mottakRepository.save(oversendtKlageAnke.toMottak(previousHandledKlage.id))
                    } else {
                        mottakRepository.save(oversendtKlageAnke.toMottak())
                    }
                }

                Type.ANKE_I_TRYGDERETTEN -> TODO()
                Type.BEHANDLING_ETTER_TRYGDERETTEN_OPPHEVET -> TODO()
                Type.OMGJOERINGSKRAV -> TODO()
            }
        return mottak
    }

    private fun validateAndSaveMottak(oversendtKlageAnke: OversendtKlageAnkeV4): Mottak {
        oversendtKlageAnke.validate()

        val mottak =
            when (oversendtKlageAnke.type) {
                OversendtType.KLAGE -> mottakRepository.save(oversendtKlageAnke.toMottak())
                OversendtType.ANKE -> {
                    val previousHandledKlage =
                        klagebehandlingRepository.findByKildeReferanseAndYtelseAndFeilregistreringIsNull(
                            oversendtKlageAnke.kildeReferanse,
                            oversendtKlageAnke.ytelse
                        )
                    if (previousHandledKlage != null) {
                        logger.debug("Fant tidligere behandlet klage i Kabal, med id {}", previousHandledKlage.id)
                        if (oversendtKlageAnke.dvhReferanse != previousHandledKlage.dvhReferanse) {
                            if (oversendtKlageAnke.fagsak.fagsystem != Fagsystem.PP01) {
                                val message =
                                    "Tidligere behandlet klage har annen dvhReferanse enn innsendt anke."
                                logger.warn(message)
                                throw OversendtKlageNotValidException(message)
                            } else {
                                logger.debug("dvhReferanse ${oversendtKlageAnke.dvhReferanse} matcher ikke med klage. Godtar, fordi det er anke fra Pesys.")
                            }
                        }
                        mottakRepository.save(oversendtKlageAnke.toMottak(previousHandledKlage.id))
                    } else {
                        mottakRepository.save(oversendtKlageAnke.toMottak())
                    }
                }
            }
        return mottak
    }

    @Transactional
    fun createMottakAndBehandlingFromKabinInput(input: CreateBehandlingBasedOnKabinInput): Behandling {
        val sourceBehandlingId = input.sourceBehandlingId
        logger.debug("Prøver å lagre behandling basert på Kabin-input med sourceBehandlingId {}", sourceBehandlingId)
        val sourceBehandling = behandlingRepository.findById(sourceBehandlingId).get()

        validateBehandlingCreationBasedOnSourceBehandling(
            sourceBehandling = sourceBehandling,
            receivedDocumentJournalpostId = input.receivedDocumentJournalpostId
        )

        validateParts(
            sakenGjelderIdentifikator = sourceBehandling.sakenGjelder.partId.value,
            prosessfullmektigIdentifikator = input.fullmektig?.value,
        )

        val mottak = mottakRepository.save(sourceBehandling.toMottak(input))

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
        logger.debug("Prøver å lage mottak fra anke fra Kabin. Se team-logs for detaljer.")
        teamLogger.debug("Prøver å lage mottak fra anke fra Kabin: {}", input)

        input.validate()

        val mottak = mottakRepository.save(input.toMottak())

        logger.debug("Har opprettet mottak med id {}", mottak.id)

        val behandling = createBehandlingFromMottak.createBehandling(mottak)

        updateMetrics(
            kilde = mottak.fagsystem.name,
            ytelse = mottak.ytelse.navn,
            type = mottak.type.navn,
        )

        return behandling
    }

    @Transactional
    fun createOmgjoeringskravBasedOnJournalpost(input: CreateOmgjoeringskravBasedOnJournalpostInput): Behandling {
        logger.debug("Prøver å lage mottak fra OmgjoeringskravBasedOnJournalpost fra Kabin. Se team-logs for detaljer.")
        teamLogger.debug("Prøver å lage mottak fra OmgjoeringskravBasedOnJournalpost fra Kabin: {}", input)

        input.validate()

        val mottak = mottakRepository.save(input.toMottak())

        logger.debug("Har opprettet mottak med id {}", mottak.id)

        val behandling = createBehandlingFromMottak.createBehandling(mottak = mottak, isBasedOnJournalpost = true)

        updateMetrics(
            kilde = mottak.fagsystem.name,
            ytelse = mottak.ytelse.navn,
            type = mottak.type.navn,
        )

        return behandling
    }

    @Transactional
    fun createKlageMottakFromKabinInput(klageInput: CreateKlageBasedOnKabinInput): Behandling {
        logger.debug("Prøver å lage mottak fra klage fra Kabin. Se team-logs for detaljer.")
        teamLogger.debug("Prøver å lage mottak fra klage fra Kabin: {}", klageInput)

        klageInput.validate()

        val mottak = mottakRepository.save(klageInput.toMottak())

        logger.debug("Har opprettet mottak med id {}", mottak.id)

        val behandling = createBehandlingFromMottak.createBehandling(mottak)

        updateMetrics(
            kilde = mottak.fagsystem.name,
            ytelse = mottak.ytelse.navn,
            type = mottak.type.navn,
        )

        return behandling
    }

    private fun validateBehandlingCreationBasedOnSourceBehandling(
        sourceBehandling: Behandling,
        receivedDocumentJournalpostId: String,
    ) {
        if (sourceBehandling.ferdigstilling?.avsluttet == null) {
            throw PreviousBehandlingNotFinalizedException("Behandling med id ${sourceBehandling.id} er ikke fullført")
        }
        validateDocumentNotAlreadyUsed(receivedDocumentJournalpostId, sourceBehandling.sakenGjelder.partId.value)
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
                    Type.BEHANDLING_ETTER_TRYGDERETTEN_OPPHEVET -> true//Ikke relevant for behandlingEtterTrygderettenOpphevet
                    Type.OMGJOERINGSKRAV -> omgjoeringskravbehandlingRepository.findByMottakId(it.id)?.feilregistrering == null
                }
            }
            .flatMap { it.mottakDokument }
            .filter {
                it.type in listOf(
                    MottakDokumentType.BRUKERS_ANKE,
                    MottakDokumentType.BRUKERS_KLAGE,
                    MottakDokumentType.BRUKERS_OMGJOERINGSKRAV
                )
            }
            .map { it.journalpostId }.toSet().toList()
    }

    fun OversendtKlageV2.validate() {
        validateDuplicate(kilde, kildeReferanse, type)
        validateYtelseAndHjemler(ytelse, hjemler)
        validateJournalpostList(tilknyttedeJournalposter.map { it.journalpostId })
        validatePartId(klager.id.toPartId())
        klager.klagersProsessfullmektig?.id?.let { validatePartId(it.toPartId()) }
        sakenGjelder?.run { validatePartId(sakenGjelder.id.toPartId()) }
        validateType(type)
        validateEnhet(avsenderEnhet)
        validateKildeReferanse(kildeReferanse)
        validateDateNotInFuture(mottattFoersteinstans, ::mottattFoersteinstans.name)
        validateDateNotInFuture(innsendtTilNav, ::innsendtTilNav.name)
        validateOptionalDateTimeNotInFuture(oversendtKaDato, ::oversendtKaDato.name)
        validateSaksbehandler(avsenderSaksbehandlerIdent, avsenderEnhet)
        validatePartsLegacy(sakenGjelder = sakenGjelder, klager = klager)
    }

    fun OversendtKlageAnkeV3.validate() {
        validateYtelseAndHjemler(ytelse, hjemler)
        validateDuplicate(kilde, kildeReferanse, type)
        validateJournalpostList(tilknyttedeJournalposter.map { it.journalpostId })
        validatePartId(klager.id.toPartId())
        klager.klagersProsessfullmektig?.id?.let { validatePartId(it.toPartId()) }
        sakenGjelder?.run { validatePartId(sakenGjelder.id.toPartId()) }
        validateDateNotInFuture(brukersHenvendelseMottattNavDato, ::brukersHenvendelseMottattNavDato.name)
        validateDateNotInFuture(innsendtTilNav, ::innsendtTilNav.name)
        validateDateNotInFuture(sakMottattKaDato, ::sakMottattKaDato.name)
        validateOptionalDateTimeNotInFuture(sakMottattKaTidspunkt, ::sakMottattKaTidspunkt.name)
        validateKildeReferanse(kildeReferanse)
        validateEnhet(forrigeBehandlendeEnhet)
        validatePartsLegacy(sakenGjelder = sakenGjelder, klager = klager)
    }

    fun validatePartsLegacy(sakenGjelder: OversendtSakenGjelder?, klager: OversendtKlagerLegacy) {
        if (klager.klagersProsessfullmektig != null) {
            if (sakenGjelder == null) {
                if (klager.id.verdi == klager.klagersProsessfullmektig.id.verdi) {
                    throw OversendtKlageNotValidException("Siden saken gjelder ikke er satt, så regner vi med at det er samme som klager, og da kan ikke prosessfullmektig være samme person.")
                }
            } else {
                if (sakenGjelder.id.verdi == klager.klagersProsessfullmektig.id.verdi) {
                    throw OversendtKlageNotValidException("Saken gjelder og prosessfullmektig kan ikke være samme person.")
                }
            }
        }
    }

    fun OversendtKlageAnkeV4.validate() {
        validateYtelseAndHjemler(ytelse, hjemler)
        validateDuplicate(fagsak.fagsystem, kildeReferanse, Type.valueOf(type.name))
        validateJournalpostList(tilknyttedeJournalposter.map { it.journalpostId })
        validatePartId(sakenGjelder.id.toPartId())
        klager?.run { validatePartId(klager.id.toPartId()) }
        if (type == OversendtType.KLAGE) {
            if (brukersKlageMottattVedtaksinstans == null) {
                throw OversendtKlageNotValidException("${::brukersKlageMottattVedtaksinstans.name} må være satt for klage.")
            }
            validateDateNotInFuture(brukersKlageMottattVedtaksinstans, ::brukersKlageMottattVedtaksinstans.name)
        }
        validateOptionalDateTimeNotInFuture(sakMottattKaTidspunkt, ::sakMottattKaTidspunkt.name)
        validateKildeReferanse(kildeReferanse)
        validateEnhet(forrigeBehandlendeEnhet)
        prosessfullmektig?.let { validateProsessfullmektig(it) }
        validateParts(
            sakenGjelderIdentifikator = sakenGjelder.id.verdi,
            prosessfullmektigIdentifikator = prosessfullmektig?.id?.verdi
        )
    }

    fun validateParts(sakenGjelderIdentifikator: String, prosessfullmektigIdentifikator: String?) {
        if (prosessfullmektigIdentifikator != null) {
            if (sakenGjelderIdentifikator == prosessfullmektigIdentifikator) {
                throw OversendtKlageNotValidException("Saken gjelder og prosessfullmektig kan ikke være samme person.")
            }
        }
    }

    private fun validateProsessfullmektig(prosessfullmektig: OversendtProsessfullmektig) {
        if (prosessfullmektig.id == null && prosessfullmektig.navn == null && prosessfullmektig.adresse == null) {
            throw OversendtKlageNotValidException("Enten id eller navn og adresse må være satt.")
        }

        if (prosessfullmektig.id != null && (prosessfullmektig.adresse != null || prosessfullmektig.navn != null)) {
            throw OversendtKlageNotValidException("Adresse og navn kan bare settes når id ikke er satt.")
        }

        if ((prosessfullmektig.adresse != null && prosessfullmektig.navn == null) || (prosessfullmektig.adresse == null && prosessfullmektig.navn != null)) {
            throw OversendtKlageNotValidException("Både adresse og navn må være satt.")
        }

        if (prosessfullmektig.id != null) {
            validatePartId(prosessfullmektig.id.toPartId())
        } else {
            prosessfullmektig.adresse!!.validateAddress()
        }
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
        validateParts(
            sakenGjelderIdentifikator = sakenGjelder.value,
            prosessfullmektigIdentifikator = fullmektig?.value,
        )
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
        validateParts(
            sakenGjelderIdentifikator = sakenGjelder.value,
            prosessfullmektigIdentifikator = fullmektig?.value,
        )
    }

    fun CreateOmgjoeringskravBasedOnJournalpostInput.validate() {
        validateDocumentNotAlreadyUsed(receivedDocumentJournalpostId, sakenGjelder.value)
        validateYtelseAndHjemler(
            ytelse = Ytelse.of(ytelseId),
            hjemler = hjemmelIdList.map { Hjemmel.of(it) }
        )
        validateDuplicate(Fagsystem.of(fagsystemId), kildereferanse, Type.ANKE)
        validateJournalpostList(listOf(receivedDocumentJournalpostId))
        klager?.toPartId()?.let { validatePartId(it) }
        validatePartId(sakenGjelder.toPartId())
        fullmektig?.let { validatePartId(it.toPartId()) }
        validateDateNotInFuture(mottattNav, ::mottattNav.name)
        validateKildeReferanse(kildereferanse)
        validateEnhet(forrigeBehandlendeEnhet)
        validateParts(
            sakenGjelderIdentifikator = sakenGjelder.value,
            prosessfullmektigIdentifikator = fullmektig?.value,
        )
    }

    fun behandlingIsDuplicate(fagsystem: Fagsystem, kildeReferanse: String, type: Type): BehandlingIsDuplicateResponse {
        return BehandlingIsDuplicateResponse(
            fagsystemId = fagsystem.id,
            kildereferanse = kildeReferanse,
            typeId = type.id,
            duplicate = isBehandlingDuplicate(
                fagsystem = fagsystem,
                kildeReferanse = kildeReferanse,
                type = type
            )
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
        if (ytelse in ytelseToHjemler.keys) {
            if (!hjemler.isNullOrEmpty()) {
                hjemler.forEach {
                    if (!ytelseToHjemler[ytelse]!!.contains(it)) {
                        throw OversendtKlageNotValidException("Behandling med ytelse ${ytelse.navn} kan ikke registreres med hjemmel $it. Ta kontakt med team klage dersom du mener hjemmelen skal være mulig å bruke for denne ytelsen.")
                    }
                }
            } else {
                throw OversendtKlageNotValidException("Behandling kan ikke registreres, mangler hjemmel.")
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

                if (!personService.personExists(partId.value)) {
                    throw OversendtKlageNotValidException("Personen fins ikke i PDL")
                }

                if (personService.getPersonInfo(partId.value).harBeskyttelsesbehovStrengtFortrolig()) {
                    throw OversendtKlageNotValidException("Personen skal ikke håndteres i Kabal. Kontakt Team Klage om du har spørsmål.")
                }
            }
        }
    }

    private fun Behandling.toMottak(input: CreateBehandlingBasedOnKabinInput): Mottak {
        val klager = if (input.klager == null || input.klager.value == sakenGjelder.partId.value) {
            Klager(
                id = sakenGjelder.id,
                partId = sakenGjelder.partId,
            )
        } else {
            Klager(
                id = UUID.randomUUID(),
                partId = PartId(
                    type = PartIdType.of(input.klager.type.name),
                    value = input.klager.value
                ),
            )
        }

        val prosessfullmektig = if (input.fullmektig != null) {
            if (input.fullmektig.value == klager.partId.value) {
                Prosessfullmektig(
                    id = klager.id,
                    partId = klager.partId,
                    navn = null,
                    address = null
                )
            } else {
                Prosessfullmektig(
                    id = UUID.randomUUID(),
                    partId = PartId(
                        type = PartIdType.of(input.fullmektig.type.name),
                        value = input.fullmektig.value
                    ),
                    navn = null,
                    address = null
                )
            }
        } else {
            null
        }

        val type = Type.of(input.typeId)
        val innsendtDokument =
            mutableSetOf(
                MottakDokument(
                    type = when (type) {
                        Type.ANKE -> MottakDokumentType.BRUKERS_ANKE
                        Type.OMGJOERINGSKRAV -> MottakDokumentType.BRUKERS_OMGJOERINGSKRAV
                        else -> error("Ugyldig type $type")
                    },
                    journalpostId = input.receivedDocumentJournalpostId
                )
            )

        val hjemmelCollection = input.hjemmelIdList.map { Hjemmel.of(it) }

        return Mottak(
            type = type,
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
            brukersKlageMottattVedtaksinstans = input.mottattNav,
            sakMottattKaDato = input.mottattNav.atStartOfDay(),
            frist = input.frist,
            created = LocalDateTime.now(),
            modified = LocalDateTime.now(),
            ytelse = ytelse,
            kommentar = null,
            forrigeBehandlingId = id,
            sentFrom = Mottak.Sender.KABIN,
            prosessfullmektig = prosessfullmektig,

            )
    }

    fun CreateKlageBasedOnKabinInput.toMottak(forrigeBehandlingId: UUID? = null): Mottak {
        val sakenGjelder = SakenGjelder(
            id = UUID.randomUUID(),
            partId = sakenGjelder.toPartId(),
        )

        val klager = if (klager == null || klager.value == sakenGjelder.partId.value) {
            Klager(
                id = sakenGjelder.id,
                partId = sakenGjelder.partId,
            )
        } else {
            Klager(
                id = UUID.randomUUID(),
                partId = PartId(
                    type = PartIdType.of(klager.type.name),
                    value = klager.value
                ),
            )
        }

        val prosessfullmektig = if (fullmektig != null) {
            if (fullmektig.value == klager.partId.value) {
                Prosessfullmektig(
                    id = klager.id,
                    partId = klager.partId,
                    navn = null,
                    address = null
                )
            } else {
                Prosessfullmektig(
                    id = UUID.randomUUID(),
                    partId = PartId(
                        type = PartIdType.of(fullmektig.type.name),
                        value = fullmektig.value
                    ),
                    navn = null,
                    address = null
                )
            }
        } else {
            null
        }

        return Mottak(
            type = Type.KLAGE,
            klager = klager,
            sakenGjelder = sakenGjelder,
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
            brukersKlageMottattVedtaksinstans = brukersHenvendelseMottattNav,
            sakMottattKaDato = sakMottattKa.atStartOfDay(),
            frist = frist,
            ytelse = Ytelse.of(ytelseId),
            forrigeBehandlingId = forrigeBehandlingId,
            sentFrom = Mottak.Sender.KABIN,
            kommentar = null,
            prosessfullmektig = prosessfullmektig,
            forrigeSaksbehandlerident = null,
        )
    }

    fun CreateAnkeBasedOnCompleteKabinInput.toMottak(): Mottak {
        val sakenGjelder = SakenGjelder(
            id = UUID.randomUUID(),
            partId = sakenGjelder.toPartId(),
        )

        val klager = if (klager == null || klager.value == sakenGjelder.partId.value) {
            Klager(
                id = sakenGjelder.id,
                partId = sakenGjelder.partId,
            )
        } else {
            Klager(
                id = UUID.randomUUID(),
                partId = PartId(
                    type = PartIdType.of(klager.type.name),
                    value = klager.value
                ),
            )
        }

        val prosessfullmektig = if (fullmektig != null) {
            if (fullmektig.value == klager.partId.value) {
                Prosessfullmektig(
                    id = klager.id,
                    partId = klager.partId,
                    navn = null,
                    address = null
                )
            } else {
                Prosessfullmektig(
                    id = UUID.randomUUID(),
                    partId = PartId(
                        type = PartIdType.of(fullmektig.type.name),
                        value = fullmektig.value
                    ),
                    navn = null,
                    address = null
                )
            }
        } else {
            null
        }

        return Mottak(
            type = Type.ANKE,
            klager = klager,
            sakenGjelder = sakenGjelder,
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
            brukersKlageMottattVedtaksinstans = mottattNav,
            sakMottattKaDato = mottattNav.atStartOfDay(),
            frist = frist,
            ytelse = Ytelse.of(ytelseId),
            forrigeBehandlingId = null,
            sentFrom = Mottak.Sender.KABIN,
            kommentar = null,
            prosessfullmektig = prosessfullmektig,
            forrigeSaksbehandlerident = null,
        )
    }

    fun CreateOmgjoeringskravBasedOnJournalpostInput.toMottak(): Mottak {
        val sakenGjelder = SakenGjelder(
            id = UUID.randomUUID(),
            partId = sakenGjelder.toPartId(),
        )

        val klager = if (klager == null || klager.value == sakenGjelder.partId.value) {
            Klager(
                id = sakenGjelder.id,
                partId = sakenGjelder.partId,
            )
        } else {
            Klager(
                id = UUID.randomUUID(),
                partId = PartId(
                    type = PartIdType.of(klager.type.name),
                    value = klager.value
                ),
            )
        }

        val prosessfullmektig = if (fullmektig != null) {
            if (fullmektig.value == klager.partId.value) {
                Prosessfullmektig(
                    id = klager.id,
                    partId = klager.partId,
                    navn = null,
                    address = null
                )
            } else {
                Prosessfullmektig(
                    id = UUID.randomUUID(),
                    partId = PartId(
                        type = PartIdType.of(fullmektig.type.name),
                        value = fullmektig.value
                    ),
                    navn = null,
                    address = null
                )
            }
        } else {
            null
        }

        return Mottak(
            type = Type.OMGJOERINGSKRAV,
            klager = klager,
            sakenGjelder = sakenGjelder,
            fagsystem = Fagsystem.of(fagsystemId),
            fagsakId = fagsakId,
            kildeReferanse = kildereferanse,
            dvhReferanse = null,
            hjemler = hjemmelIdList.map { MottakHjemmel(hjemmelId = it) }.toSet(),
            forrigeBehandlendeEnhet = forrigeBehandlendeEnhet,
            mottakDokument = mutableSetOf(
                MottakDokument(
                    type = MottakDokumentType.BRUKERS_OMGJOERINGSKRAV,
                    journalpostId = receivedDocumentJournalpostId
                )
            ),
            brukersKlageMottattVedtaksinstans = mottattNav,
            sakMottattKaDato = mottattNav.atStartOfDay(),
            frist = frist,
            ytelse = Ytelse.of(ytelseId),
            forrigeBehandlingId = null,
            sentFrom = Mottak.Sender.KABIN,
            kommentar = null,
            prosessfullmektig = prosessfullmektig,
            forrigeSaksbehandlerident = null,
        )
    }

    fun getMottak(mottakId: UUID): Mottak = mottakRepository.getReferenceById(mottakId)
}
