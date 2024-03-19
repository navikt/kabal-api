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
                    utfoerendeIdent = null

                )
            ) + tildelingHistorikkSet.sortedBy { it.tidspunkt }
        } else tildelingHistorikkSet.sortedBy { it.tidspunkt }

        return historySorted.zipWithNext().map { (previous, current) ->
            val previousEvent = HistoryEvent(
                type = HistoryEventType.TILDELING,
                timestamp = previous.tidspunkt,
                actor = previous.utfoerendeIdent.toSaksbehandlerView(),
                event = TildelingEvent(
                    saksbehandler = previous.saksbehandlerident.toSaksbehandlerView(),
                    fradelingReasonId = previous.fradelingReason?.id,
                    hjemmelIdList = if (previous.hjemmelIdList != null) {
                        previous.hjemmelIdList.split(",")
                    } else null,
                ),
            )

            HistoryEventWithPrevious(
                type = HistoryEventType.TILDELING,
                timestamp = current.tidspunkt,
                actor = current.utfoerendeIdent.toSaksbehandlerView(),
                event = TildelingEvent(
                    saksbehandler = current.saksbehandlerident.toSaksbehandlerView(),
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
                    flowState = null,
                )
            ) + medunderskriverHistorikkSet.sortedBy { it.tidspunkt }
        } else medunderskriverHistorikkSet.sortedBy { it.tidspunkt }

        return historySorted.zipWithNext()
            .map { (previous, current) ->
                val previousEvent: HistoryEvent<MedunderskriverEvent> = HistoryEvent(
                    type = HistoryEventType.MEDUNDERSKRIVER,
                    timestamp = previous.tidspunkt,
                    actor = previous.utfoerendeIdent.toSaksbehandlerView(),
                    event = MedunderskriverEvent(
                        medunderskriver = previous.saksbehandlerident.toSaksbehandlerView(),
                        flow = previous.flowState
                    )
                )

                HistoryEventWithPrevious(
                    type = HistoryEventType.MEDUNDERSKRIVER,
                    timestamp = current.tidspunkt,
                    actor = current.utfoerendeIdent.toSaksbehandlerView(),
                    event = MedunderskriverEvent(
                        medunderskriver = current.saksbehandlerident.toSaksbehandlerView(),
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
                    actor = previous.utfoerendeIdent.toSaksbehandlerView(),
                    event = RolEvent(
                        rol = previous.rolIdent.toSaksbehandlerView(),
                        flow = previous.flowState
                    )
                )

                HistoryEventWithPrevious(
                    type = HistoryEventType.ROL,
                    timestamp = current.tidspunkt,
                    actor = current.utfoerendeIdent.toSaksbehandlerView(),
                    event = RolEvent(
                        rol = current.rolIdent.toSaksbehandlerView(),
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
                    actor = feilregistrering.navIdent.toSaksbehandlerView(),
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
                    actor = previous.utfoerendeIdent.toSaksbehandlerView(),
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
                    actor = current.utfoerendeIdent.toSaksbehandlerView(),
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
                    actor = previous.utfoerendeIdent.toSaksbehandlerView(),
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
                    actor = current.utfoerendeIdent.toSaksbehandlerView(),
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
                    actor = previous.utfoerendeIdent.toSaksbehandlerView(),
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
                    actor = current.utfoerendeIdent.toSaksbehandlerView(),
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
        return if (behandling.avsluttetAvSaksbehandler != null) {
            return listOf(
                HistoryEventWithPrevious(
                    type = HistoryEventType.FERDIGSTILT,
                    timestamp = behandling.avsluttetAvSaksbehandler!!,
                    actor = behandling.tildeling!!.saksbehandlerident.toSaksbehandlerView(),
                    event = FerdigstiltEvent(
                        avsluttetAvSaksbehandler = behandling.avsluttetAvSaksbehandler!!,
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

    private fun String?.toSaksbehandlerView(): SaksbehandlerView? {
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
}
