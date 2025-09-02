package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.clients.kaka.KakaApiGateway
import no.nav.klage.oppgave.domain.behandling.Klagebehandling
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent.Change.Companion.createChange
import no.nav.klage.oppgave.domain.mottak.Mottak
import no.nav.klage.oppgave.repositories.KlagebehandlingRepository
import no.nav.klage.oppgave.util.getLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional
class KlagebehandlingService(
    private val klagebehandlingRepository: KlagebehandlingRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val dokumentService: DokumentService,
    private val kakaApiGateway: KakaApiGateway,
    @Value("#{T(java.time.LocalDate).parse('\${KAKA_VERSION_2_DATE}')}")
    private val kakaVersion2Date: LocalDate,
    @Value("\${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun createKlagebehandlingFromMottak(mottak: Mottak): Klagebehandling {
        val kvalitetsvurderingVersion = getKakaVersion()

        val klagebehandling = klagebehandlingRepository.save(
            Klagebehandling(
                klager = mottak.klager.copy(),
                sakenGjelder = mottak.sakenGjelder?.copy() ?: mottak.klager.toSakenGjelder(),
                prosessfullmektig = mottak.prosessfullmektig,
                ytelse = mottak.ytelse,
                type = mottak.type,
                kildeReferanse = mottak.kildeReferanse,
                dvhReferanse = mottak.dvhReferanse,
                fagsystem = mottak.fagsystem,
                fagsakId = mottak.fagsakId,
                mottattVedtaksinstans = mottak.brukersKlageMottattVedtaksinstans!!,
                avsenderEnhetFoersteinstans = mottak.forrigeBehandlendeEnhet,
                previousSaksbehandlerident = mottak.forrigeSaksbehandlerident,
                mottattKlageinstans = mottak.sakMottattKaDato,
                tildeling = null,
                frist = mottak.generateFrist(),
                mottakId = mottak.id,
                saksdokumenter = dokumentService.createSaksdokumenterFromJournalpostIdList(mottak.mottakDokument.map { it.journalpostId }),
                kakaKvalitetsvurderingId = kakaApiGateway.createKvalitetsvurdering(kvalitetsvurderingVersion = kvalitetsvurderingVersion).kvalitetsvurderingId,
                kakaKvalitetsvurderingVersion = kvalitetsvurderingVersion,
                hjemler = mottak.mapToBehandlingHjemler(),
                kommentarFraFoersteinstans = mottak.kommentar,
                gosysOppgaveId = null,
                tilbakekreving = false,
                varsletBehandlingstid = null,
                forlengetBehandlingstidDraft = null,
            )
        )
        logger.debug("Created klagebehandling {} for mottak {}", klagebehandling.id, mottak.id)

        applicationEventPublisher.publishEvent(
            BehandlingChangedEvent(
                behandling = klagebehandling,
                changeList = listOfNotNull(
                    createChange(
                        saksbehandlerident = systembrukerIdent,
                        felt = BehandlingChangedEvent.Felt.KLAGEBEHANDLING_MOTTATT,
                        fraVerdi = null,
                        tilVerdi = "Opprettet",
                        behandlingId = klagebehandling.id,
                    )
                )
            )
        )
        applicationEventPublisher.publishEvent(
            BehandlingChangedEvent(
                behandling = klagebehandling,
                changeList = listOfNotNull(
                    createChange(
                        saksbehandlerident = systembrukerIdent,
                        felt = BehandlingChangedEvent.Felt.KLAGEBEHANDLING_OPPRETTET,
                        fraVerdi = null,
                        tilVerdi = "Opprettet",
                        behandlingId = klagebehandling.id,
                    )
                )
            )
        )

        klagebehandling.opprettetSendt = true

        return klagebehandling
    }

    private fun getKakaVersion(): Int {
        val kvalitetsvurderingVersion = if (LocalDate.now() >= kakaVersion2Date) {
            2
        } else {
            1
        }
        return kvalitetsvurderingVersion
    }
}