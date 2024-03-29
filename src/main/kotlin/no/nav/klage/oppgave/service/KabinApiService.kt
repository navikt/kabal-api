package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.api.mapper.BehandlingMapper
import no.nav.klage.oppgave.api.view.kabin.*
import no.nav.klage.oppgave.clients.klagefssproxy.KlageFssProxyClient
import no.nav.klage.oppgave.clients.klagefssproxy.domain.GetSakAppAccessInput
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.exceptions.BehandlingNotFoundException
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*

@Service
class KabinApiService(
    private val behandlingMapper: BehandlingMapper,
    private val dokumentService: DokumentService,
    private val saksbehandlerService: SaksbehandlerService,
    private val mottakService: MottakService,
    private val ankebehandlingService: AnkebehandlingService,
    private val ankeITrygderettenbehandlingService: AnkeITrygderettenbehandlingService,
    private val behandlingService: BehandlingService,
    private val klagebehandlingService: KlagebehandlingService,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val klageFssProxyClient: KlageFssProxyClient
) {

    fun getCombinedAnkemuligheter(partIdValue: String): List<Ankemulighet> {
        behandlingService.checkLesetilgangForPerson(partIdValue)
        val ankemuligheterFromKlagebehandlinger =
            klagebehandlingService.getCompletedKlagebehandlingerByPartIdValue(partIdValue = partIdValue)
                .map { it.toAnkemulighet() }
        val ankemuligheterFromAnkebehandlinger =
            ankebehandlingService.getCompletedAnkebehandlingerByPartIdValue(partIdValue = partIdValue)
                .map { it.toAnkemulighet() }
        val ankemuligheterFromAnkeITrygderettenbehandlinger =
            ankeITrygderettenbehandlingService.getCompletedAnkeITrygderettenbehandlingerByPartIdValue(partIdValue = partIdValue)
                .map { it.toAnkemulighet() }

        return ankemuligheterFromKlagebehandlinger + ankemuligheterFromAnkebehandlinger + ankemuligheterFromAnkeITrygderettenbehandlinger
    }

    fun createAnke(input: CreateAnkeBasedOnKabinInput): CreatedAnkeResponse {
        val mottakId = mottakService.createAnkeMottakFromKabinInput(input = input)
        if (input.saksbehandlerIdent != null) {
            val ankebehandling = ankebehandlingService.getAnkebehandlingFromMottakId(mottakId)
            behandlingService.setSaksbehandler(
                behandlingId = ankebehandling!!.id,
                tildeltSaksbehandlerIdent = input.saksbehandlerIdent,
                enhetId = saksbehandlerService.getEnhetForSaksbehandler(
                    input.saksbehandlerIdent
                ).enhetId,
                fradelingReason = null,
                utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            )
        }
        return CreatedAnkeResponse(mottakId = mottakId)
    }

    fun createAnkeFromCompleteKabinInput(input: CreateAnkeBasedOnCompleteKabinInput): CreatedAnkeResponse {
        val mottakId = mottakService.createAnkeMottakFromCompleteKabinInput(input = input)
        if (input.saksbehandlerIdent != null) {
            val ankebehandling = ankebehandlingService.getAnkebehandlingFromMottakId(mottakId)
            behandlingService.setSaksbehandler(
                behandlingId = ankebehandling!!.id,
                tildeltSaksbehandlerIdent = input.saksbehandlerIdent,
                enhetId = saksbehandlerService.getEnhetForSaksbehandler(
                    input.saksbehandlerIdent
                ).enhetId,
                fradelingReason = null,
                utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            )
        }
        return CreatedAnkeResponse(mottakId = mottakId)
    }

    fun getCreatedAnkebehandlingStatus(
        mottakId: UUID
    ): CreatedAnkebehandlingStatusForKabin {
        val mottak =
            mottakService.getMottak(mottakId = mottakId) ?: throw RuntimeException("mottak not found for id $mottakId")

        val ankebehandling = ankebehandlingService.getAnkebehandlingFromMottakId(mottakId)
            ?: throw BehandlingNotFoundException("anke not found")

        return if (ankebehandling.sourceBehandlingId != null) {
            val sourceBehandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(ankebehandling.sourceBehandlingId!!)
            getCreatedAnkebehandlingStatusForKabin(
                ankebehandling = ankebehandling,
                mottak = mottak,
                vedtakDate = sourceBehandling.avsluttetAvSaksbehandler!!,
            )
        } else {
            val klageInInfotrygd = klageFssProxyClient.getSakWithAppAccess(
                sakId = ankebehandling.kildeReferanse,
                input = GetSakAppAccessInput(saksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent())
            )
            getCreatedAnkebehandlingStatusForKabin(
                ankebehandling = ankebehandling,
                mottak = mottak,
                vedtakDate = klageInInfotrygd.vedtaksdato.atStartOfDay(),
            )
        }
    }

    fun createKlage(
        input: CreateKlageBasedOnKabinInput
    ): CreatedKlageResponse {
        val mottakId = mottakService.createKlageMottakFromKabinInput(klageInput = input)
        if (input.saksbehandlerIdent != null) {
            val ankebehandling = klagebehandlingService.getKlagebehandlingFromMottakId(mottakId)
            behandlingService.setSaksbehandler(
                behandlingId = ankebehandling!!.id,
                tildeltSaksbehandlerIdent = input.saksbehandlerIdent,
                enhetId = saksbehandlerService.getEnhetForSaksbehandler(
                    input.saksbehandlerIdent
                ).enhetId,
                fradelingReason = null,
                utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            )
        }
        return CreatedKlageResponse(mottakId = mottakId)
    }

    fun getCreatedKlagebehandlingStatus(
        mottakId: UUID
    ): CreatedKlagebehandlingStatusForKabin {
        val mottak =
            mottakService.getMottak(mottakId = mottakId) ?: throw RuntimeException("mottak not found for id $mottakId")
        val klagebehandling = klagebehandlingService.getKlagebehandlingFromMottakId(mottakId)
            ?: throw BehandlingNotFoundException("klage not found")

        return getCreatedKlagebehandlingStatusForKabin(
            klagebehandling = klagebehandling,
            mottak = mottak
        )
    }

    private fun getCreatedAnkebehandlingStatusForKabin(
        ankebehandling: Ankebehandling,
        mottak: Mottak,
        vedtakDate: LocalDateTime,
    ): CreatedAnkebehandlingStatusForKabin {
        return CreatedAnkebehandlingStatusForKabin(
            typeId = Type.ANKE.id,
            ytelseId = ankebehandling.ytelse.id,
            vedtakDate = vedtakDate,
            sakenGjelder = behandlingMapper.getSakenGjelderView(ankebehandling.sakenGjelder).toKabinPartView(),
            klager = behandlingMapper.getPartView(ankebehandling.klager.partId).toKabinPartView(),
            fullmektig = ankebehandling.klager.prosessfullmektig?.let { behandlingMapper.getPartView(it.partId).toKabinPartView() },
            mottattNav = ankebehandling.mottattKlageinstans.toLocalDate(),
            frist = ankebehandling.frist!!,
            fagsakId = ankebehandling.fagsakId,
            fagsystemId = ankebehandling.fagsystem.id,
            journalpost = dokumentService.getDokumentReferanse(
                journalpostId = mottak.mottakDokument.find { it.type == MottakDokumentType.BRUKERS_ANKE }!!.journalpostId,
                behandling = ankebehandling
            ),
            tildeltSaksbehandler = ankebehandling.tildeling?.saksbehandlerident?.let {
                TildeltSaksbehandler(
                    navIdent = it,
                    navn = saksbehandlerService.getNameForIdentDefaultIfNull(it),
                )
            },
        )
    }

    private fun getCreatedKlagebehandlingStatusForKabin(
        klagebehandling: Klagebehandling,
        mottak: Mottak,
    ): CreatedKlagebehandlingStatusForKabin {
        return CreatedKlagebehandlingStatusForKabin(
            typeId = Type.KLAGE.id,
            ytelseId = klagebehandling.ytelse.id,
            sakenGjelder = behandlingMapper.getSakenGjelderView(klagebehandling.sakenGjelder).toKabinPartView(),
            klager = behandlingMapper.getPartView(klagebehandling.klager.partId).toKabinPartView(),
            fullmektig = klagebehandling.klager.prosessfullmektig?.let { behandlingMapper.getPartView(it.partId).toKabinPartView() },
            mottattVedtaksinstans = klagebehandling.mottattVedtaksinstans,
            mottattKlageinstans = klagebehandling.mottattKlageinstans.toLocalDate(),
            frist = klagebehandling.frist!!,
            fagsakId = klagebehandling.fagsakId,
            fagsystemId = klagebehandling.fagsystem.id,
            journalpost = dokumentService.getDokumentReferanse(
                journalpostId = mottak.mottakDokument.find { it.type == MottakDokumentType.BRUKERS_KLAGE }!!.journalpostId,
                behandling = klagebehandling
            ),
            kildereferanse = mottak.kildeReferanse,
            tildeltSaksbehandler = klagebehandling.tildeling?.saksbehandlerident?.let {
                TildeltSaksbehandler(
                    navIdent = it,
                    navn = saksbehandlerService.getNameForIdentDefaultIfNull(it),
                )
            },
        )
    }

    private fun Behandling.toAnkemulighet(): Ankemulighet {
        val ankebehandlingerBasedOnThisBehandling = ankebehandlingService.getAnkebehandlingerBasedOnSourceBehandlingId(sourceBehandlingId = id)

        return Ankemulighet(
            behandlingId = id,
            ytelseId = ytelse.id,
            hjemmelIdList = hjemler.map { it.id },
            vedtakDate = avsluttetAvSaksbehandler!!,
            sakenGjelder = behandlingMapper.getSakenGjelderView(sakenGjelder).toKabinPartView(),
            klager = behandlingMapper.getPartView(klager.partId).toKabinPartView(),
            fullmektig = klager.prosessfullmektig?.let { behandlingMapper.getPartView(it.partId).toKabinPartView() },
            fagsakId = fagsakId,
            fagsystem = fagsystem,
            fagsystemId = fagsystem.id,
            klageBehandlendeEnhet = tildeling!!.enhet!!,
            tildeltSaksbehandlerIdent = tildeling!!.saksbehandlerident!!,
            tildeltSaksbehandlerNavn = saksbehandlerService.getNameForIdentDefaultIfNull(tildeling!!.saksbehandlerident!!),
            typeId = type.id,
            sourceOfExistingAnkebehandling = ankebehandlingerBasedOnThisBehandling.map {
                ExistingAnkebehandling(
                    id = it.id,
                    created = it.created,
                    completed = it.avsluttetAvSaksbehandler,
                )
            },
        )
    }
}