package no.nav.klage.oppgave.service


import io.micrometer.core.instrument.MeterRegistry
import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.clients.norg2.Norg2Client
import no.nav.klage.oppgave.config.incrementMottattKlage
import no.nav.klage.oppgave.domain.Behandling
import no.nav.klage.oppgave.domain.events.MottakLagretEvent
import no.nav.klage.oppgave.domain.kodeverk.LovligeTyper
import no.nav.klage.oppgave.eventlisteners.CreateBehandlingFromMottakEventListener
import no.nav.klage.oppgave.exceptions.DuplicateOversendelseException
import no.nav.klage.oppgave.exceptions.JournalpostNotFoundException
import no.nav.klage.oppgave.exceptions.OversendtKlageNotValidException
import no.nav.klage.oppgave.gateway.AzureGateway
import no.nav.klage.oppgave.repositories.MottakRepository
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.klage.oppgave.util.isValidFnrOrDnr
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MottakService(
    environment: Environment,
    private val mottakRepository: MottakRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val dokumentService: DokumentService,
    private val norg2Client: Norg2Client,
    private val azureGateway: AzureGateway,
    private val meterRegistry: MeterRegistry,
    private val createBehandlingFromMottakEventListener: CreateBehandlingFromMottakEventListener,
) {

    private val lovligeTyperIKabal = LovligeTyper.lovligeTyper(environment)


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

        applicationEventPublisher.publishEvent(MottakLagretEvent(mottak))

        //TODO: Move to outside of transaction to make sure it went well
        meterRegistry.incrementMottattKlage(oversendtKlage.kilde.name, oversendtKlage.ytelse.toTema().navn)
    }

    @Transactional
    fun createMottakForKlageAnkeV3(oversendtKlageAnke: OversendtKlageAnkeV3) {
        secureLogger.debug("Prøver å lagre oversendtKlageAnkeV3: {}", oversendtKlageAnke)
        oversendtKlageAnke.validate()

        val mottak = mottakRepository.save(oversendtKlageAnke.toMottak())

        secureLogger.debug("Har lagret følgende mottak basert på en oversendtKlage: {}", mottak)
        logger.debug("Har lagret mottak {}, publiserer nå event", mottak.id)

        applicationEventPublisher.publishEvent(MottakLagretEvent(mottak))

        //TODO: Move to outside of transaction to make sure it went well
        meterRegistry.incrementMottattKlage(oversendtKlageAnke.kilde.name, oversendtKlageAnke.ytelse.toTema().navn)

    }

    @Transactional
    fun createMottakForKlageAnkeV3ForE2ETests(oversendtKlageAnke: OversendtKlageAnkeV3): Behandling {
        secureLogger.debug("Prøver å lagre oversendtKlageAnkeV3 for E2E-tests: {}", oversendtKlageAnke)
        oversendtKlageAnke.validate()

        val mottak = mottakRepository.save(oversendtKlageAnke.toMottak())

        secureLogger.debug("Har lagret følgende mottak basert på en oversendtKlage: {}", mottak)
        logger.debug("Har lagret mottak {}, publiserer nå event", mottak.id)

        return createBehandlingFromMottakEventListener.createBehandling(MottakLagretEvent(mottak))
    }

    fun OversendtKlageV2.validate() {
        validateDuplicate(kilde, kildeReferanse)
        tilknyttedeJournalposter.forEach { validateJournalpost(it.journalpostId) }
        validatePartId(klager.id)
        sakenGjelder?.run { validatePartId(sakenGjelder.id) }
        validateType(type)
        validateEnhet(avsenderEnhet)
        validateSaksbehandler(avsenderSaksbehandlerIdent, avsenderEnhet)
    }

    fun OversendtKlageAnkeV3.validate() {
        validateDuplicate(kilde, kildeReferanse)
        tilknyttedeJournalposter.forEach { validateJournalpost(it.journalpostId) }
        validatePartId(klager.id)
        sakenGjelder?.run { validatePartId(sakenGjelder.id) }
        validateEnhet(forrigeBehandlendeEnhet)
    }

    private fun validateDuplicate(kildeFagsystem: KildeFagsystem, kildeReferanse: String) {
        if (mottakRepository.existsByKildesystemAndKildeReferanse(kildeFagsystem.mapFagsystem(), kildeReferanse)) {
            val message =
                "Kunne ikke lagre oversendelse grunnet duplikat: kilde ${kildeFagsystem.name} og kildereferanse: $kildeReferanse"
            logger.warn(message)
            throw DuplicateOversendelseException(message)
        }
    }

    private fun validateType(type: Type) {
        if (!lovligeTyperIKabal.contains(type)) {
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

    private fun validateJournalpost(journalpostId: String) =
        try {
            dokumentService.validateJournalpostExistsAsSystembruker(journalpostId)
        } catch (e: JournalpostNotFoundException) {
            logger.warn("Unable to validate journalpost from oversendt klage: {}", journalpostId, e)
            throw OversendtKlageNotValidException("$journalpostId er ikke en gyldig journalpost referanse")
        }

    private fun validatePartId(partId: OversendtPartId) {
        if (partId.type == OversendtPartIdType.VIRKSOMHET) {
            return
        }

        if (!isValidFnrOrDnr(partId.verdi)) {
            throw OversendtKlageNotValidException("Ugyldig fødselsnummer")
        }
    }
}
