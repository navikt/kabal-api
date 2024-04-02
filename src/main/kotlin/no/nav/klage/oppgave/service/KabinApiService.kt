package no.nav.klage.oppgave.service

import no.nav.klage.dokument.service.DokumentUnderArbeidService
import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.api.mapper.BehandlingMapper
import no.nav.klage.oppgave.api.view.BehandlingDetaljerView
import no.nav.klage.oppgave.api.view.kabin.*
import no.nav.klage.oppgave.clients.klagefssproxy.KlageFssProxyClient
import no.nav.klage.oppgave.clients.klagefssproxy.domain.GetSakAppAccessInput
import no.nav.klage.oppgave.domain.klage.*
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
    private val klageFssProxyClient: KlageFssProxyClient,
    private val dokumentUnderArbeidService: DokumentUnderArbeidService,
    private val searchService: PartSearchService,
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
        return createdAnkeResponse(
            behandling = mottakService.createAnkeMottakAndBehandlingFromKabinInput(input = input),
            saksbehandlerIdent = input.saksbehandlerIdent,
            svarbrevInput = input.svarbrevInput,
        )
    }

    fun createAnkeFromCompleteKabinInput(input: CreateAnkeBasedOnCompleteKabinInput): CreatedAnkeResponse {
        return createdAnkeResponse(
            behandling = mottakService.createAnkeMottakFromCompleteKabinInput(input = input),
            saksbehandlerIdent = input.saksbehandlerIdent,
            svarbrevInput = input.svarbrevInput,
        )
    }

    private fun createdAnkeResponse(
        behandling: Behandling,
        saksbehandlerIdent: String?,
        svarbrevInput: SvarbrevInput?,
    ): CreatedAnkeResponse {
        if (saksbehandlerIdent != null) {
            behandlingService.setSaksbehandler(
                behandlingId = behandling.id,
                tildeltSaksbehandlerIdent = saksbehandlerIdent,
                enhetId = saksbehandlerService.getEnhetForSaksbehandler(
                    saksbehandlerIdent
                ).enhetId,
                fradelingReason = null,
                utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            )
        }

        //Create DokumentUnderArbeid from input based on svarbrevInput.
        if (svarbrevInput != null) {
            dokumentUnderArbeidService.createDokumentUnderArbeidFromSvarbrevInput(
                svarbrevInput = svarbrevInput,
                behandling = behandling,
            )
        }

        return CreatedAnkeResponse(behandlingId = behandling.id)
    }

    fun getCreatedAnkebehandlingStatus(
        behandlingId: UUID
    ): CreatedAnkebehandlingStatusForKabin {
        val ankebehandling = behandlingService.getBehandlingForReadWithoutCheckForAccess(behandlingId = behandlingId) as Ankebehandling
        val mottak = mottakService.getMottak(mottakId = ankebehandling.mottakId!!)

        return if (ankebehandling.sourceBehandlingId != null) {
            val sourceBehandling =
                behandlingService.getBehandlingAndCheckLeseTilgangForPerson(ankebehandling.sourceBehandlingId!!)
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
        return CreatedKlageResponse(behandlingId = mottakId)
    }

    fun getCreatedKlagebehandlingStatus(
        behandlingId: UUID
    ): CreatedKlagebehandlingStatusForKabin {
        val klagebehandling = behandlingService.getBehandlingForReadWithoutCheckForAccess(behandlingId = behandlingId) as Klagebehandling
        val mottak = mottakService.getMottak(mottakId = klagebehandling.mottakId)

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
            sakenGjelder = behandlingMapper.getSakenGjelderViewWithUtsendingskanal(behandling = ankebehandling)
                .toKabinPartView(),
            klager = behandlingMapper.getPartViewWithUtsendingskanal(
                partId = ankebehandling.klager.partId,
                behandling = ankebehandling
            ).toKabinPartView(),
            fullmektig = ankebehandling.klager.prosessfullmektig?.let {
                behandlingMapper.getPartViewWithUtsendingskanal(partId = it.partId, behandling = ankebehandling)
                    .toKabinPartView()
            },
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
            svarbrev = dokumentUnderArbeidService.getSvarbrevAsOpplastetDokumentUnderArbeidAsHoveddokument(behandlingId = ankebehandling.id)
                ?.let { document ->
                    CreatedAnkebehandlingStatusForKabin.Svarbrev(
                        dokumentUnderArbeidId = document.id,
                        title = document.name,
                        receivers = document.avsenderMottakerInfoSet.map { mottakerInfo ->
                            CreatedAnkebehandlingStatusForKabin.Svarbrev.Receiver(
                                id = mottakerInfo.identifikator,
                                name = searchService.searchPart(identifikator = mottakerInfo.identifikator).name,
                                address = mottakerInfo.address?.let { address ->
                                    BehandlingDetaljerView.Address(
                                        adresselinje1 = address.adresselinje1,
                                        adresselinje2 = address.adresselinje2,
                                        adresselinje3 = address.adresselinje3,
                                        landkode = address.landkode,
                                        postnummer = address.postnummer,
                                        poststed = address.poststed,
                                    )
                                },
                                localPrint = mottakerInfo.localPrint,
                                forceCentralPrint = mottakerInfo.forceCentralPrint,
                            )
                        }
                    )
                }
        )

    }

    private fun getCreatedKlagebehandlingStatusForKabin(
        klagebehandling: Klagebehandling,
        mottak: Mottak,
    ): CreatedKlagebehandlingStatusForKabin {
        return CreatedKlagebehandlingStatusForKabin(
            typeId = Type.KLAGE.id,
            ytelseId = klagebehandling.ytelse.id,
            sakenGjelder = behandlingMapper.getSakenGjelderViewWithUtsendingskanal(behandling = klagebehandling)
                .toKabinPartView(),
            klager = behandlingMapper.getPartViewWithUtsendingskanal(
                partId = klagebehandling.klager.partId,
                behandling = klagebehandling
            ).toKabinPartView(),
            fullmektig = klagebehandling.klager.prosessfullmektig?.let {
                behandlingMapper.getPartViewWithUtsendingskanal(partId = it.partId, behandling = klagebehandling)
                    .toKabinPartView()
            },
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
        val ankebehandlingerBasedOnThisBehandling =
            ankebehandlingService.getAnkebehandlingerBasedOnSourceBehandlingId(sourceBehandlingId = id)

        return Ankemulighet(
            behandlingId = id,
            ytelseId = ytelse.id,
            hjemmelIdList = hjemler.map { it.id },
            vedtakDate = avsluttetAvSaksbehandler!!,
            sakenGjelder = behandlingMapper.getSakenGjelderViewWithUtsendingskanal(behandling = this).toKabinPartView(),
            klager = behandlingMapper.getPartViewWithUtsendingskanal(partId = klager.partId, behandling = this)
                .toKabinPartView(),
            fullmektig = klager.prosessfullmektig?.let {
                behandlingMapper.getPartViewWithUtsendingskanal(
                    partId = it.partId,
                    behandling = this
                ).toKabinPartView()
            },
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