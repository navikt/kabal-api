package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.domain.klage.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class HistoryService(
    private val partSearchService: PartSearchService,
    private val saksbehandlerService: SaksbehandlerService,
    @Value("\${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
) {

    fun createTildelingHistory(
        tildelingHistorikkSet: Set<TildelingHistorikk>,
        behandlingCreated: LocalDateTime,
        originalHjemmelIdList: String?,
    ): List<WithPrevious<TildelingEvent>> {
        val historySorted = if (tildelingHistorikkSet.size == 1) {
            listOf(
                TildelingHistorikk(
                    saksbehandlerident = null,
                    enhet = null,
                    tidspunkt = behandlingCreated,
                    fradelingReason = null,
                    hjemmelIdList = originalHjemmelIdList,
                    utfoerendeIdent = null,
                    utfoerendeNavn = null,
                )
            ) + tildelingHistorikkSet.sortedBy { it.tidspunkt }
        } else tildelingHistorikkSet.sortedBy { it.tidspunkt }

        return historySorted.zipWithNext().map { (previous, current) ->
            val previousEvent = HistoryEvent(
                type = HistoryEventType.TILDELING,
                timestamp = previous.tidspunkt,
                actor = getSaksbehandlerView(previous.utfoerendeIdent, previous.utfoerendeNavn),
                event = TildelingEvent(
                    saksbehandler = previous.saksbehandlerident.navIdentToSaksbehandlerView(),
                    fradelingReasonId = previous.fradelingReason?.id,
                    hjemmelIdList = if (previous.hjemmelIdList != null) {
                        previous.hjemmelIdList.split(",")
                    } else null,
                ),
            )

            HistoryEventWithPrevious(
                type = HistoryEventType.TILDELING,
                timestamp = current.tidspunkt,
                actor = getSaksbehandlerView(current.utfoerendeIdent, current.utfoerendeNavn),
                event = TildelingEvent(
                    saksbehandler = current.saksbehandlerident.navIdentToSaksbehandlerView(),
                    fradelingReasonId = current.fradelingReason?.id,
                    hjemmelIdList = if (current.hjemmelIdList != null) {
                        current.hjemmelIdList.split(",")
                    } else null,
                ),
                previous = previousEvent,
            )
        }

    }

    fun createMedunderskriverHistory(
        medunderskriverHistorikkSet: Set<MedunderskriverHistorikk>,
        behandlingCreated: LocalDateTime,
    ): List<WithPrevious<MedunderskriverEvent>> {
        val historySorted = if (medunderskriverHistorikkSet.size == 1) {
            listOf(
                MedunderskriverHistorikk(
                    saksbehandlerident = null,
                    tidspunkt = behandlingCreated,
                    utfoerendeIdent = null,
                    utfoerendeNavn = null,
                    flowState = null,
                )
            ) + medunderskriverHistorikkSet.sortedBy { it.tidspunkt }
        } else medunderskriverHistorikkSet.sortedBy { it.tidspunkt }

        return historySorted.zipWithNext()
            .map { (previous, current) ->
                val previousEvent: HistoryEvent<MedunderskriverEvent> = HistoryEvent(
                    type = HistoryEventType.MEDUNDERSKRIVER,
                    timestamp = previous.tidspunkt,
                    actor = getSaksbehandlerView(previous.utfoerendeIdent, previous.utfoerendeNavn),
                    event = MedunderskriverEvent(
                        medunderskriver = previous.saksbehandlerident.navIdentToSaksbehandlerView(),
                        flow = previous.flowState
                    )
                )

                HistoryEventWithPrevious(
                    type = HistoryEventType.MEDUNDERSKRIVER,
                    timestamp = current.tidspunkt,
                    actor = getSaksbehandlerView(current.utfoerendeIdent, current.utfoerendeNavn),
                    event = MedunderskriverEvent(
                        medunderskriver = current.saksbehandlerident.navIdentToSaksbehandlerView(),
                        flow = current.flowState
                    ),
                    previous = previousEvent,
                )
            }

    }

    fun createRolHistory(
        rolHistorikk: Set<RolHistorikk>,
    ): List<WithPrevious<RolEvent>> {
        val historySorted = rolHistorikk.sortedBy { it.tidspunkt }

        return historySorted.zipWithNext()
            .map { (previous, current) ->
                val previousEvent: HistoryEvent<RolEvent> = HistoryEvent(
                    type = HistoryEventType.ROL,
                    timestamp = previous.tidspunkt,
                    actor = getSaksbehandlerView(previous.utfoerendeIdent, previous.utfoerendeNavn),
                    event = RolEvent(
                        rol = previous.rolIdent.navIdentToSaksbehandlerView(),
                        flow = previous.flowState
                    )
                )

                HistoryEventWithPrevious(
                    type = HistoryEventType.ROL,
                    timestamp = current.tidspunkt,
                    actor = getSaksbehandlerView(current.utfoerendeIdent, current.utfoerendeNavn),
                    event = RolEvent(
                        rol = current.rolIdent.navIdentToSaksbehandlerView(),
                        flow = current.flowState
                    ),
                    previous = previousEvent,
                )
            }

    }

    fun createFeilregistrertHistory(
        feilregistrering: Feilregistrering?,
        behandlingCreated: LocalDateTime
    ): List<WithPrevious<FeilregistrertEvent>> {
        return if (feilregistrering != null) {
            return listOf(
                HistoryEventWithPrevious(
                    type = HistoryEventType.FEILREGISTRERT,
                    timestamp = feilregistrering.registered,
                    actor = getSaksbehandlerView(feilregistrering.navIdent, feilregistrering.navn),
                    event = FeilregistrertEvent(
                        reason = feilregistrering.reason,
                    ),
                    previous = HistoryEvent(
                        type = HistoryEventType.FEILREGISTRERT,
                        timestamp = behandlingCreated,
                        actor = null,
                        event = FeilregistrertEvent(
                            reason = feilregistrering.reason,
                        )
                    ),
                )
            )
        } else emptyList()
    }

    fun createFullmektigHistory(
        fullmektigHistorikk: Set<FullmektigHistorikk>,
    ): List<WithPrevious<FullmektigEvent>> {
        val historySorted = fullmektigHistorikk.sortedBy { it.tidspunkt }

        return historySorted.zipWithNext()
            .map { (previous, current) ->
                val previousEvent: HistoryEvent<FullmektigEvent> = HistoryEvent(
                    type = HistoryEventType.FULLMEKTIG,
                    timestamp = previous.tidspunkt,
                    actor = getSaksbehandlerView(previous.utfoerendeIdent, previous.utfoerendeNavn),
                    event = FullmektigEvent(
                        part = previous.partId?.let {
                            Part(
                                id = it.value,
                                name = partSearchService.searchPart(it.value).name ?: "Manglende navn. Noe er feil.",
                                type = if (it.type == PartIdType.PERSON) {
                                    BehandlingDetaljerView.IdType.FNR
                                } else BehandlingDetaljerView.IdType.ORGNR
                            )
                        }
                    )
                )

                HistoryEventWithPrevious(
                    type = HistoryEventType.FULLMEKTIG,
                    timestamp = current.tidspunkt,
                    actor = getSaksbehandlerView(current.utfoerendeIdent, current.utfoerendeNavn),
                    event = FullmektigEvent(
                        part = current.partId?.let {
                            Part(
                                id = it.value,
                                name = partSearchService.searchPart(it.value).name ?: "Manglende navn. Noe er feil.",
                                type = if (it.type == PartIdType.PERSON) {
                                    BehandlingDetaljerView.IdType.FNR
                                } else BehandlingDetaljerView.IdType.ORGNR
                            )
                        }
                    ),
                    previous = previousEvent,
                )
            }
    }

    fun createKlagerHistory(
        klagerHistorikk: Set<KlagerHistorikk>,
    ): List<WithPrevious<KlagerEvent>> {
        val historySorted = klagerHistorikk.sortedBy { it.tidspunkt }

        return historySorted.zipWithNext()
            .map { (previous, current) ->
                val previousEvent = HistoryEvent(
                    type = HistoryEventType.KLAGER,
                    timestamp = previous.tidspunkt,
                    actor = getSaksbehandlerView(previous.utfoerendeIdent, previous.utfoerendeNavn),
                    event = KlagerEvent(
                        part = previous.partId.let {
                            Part(
                                id = it.value,
                                name = partSearchService.searchPart(it.value).name ?: "Manglende navn. Noe er feil.",
                                type = if (it.type == PartIdType.PERSON) {
                                    BehandlingDetaljerView.IdType.FNR
                                } else BehandlingDetaljerView.IdType.ORGNR
                            )
                        }
                    )
                )

                HistoryEventWithPrevious(
                    type = HistoryEventType.KLAGER,
                    timestamp = current.tidspunkt,
                    actor = getSaksbehandlerView(current.utfoerendeIdent, current.utfoerendeNavn),
                    event = KlagerEvent(
                        part = current.partId.let {
                            Part(
                                id = it.value,
                                name = partSearchService.searchPart(it.value).name ?: "Manglende navn. Noe er feil.",
                                type = if (it.type == PartIdType.PERSON) {
                                    BehandlingDetaljerView.IdType.FNR
                                } else BehandlingDetaljerView.IdType.ORGNR
                            )
                        }
                    ),
                    previous = previousEvent,
                )
            }
    }

    fun createSattPaaVentHistory(
        sattPaaVentHistorikk: Set<SattPaaVentHistorikk>,
    ): List<WithPrevious<SattPaaVentEvent>> {
        val historySorted = sattPaaVentHistorikk.sortedBy { it.tidspunkt }

        return historySorted.zipWithNext()
            .map { (previous, current) ->
                val previousEvent: HistoryEvent<SattPaaVentEvent> = HistoryEvent(
                    type = HistoryEventType.SATT_PAA_VENT,
                    timestamp = previous.tidspunkt,
                    actor = getSaksbehandlerView(previous.utfoerendeIdent, previous.utfoerendeNavn),
                    event = previous.sattPaaVent?.let {
                        SattPaaVentEvent(
                            from = it.from,
                            to = it.to,
                            reason = it.reason,
                        )
                    }
                )

                HistoryEventWithPrevious(
                    type = HistoryEventType.SATT_PAA_VENT,
                    timestamp = current.tidspunkt,
                    actor = getSaksbehandlerView(current.utfoerendeIdent, current.utfoerendeNavn),
                    event = current.sattPaaVent?.let {
                        SattPaaVentEvent(
                            from = it.from,
                            to = it.to,
                            reason = it.reason,
                        )
                    },
                    previous = previousEvent,
                )
            }
    }

    fun createFerdigstiltHistory(behandling: Behandling): List<WithPrevious<FerdigstiltEvent>> {
        return if (behandling.ferdigstilling != null) {
            return listOf(
                HistoryEventWithPrevious(
                    type = HistoryEventType.FERDIGSTILT,
                    timestamp = behandling.ferdigstilling!!.avsluttetAvSaksbehandler,
                    actor = getSaksbehandlerView(
                        utfoerendeIdent = behandling.ferdigstilling!!.navIdent,
                        utfoerendeNavn = behandling.ferdigstilling!!.navn
                    ),
                    event = FerdigstiltEvent(
                        avsluttetAvSaksbehandler = behandling.ferdigstilling!!.avsluttetAvSaksbehandler,
                    ),
                    previous = HistoryEvent(
                        type = HistoryEventType.FERDIGSTILT,
                        timestamp = behandling.created,
                        actor = null,
                        event = null,
                    ),
                )
            )
        } else emptyList()
    }

    private fun String?.navIdentToSaksbehandlerView(): SaksbehandlerView? {
        return if (this != null) {
            SaksbehandlerView(
                navIdent = this,
                navn = if (this == systembrukerIdent) {
                    this
                } else saksbehandlerService.getNameForIdentDefaultIfNull(
                    navIdent = this
                ),
            )
        } else null
    }

    fun createVarsletBehandlingstidHistory(
        varsletBehandlingstidHistorikk: Set<VarsletBehandlingstidHistorikk>,
        behandlingCreated: LocalDateTime
    ): List<WithPrevious<VarsletBehandlingstidEvent>> {
        val historySorted = if (varsletBehandlingstidHistorikk.size == 1) {
            listOf(
                VarsletBehandlingstidHistorikk(
                    tidspunkt = behandlingCreated,
                    utfoerendeIdent = null,
                    utfoerendeNavn = null,
                    mottakerList = listOf(),
                    varsletFrist = null,
                    varsletBehandlingstidUnits = null,
                    varsletBehandlingstidUnitType = null,
                )
            ) + varsletBehandlingstidHistorikk.sortedBy { it.tidspunkt }
        } else varsletBehandlingstidHistorikk.sortedBy { it.tidspunkt }

        return historySorted.zipWithNext()
            .map { (previous, current) ->
                val previousEvent: HistoryEvent<VarsletBehandlingstidEvent> = HistoryEvent(
                    type = HistoryEventType.VARSLET_BEHANDLINGSTID,
                    timestamp = previous.tidspunkt,
                    actor = getSaksbehandlerView(previous.utfoerendeIdent, previous.utfoerendeNavn),
                    event = VarsletBehandlingstidEvent(
                        mottakere = previous.mottakerList.map {
                            Part(
                                id = it.partId.value,
                                name = partSearchService.searchPart(it.partId.value).name
                                    ?: "Manglende navn. Noe er feil.",
                                type = if (it.partId.type == PartIdType.PERSON) {
                                    BehandlingDetaljerView.IdType.FNR
                                } else BehandlingDetaljerView.IdType.ORGNR
                            )
                        },
                        varsletBehandlingstidUnits = previous.varsletBehandlingstidUnits,
                        varsletBehandlingstidUnitTypeId = previous.varsletBehandlingstidUnitType?.id,
                        varsletFrist = previous.varsletFrist,
                    )
                )

                HistoryEventWithPrevious(
                    type = HistoryEventType.VARSLET_BEHANDLINGSTID,
                    timestamp = current.tidspunkt,
                    actor = getSaksbehandlerView(current.utfoerendeIdent, current.utfoerendeNavn),
                    event = VarsletBehandlingstidEvent(
                        mottakere = current.mottakerList.map {
                            Part(
                                id = it.partId.value,
                                name = partSearchService.searchPart(it.partId.value).name
                                    ?: "Manglende navn. Noe er feil.",
                                type = if (it.partId.type == PartIdType.PERSON) {
                                    BehandlingDetaljerView.IdType.FNR
                                } else BehandlingDetaljerView.IdType.ORGNR
                            )
                        },
                        varsletBehandlingstidUnits = current.varsletBehandlingstidUnits,
                        varsletBehandlingstidUnitTypeId = current.varsletBehandlingstidUnitType?.id,
                        varsletFrist = current.varsletFrist,
                    ),
                    previous = previousEvent,
                )
            }
    }

    private fun getSaksbehandlerView(utfoerendeIdent: String?, utfoerendeNavn: String?): SaksbehandlerView? {
        if (utfoerendeIdent == null) return null

        val navn = utfoerendeNavn
            ?: saksbehandlerService.getNameForIdentDefaultIfNull(
                navIdent = utfoerendeIdent,
            )

        return SaksbehandlerView(
            navIdent = utfoerendeIdent,
            navn = navn,
        )
    }
}
