package no.nav.klage.oppgave.service

import jakarta.transaction.Transactional
import no.nav.klage.dokument.api.view.JournalfoertDokumentReference
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.oppgave.clients.kaka.KakaApiGateway
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.repositories.AnkebehandlingRepository
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.util.getLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.Period
import java.util.*

@Service
@Transactional
class AnkebehandlingService(
    private val ankebehandlingRepository: AnkebehandlingRepository,
    private val behandlingRepository: BehandlingRepository,
    private val kakaApiGateway: KakaApiGateway,
    private val dokumentService: DokumentService,
    private val behandlingService: BehandlingService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    @Value("#{T(java.time.LocalDate).parse('\${KAKA_VERSION_2_DATE}')}")
    private val kakaVersion2Date: LocalDate,
    @Value("\${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun getAnkebehandlingerBasedOnSourceBehandlingId(sourceBehandlingId: UUID): List<Ankebehandling> {
        return ankebehandlingRepository.findBySourceBehandlingIdAndFeilregistreringIsNull(sourceBehandlingId = sourceBehandlingId)
    }

    fun getCompletedAnkebehandlingerByPartIdValue(
        partIdValue: String
    ): List<Ankebehandling> {
        return ankebehandlingRepository.getCompletedAnkebehandlinger(partIdValue = partIdValue)
    }

    fun createAnkebehandlingFromMottak(mottak: Mottak): Ankebehandling {
        val kvalitetsvurderingVersion = getKakaVersion()

        val ankebehandling = ankebehandlingRepository.save(
            Ankebehandling(
                klager = mottak.klager.copy(),
                sakenGjelder = mottak.sakenGjelder?.copy() ?: mottak.klager.toSakenGjelder(),
                ytelse = mottak.ytelse,
                type = mottak.type,
                kildeReferanse = mottak.kildeReferanse,
                dvhReferanse = mottak.dvhReferanse,
                fagsystem = mottak.fagsystem,
                fagsakId = mottak.fagsakId,
                innsendt = mottak.innsendtDato,
                mottattKlageinstans = mottak.sakMottattKaDato,
                tildeling = null,
                frist = mottak.generateFrist(),
                mottakId = mottak.id,
                saksdokumenter = dokumentService.createSaksdokumenterFromJournalpostIdList(mottak.mottakDokument.map { it.journalpostId }),
                kakaKvalitetsvurderingId = kakaApiGateway.createKvalitetsvurdering(kvalitetsvurderingVersion = kvalitetsvurderingVersion).kvalitetsvurderingId,
                kakaKvalitetsvurderingVersion = kvalitetsvurderingVersion,
                hjemler = createHjemmelSetFromMottak(mottak.hjemler),
                klageBehandlendeEnhet = mottak.forrigeBehandlendeEnhet,
                sourceBehandlingId = mottak.forrigeBehandlingId,
                previousSaksbehandlerident = mottak.forrigeSaksbehandlerident,
                oppgaveId = null,
            )
        )
        logger.debug("Created ankebehandling {} for mottak {}", ankebehandling.id, mottak.id)

        if (mottak.forrigeBehandlingId != null) {
            val behandling = behandlingRepository.findById(mottak.forrigeBehandlingId).get()
            val dokumenter = behandling.saksdokumenter

            logger.debug(
                "Adding saksdokumenter from behandling {} to ankebehandling {}",
                mottak.forrigeBehandlingId,
                ankebehandling.id
            )

            behandlingService.connectDocumentsToBehandling(
                behandlingId = ankebehandling.id,
                journalfoertDokumentReferenceSet = dokumenter.map {
                    JournalfoertDokumentReference(
                        journalpostId = it.journalpostId,
                        dokumentInfoId = it.dokumentInfoId
                    )
                }.toSet(),
                saksbehandlerIdent = systembrukerIdent,
                systemUserContext = true,
                ignoreCheckSkrivetilgang = true
            )
        }

        applicationEventPublisher.publishEvent(
            BehandlingEndretEvent(
                behandling = ankebehandling,
                endringslogginnslag = emptyList()
            )
        )
        return ankebehandling
    }

    private fun getKakaVersion(): Int {
        val kvalitetsvurderingVersion = if (LocalDate.now() >= kakaVersion2Date) {
            2
        } else {
            1
        }
        return kvalitetsvurderingVersion
    }

    fun createAnkebehandlingFromAnkeITrygderettenbehandling(ankeITrygderettenbehandling: AnkeITrygderettenbehandling): Ankebehandling {
        val kvalitetsvurderingVersion = getKakaVersion()

        val ankebehandling = ankebehandlingRepository.save(
            Ankebehandling(
                klager = ankeITrygderettenbehandling.klager.copy(),
                sakenGjelder = ankeITrygderettenbehandling.sakenGjelder.copy(),
                ytelse = ankeITrygderettenbehandling.ytelse,
                type = Type.ANKE,
                kildeReferanse = ankeITrygderettenbehandling.kildeReferanse,
                dvhReferanse = ankeITrygderettenbehandling.dvhReferanse,
                fagsystem = ankeITrygderettenbehandling.fagsystem,
                fagsakId = ankeITrygderettenbehandling.fagsakId,
                innsendt = ankeITrygderettenbehandling.mottattKlageinstans.toLocalDate(),
                mottattKlageinstans = ankeITrygderettenbehandling.mottattKlageinstans,
                tildeling = null,
                frist = LocalDate.now() + Period.ofWeeks(12),
                kakaKvalitetsvurderingId = kakaApiGateway.createKvalitetsvurdering(kvalitetsvurderingVersion = kvalitetsvurderingVersion).kvalitetsvurderingId,
                kakaKvalitetsvurderingVersion = kvalitetsvurderingVersion,
                hjemler = ankeITrygderettenbehandling.hjemler,
                klageBehandlendeEnhet = ankeITrygderettenbehandling.tildeling?.enhet!!,
                sourceBehandlingId = ankeITrygderettenbehandling.id,
                previousSaksbehandlerident = ankeITrygderettenbehandling.tildeling?.saksbehandlerident,
                oppgaveId = ankeITrygderettenbehandling.oppgaveId,
            )
        )
        logger.debug(
            "Created ankebehandling {} from ankeITrygderettenbehandling {}",
            ankebehandling.id,
            ankeITrygderettenbehandling.id
        )

        behandlingService.connectDocumentsToBehandling(
            behandlingId = ankebehandling.id,
            journalfoertDokumentReferenceSet = ankeITrygderettenbehandling.saksdokumenter.map {
                JournalfoertDokumentReference(
                    journalpostId = it.journalpostId,
                    dokumentInfoId = it.dokumentInfoId
                )
            }.toSet(),
            saksbehandlerIdent = systembrukerIdent,
            systemUserContext = true,
            ignoreCheckSkrivetilgang = true
        )

        applicationEventPublisher.publishEvent(
            BehandlingEndretEvent(
                behandling = ankebehandling,
                endringslogginnslag = listOfNotNull(
                    Endringslogginnslag.endringslogg(
                        saksbehandlerident = ankeITrygderettenbehandling.tildeling!!.saksbehandlerident,
                        felt = Felt.ANKEBEHANDLING_OPPRETTET_BASERT_PAA_ANKE_I_TRYGDERETTEN,
                        fraVerdi = null,
                        tilVerdi = "Opprettet",
                        behandlingId = ankebehandling.id,
                        tidspunkt = ankebehandling.created,
                    )
                )
            )
        )

        //TODO: Undersøk om vi skal sende noen infomelding om at dette har skjedd

        return ankebehandling
    }

    private fun createHjemmelSetFromMottak(hjemler: Set<MottakHjemmel>?): MutableSet<Hjemmel> =
        if (hjemler.isNullOrEmpty()) {
            mutableSetOf(Hjemmel.MANGLER)
        } else {
            hjemler.map { Hjemmel.of(it.hjemmelId) }.toMutableSet()
        }
}