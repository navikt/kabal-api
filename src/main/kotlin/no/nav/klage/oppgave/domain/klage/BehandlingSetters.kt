package no.nav.klage.oppgave.domain.klage

import no.nav.klage.dokument.domain.dokumenterunderarbeid.Adresse
import no.nav.klage.kodeverk.FlowState
import no.nav.klage.kodeverk.FradelingReason
import no.nav.klage.kodeverk.Utfall
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent.Change.Companion.createChange
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

object BehandlingSetters {

    fun Behandling.setTildeling(
        nyVerdiSaksbehandlerident: String?,
        nyVerdiEnhet: String?,
        fradelingReason: FradelingReason?,
        utfoerendeIdent: String,
        utfoerendeNavn: String,
        fradelingWithChangedHjemmelIdList: String?,
    ): BehandlingChangedEvent {
        if (!(nyVerdiSaksbehandlerident == null && nyVerdiEnhet == null) &&
            !(nyVerdiSaksbehandlerident != null && nyVerdiEnhet != null)
        ) {
            error("saksbehandler and enhet must both be set (or null)")
        }

        val gammelVerdiSaksbehandlerident = tildeling?.saksbehandlerident
        val gammelVerdiEnhet = tildeling?.enhet
        val gammelVerdiTidspunkt = tildeling?.tidspunkt
        val tidspunkt = LocalDateTime.now()

        //record initial state
        if (tildelingHistorikk.isEmpty()) {
            recordTildelingHistory(
                tidspunkt = gammelVerdiTidspunkt ?: created,
                utfoerendeIdent = null,
                utfoerendeNavn = null,
                fradelingReason = null,
                hjemmelIdList = hjemler.joinToString(",") { it.id },
            )
        }

        tildeling = if (nyVerdiSaksbehandlerident == null) {
            null
        } else {
            Tildeling(nyVerdiSaksbehandlerident, nyVerdiEnhet, tidspunkt)
        }
        modified = tidspunkt

        recordTildelingHistory(
            tidspunkt = tidspunkt,
            utfoerendeIdent = utfoerendeIdent,
            utfoerendeNavn = utfoerendeNavn,
            fradelingReason = fradelingReason,
            hjemmelIdList = if (tildeling == null) {
                fradelingWithChangedHjemmelIdList
            } else hjemler.joinToString(",") { it.id },
        )

        val changeList = mutableListOf<BehandlingChangedEvent.Change>()

        createChange(
            behandlingId = this.id,
            saksbehandlerident = utfoerendeIdent,
            felt = BehandlingChangedEvent.Felt.TILDELT_TIDSPUNKT,
            fraVerdi = gammelVerdiTidspunkt?.format(DateTimeFormatter.ISO_LOCAL_DATE),
            tilVerdi = tidspunkt.format(DateTimeFormatter.ISO_LOCAL_DATE),
        )?.let { changeList.add(it) }

        createChange(
            behandlingId = this.id,
            saksbehandlerident = utfoerendeIdent,
            felt = BehandlingChangedEvent.Felt.TILDELT_SAKSBEHANDLERIDENT,
            fraVerdi = gammelVerdiSaksbehandlerident,
            tilVerdi = nyVerdiSaksbehandlerident,
        )?.let { changeList.add(it) }

        createChange(
            behandlingId = this.id,
            saksbehandlerident = utfoerendeIdent,
            felt = BehandlingChangedEvent.Felt.TILDELT_ENHET,
            fraVerdi = gammelVerdiEnhet,
            tilVerdi = nyVerdiEnhet,
        )
            ?.let { changeList.add(it) }

        return BehandlingChangedEvent(behandling = this, changeList = changeList)
    }

    private fun Behandling.recordTildelingHistory(
        tidspunkt: LocalDateTime,
        utfoerendeIdent: String?,
        utfoerendeNavn: String?,
        fradelingReason: FradelingReason?,
        hjemmelIdList: String?,
    ) {
        tildelingHistorikk.add(
            TildelingHistorikk(
                saksbehandlerident = tildeling?.saksbehandlerident,
                enhet = tildeling?.enhet,
                tidspunkt = tidspunkt,
                utfoerendeIdent = utfoerendeIdent,
                utfoerendeNavn = utfoerendeNavn,
                fradelingReason = fradelingReason,
                hjemmelIdList = hjemmelIdList,
            )
        )
    }

    fun Behandling.setMedunderskriverFlowState(
        nyMedunderskriverFlowState: FlowState,
        utfoerendeIdent: String,
        utfoerendeNavn: String,
    ): BehandlingChangedEvent {
        val gammelVerdiMedunderskriverFlowState = medunderskriverFlowState
        val tidspunkt = LocalDateTime.now()

        //record initial history
        if (medunderskriverHistorikk.isEmpty()) {
            recordMedunderskriverHistory(
                tidspunkt = created,
                utfoerendeIdent = null,
                utfoerendeNavn = null,
            )
        }

        medunderskriverFlowState = nyMedunderskriverFlowState
        modified = tidspunkt

        recordMedunderskriverHistory(
            tidspunkt = tidspunkt,
            utfoerendeIdent = utfoerendeIdent,
            utfoerendeNavn = utfoerendeNavn,
        )

        val changeList = mutableListOf<BehandlingChangedEvent.Change>()

        createChange(
            behandlingId = this.id,
            saksbehandlerident = utfoerendeIdent,
            felt = BehandlingChangedEvent.Felt.MEDUNDERSKRIVER_FLOW_STATE_ID,
            fraVerdi = gammelVerdiMedunderskriverFlowState.id,
            tilVerdi = nyMedunderskriverFlowState.id,
        )?.let { changeList.add(it) }

        return BehandlingChangedEvent(behandling = this, changeList = changeList)
    }

    fun Behandling.setMedunderskriverNavIdent(
        nyMedunderskriverNavIdent: String?,
        utfoerendeIdent: String,
        utfoerendeNavn: String
    ): BehandlingChangedEvent {
        val gammelVerdiMedunderskriverNavIdent = medunderskriver?.saksbehandlerident
        val tidspunkt = LocalDateTime.now()

        //record initial history
        if (medunderskriverHistorikk.isEmpty()) {
            recordMedunderskriverHistory(
                tidspunkt = medunderskriver?.tidspunkt ?: created,
                utfoerendeIdent = null,
                utfoerendeNavn = null,
            )
        }

        medunderskriver = MedunderskriverTildeling(
            saksbehandlerident = nyMedunderskriverNavIdent,
            tidspunkt = tidspunkt,
        )

        if (medunderskriverFlowState == FlowState.RETURNED || nyMedunderskriverNavIdent == null) {
            medunderskriverFlowState = FlowState.NOT_SENT
        }

        recordMedunderskriverHistory(
            tidspunkt = tidspunkt,
            utfoerendeIdent = utfoerendeIdent,
            utfoerendeNavn = utfoerendeNavn,
        )

        modified = tidspunkt

        val changeList = mutableListOf<BehandlingChangedEvent.Change>()

        createChange(
            behandlingId = this.id,
            saksbehandlerident = utfoerendeIdent,
            felt = BehandlingChangedEvent.Felt.MEDUNDERSKRIVERIDENT,
            fraVerdi = gammelVerdiMedunderskriverNavIdent,
            tilVerdi = nyMedunderskriverNavIdent,
        )?.let { changeList.add(it) }

        return BehandlingChangedEvent(behandling = this, changeList = changeList)
    }

    private fun Behandling.recordMedunderskriverHistory(
        tidspunkt: LocalDateTime,
        utfoerendeIdent: String?,
        utfoerendeNavn: String?,
    ) {
        medunderskriverHistorikk.add(
            MedunderskriverHistorikk(
                saksbehandlerident = medunderskriver?.saksbehandlerident,
                tidspunkt = tidspunkt,
                utfoerendeIdent = utfoerendeIdent,
                utfoerendeNavn = utfoerendeNavn,
                flowState = medunderskriverFlowState,
            )
        )
    }

    fun Behandling.setROLFlowState(
        newROLFlowStateState: FlowState,
        utfoerendeIdent: String,
        utfoerendeNavn: String,
    ): BehandlingChangedEvent {
        val oldValue = rolFlowState
        val now = LocalDateTime.now()

        //record initial state
        if (rolHistorikk.isEmpty()) {
            recordRolHistory(
                tidspunkt = created,
                utfoerendeIdent = null,
                utfoerendeNavn = null,
            )
        }

        rolFlowState = newROLFlowStateState
        modified = now

        recordRolHistory(
            tidspunkt = now,
            utfoerendeIdent = utfoerendeIdent,
            utfoerendeNavn = utfoerendeNavn,
        )

        val changeList = mutableListOf<BehandlingChangedEvent.Change>()

        createChange(
            behandlingId = this.id,
            saksbehandlerident = utfoerendeIdent,
            felt = BehandlingChangedEvent.Felt.ROL_FLOW_STATE_ID,
            fraVerdi = oldValue.id,
            tilVerdi = rolFlowState.id,
        )?.let { changeList.add(it) }

        return BehandlingChangedEvent(behandling = this, changeList = changeList)
    }

    fun Behandling.setROLReturnedDate(
        setNull: Boolean,
        utfoerendeIdent: String
    ): BehandlingChangedEvent {
        val oldValue = rolReturnedDate
        val now = LocalDateTime.now()

        rolReturnedDate = if (setNull) null else now
        modified = now

        val changeList = mutableListOf<BehandlingChangedEvent.Change>()

        createChange(
            behandlingId = this.id,
            saksbehandlerident = utfoerendeIdent,
            felt = BehandlingChangedEvent.Felt.ROL_RETURNED_TIDSPUNKT,
            fraVerdi = oldValue.toString(),
            tilVerdi = rolReturnedDate.toString(),
        )?.let { changeList.add(it) }

        return BehandlingChangedEvent(behandling = this, changeList = changeList)
    }

    fun Behandling.setROLIdent(
        newROLIdent: String?,
        utfoerendeIdent: String,
        utfoerendeNavn: String,
    ): BehandlingChangedEvent {
        val oldValue = rolIdent
        val now = LocalDateTime.now()

        //record initial state
        if (rolHistorikk.isEmpty()) {
            recordRolHistory(
                tidspunkt = created,
                utfoerendeIdent = null,
                utfoerendeNavn = null,
            )
        }

        rolIdent = newROLIdent
        modified = now

        if (rolFlowState == FlowState.RETURNED) {
            rolFlowState = FlowState.NOT_SENT
        }

        recordRolHistory(
            tidspunkt = now,
            utfoerendeIdent = utfoerendeIdent,
            utfoerendeNavn = utfoerendeNavn,
        )

        val changeList = mutableListOf<BehandlingChangedEvent.Change>()

        createChange(
            behandlingId = this.id,
            saksbehandlerident = utfoerendeIdent,
            felt = BehandlingChangedEvent.Felt.ROL_IDENT,
            fraVerdi = oldValue,
            tilVerdi = rolIdent,
        )?.let { changeList.add(it) }

        return BehandlingChangedEvent(behandling = this, changeList = changeList)
    }

    private fun Behandling.recordRolHistory(
        tidspunkt: LocalDateTime,
        utfoerendeIdent: String?,
        utfoerendeNavn: String?
    ) {
        rolHistorikk.add(
            RolHistorikk(
                rolIdent = rolIdent,
                tidspunkt = tidspunkt,
                utfoerendeIdent = utfoerendeIdent,
                utfoerendeNavn = utfoerendeNavn,
                flowState = rolFlowState,
            )
        )
    }

    fun Behandling.setSattPaaVent(
        nyVerdi: SattPaaVent?,
        utfoerendeIdent: String,
        utfoerendeNavn: String,
    ): BehandlingChangedEvent {
        val gammelSattPaaVent = sattPaaVent
        val tidspunkt = LocalDateTime.now()

        //record initial state
        if (sattPaaVentHistorikk.isEmpty()) {
            recordSattPaaVentHistory(
                tidspunkt = sattPaaVent?.from?.atStartOfDay() ?: created,
                utfoerendeIdent = null,
                utfoerendeNavn = null,
            )
        }

        sattPaaVent = nyVerdi
        modified = tidspunkt

        recordSattPaaVentHistory(
            tidspunkt = tidspunkt,
            utfoerendeIdent = utfoerendeIdent,
            utfoerendeNavn = utfoerendeNavn,
        )

        val changeList = mutableListOf<BehandlingChangedEvent.Change>()

        createChange(
            behandlingId = this.id,
            saksbehandlerident = utfoerendeIdent,
            felt = BehandlingChangedEvent.Felt.SATT_PAA_VENT,
            fraVerdi = gammelSattPaaVent.toString(),
            tilVerdi = nyVerdi.toString(),
        )?.let { changeList.add(it) }

        return BehandlingChangedEvent(behandling = this, changeList = changeList)
    }

    private fun Behandling.recordSattPaaVentHistory(
        tidspunkt: LocalDateTime,
        utfoerendeIdent: String?,
        utfoerendeNavn: String?,
    ) {
        sattPaaVentHistorikk.add(
            SattPaaVentHistorikk(
                sattPaaVent = sattPaaVent,
                tidspunkt = tidspunkt,
                utfoerendeIdent = utfoerendeIdent,
                utfoerendeNavn = utfoerendeNavn,
            )
        )
    }

    fun Behandling.setMottattKlageinstans(
        nyVerdi: LocalDateTime,
        saksbehandlerident: String
    ): BehandlingChangedEvent {
        val gammelVerdi = mottattKlageinstans
        val tidspunkt = LocalDateTime.now()
        mottattKlageinstans = nyVerdi
        modified = tidspunkt
        val change =
            createChange(
                behandlingId = this.id,
                saksbehandlerident = saksbehandlerident,
                felt = BehandlingChangedEvent.Felt.MOTTATT_KLAGEINSTANS_TIDSPUNKT,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = nyVerdi.toString(),
            )
        return BehandlingChangedEvent(behandling = this, changeList = listOfNotNull(change))
    }

    fun Behandling.setFrist(
        nyVerdi: LocalDate,
        saksbehandlerident: String
    ): BehandlingChangedEvent {
        val gammelVerdi = frist
        val tidspunkt = LocalDateTime.now()
        frist = nyVerdi
        modified = tidspunkt
        val change =
            createChange(
                behandlingId = this.id,
                saksbehandlerident = saksbehandlerident,
                felt = BehandlingChangedEvent.Felt.FRIST_DATO,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = nyVerdi.toString(),
            )
        return BehandlingChangedEvent(behandling = this, changeList = listOfNotNull(change))
    }

    fun Behandling.setGosysOppgaveId(
        nyVerdi: Long,
        saksbehandlerident: String
    ): BehandlingChangedEvent {
        val gammelVerdi = gosysOppgaveId
        val tidspunkt = LocalDateTime.now()
        gosysOppgaveId = nyVerdi
        modified = tidspunkt
        val change =
            createChange(
                behandlingId = this.id,
                saksbehandlerident = saksbehandlerident,
                felt = BehandlingChangedEvent.Felt.GOSYSOPPGAVE_ID,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = nyVerdi.toString(),
            )
        return BehandlingChangedEvent(behandling = this, changeList = listOfNotNull(change))
    }

    fun Behandling.setInnsendingshjemler(
        nyVerdi: Set<Hjemmel>,
        saksbehandlerident: String
    ): BehandlingChangedEvent {
        val gammelVerdi = hjemler
        val tidspunkt = LocalDateTime.now()
        hjemler = nyVerdi
        modified = tidspunkt
        val change =
            createChange(
                behandlingId = this.id,
                saksbehandlerident = saksbehandlerident,
                felt = BehandlingChangedEvent.Felt.INNSENDINGSHJEMLER_ID_LIST,
                fraVerdi = gammelVerdi.joinToString { it.id },
                tilVerdi = nyVerdi.joinToString { it.id },
            )
        return BehandlingChangedEvent(behandling = this, changeList = listOfNotNull(change))
    }

    fun Behandling.setFullmektig(
        partId: PartId?,
        address: Adresse?,
        name: String?,
        utfoerendeIdent: String,
        utfoerendeNavn: String,
    ): BehandlingChangedEvent {
        val gammelVerdi = prosessfullmektig
        val tidspunkt = LocalDateTime.now()

        //record initial state
        if (fullmektigHistorikk.isEmpty()) {
            recordFullmektigHistory(
                tidspunkt = created,
                utfoerendeIdent = null,
                utfoerendeNavn = null,
            )
        }

        prosessfullmektig = if (partId == null && address == null && name == null) {
            null
        } else {
            if (partId?.value == klager.partId.value) {
                Prosessfullmektig(
                    id = klager.id,
                    partId = klager.partId,
                    address = null,
                    navn = null,
                )
            } else Prosessfullmektig(
                id = UUID.randomUUID(),
                partId = partId,
                address = address?.let {
                    Adresse(
                        adresselinje1 = it.adresselinje1,
                        adresselinje2 = it.adresselinje2,
                        adresselinje3 = it.adresselinje3,
                        postnummer = it.postnummer,
                        poststed = it.poststed,
                        landkode = it.landkode,
                    )
                },
                navn = name,
            )
        }
        modified = tidspunkt

        recordFullmektigHistory(
            tidspunkt = tidspunkt,
            utfoerendeIdent = utfoerendeIdent,
            utfoerendeNavn = utfoerendeNavn,
        )

        val change =
            createChange(
                behandlingId = this.id,
                saksbehandlerident = utfoerendeIdent,
                felt = BehandlingChangedEvent.Felt.FULLMEKTIG,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = partId.toString(),
            )
        return BehandlingChangedEvent(behandling = this, changeList = listOfNotNull(change))
    }

    private fun Behandling.recordFullmektigHistory(
        tidspunkt: LocalDateTime,
        utfoerendeIdent: String?,
        utfoerendeNavn: String?
    ) {
        fullmektigHistorikk.add(
            FullmektigHistorikk(
                partId = prosessfullmektig?.partId,
                tidspunkt = tidspunkt,
                utfoerendeIdent = utfoerendeIdent,
                utfoerendeNavn = utfoerendeNavn,
                name = prosessfullmektig?.navn
            )
        )
    }

    fun Behandling.setKlager(
        nyVerdi: PartId,
        utfoerendeIdent: String,
        utfoerendeNavn: String,
    ): BehandlingChangedEvent {
        val gammelVerdi = klager
        val tidspunkt = LocalDateTime.now()

        //record initial state
        if (klagerHistorikk.isEmpty()) {
            recordKlagerHistory(
                tidspunkt = created,
                utfoerendeIdent = null,
                utfoerendeNavn = null,
            )
        }

        when (nyVerdi.value) {
            prosessfullmektig?.partId?.value -> {
                klager.id = prosessfullmektig!!.id
                klager.partId = prosessfullmektig!!.partId!!
            }

            sakenGjelder.partId.value -> {
                klager.id = sakenGjelder.id
                klager.partId = sakenGjelder.partId
            }

            else -> {
                klager.partId = nyVerdi
                klager.id = UUID.randomUUID()
            }
        }

        recordKlagerHistory(
            tidspunkt = tidspunkt,
            utfoerendeIdent = utfoerendeIdent,
            utfoerendeNavn = utfoerendeNavn,
        )

        modified = tidspunkt
        val change =
            createChange(
                behandlingId = this.id,
                saksbehandlerident = utfoerendeIdent,
                felt = BehandlingChangedEvent.Felt.KLAGER,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = klager.toString(),
            )
        return BehandlingChangedEvent(behandling = this, changeList = listOfNotNull(change))
    }

    private fun Behandling.recordKlagerHistory(
        tidspunkt: LocalDateTime,
        utfoerendeIdent: String?,
        utfoerendeNavn: String?,
    ) {
        klagerHistorikk.add(
            KlagerHistorikk(
                partId = klager.partId,
                tidspunkt = tidspunkt,
                utfoerendeIdent = utfoerendeIdent,
                utfoerendeNavn = utfoerendeNavn,
            )
        )
    }

    fun Behandling.setRegistreringshjemler(
        nyVerdi: Set<Registreringshjemmel>,
        saksbehandlerident: String
    ): BehandlingChangedEvent {
        val gammelVerdi = registreringshjemler
        val tidspunkt = LocalDateTime.now()
        registreringshjemler = nyVerdi.toMutableSet()
        modified = tidspunkt
        modified = tidspunkt
        val change =
            createChange(
                behandlingId = this.id,
                saksbehandlerident = saksbehandlerident,
                felt = BehandlingChangedEvent.Felt.REGISTRERINGSHJEMLER_ID_LIST,
                fraVerdi = gammelVerdi.joinToString { it.id },
                tilVerdi = nyVerdi.joinToString { it.id },
            )
        return BehandlingChangedEvent(behandling = this, changeList = listOfNotNull(change))
    }

    fun Behandling.setUtfall(
        nyVerdi: Utfall?,
        saksbehandlerident: String
    ): BehandlingChangedEvent {
        val gammelVerdi = utfall
        val tidspunkt = LocalDateTime.now()
        utfall = nyVerdi
        modified = tidspunkt
        modified = tidspunkt
        val change =
            createChange(
                behandlingId = this.id,
                saksbehandlerident = saksbehandlerident,
                felt = BehandlingChangedEvent.Felt.UTFALL_ID,
                fraVerdi = gammelVerdi?.id,
                tilVerdi = utfall?.id,
            )
        return BehandlingChangedEvent(behandling = this, changeList = listOfNotNull(change))
    }

    fun Behandling.setExtraUtfallSet(
        nyVerdi: Set<Utfall>,
        saksbehandlerident: String
    ): BehandlingChangedEvent {
        val gammelVerdi = extraUtfallSet
        val tidspunkt = LocalDateTime.now()
        extraUtfallSet = nyVerdi
        modified = tidspunkt
        modified = tidspunkt
        val change =
            createChange(
                behandlingId = this.id,
                saksbehandlerident = saksbehandlerident,
                felt = BehandlingChangedEvent.Felt.EXTRA_UTFALL_SET,
                fraVerdi = gammelVerdi.joinToString { it.id },
                tilVerdi = extraUtfallSet.joinToString { it.id },
            )
        return BehandlingChangedEvent(behandling = this, changeList = listOfNotNull(change))
    }

    fun Behandling.setTilbakekreving(
        nyVerdi: Boolean,
        saksbehandlerident: String
    ): BehandlingChangedEvent {
        val gammelVerdi = tilbakekreving
        val tidspunkt = LocalDateTime.now()
        tilbakekreving = nyVerdi
        modified = tidspunkt
        val change =
            createChange(
                behandlingId = this.id,
                saksbehandlerident = saksbehandlerident,
                felt = BehandlingChangedEvent.Felt.TILBAKEKREVING,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = tilbakekreving.toString(),
            )
        return BehandlingChangedEvent(behandling = this, changeList = listOfNotNull(change))
    }

    fun Behandling.setAvsluttetAvSaksbehandler(
        saksbehandlerident: String,
        saksbehandlernavn: String,
    ): BehandlingChangedEvent {
        val gammelVerdi = ferdigstilling?.avsluttetAvSaksbehandler
        val tidspunkt = LocalDateTime.now()

        ferdigstilling = Ferdigstilling(
            avsluttet = null,
            avsluttetAvSaksbehandler = tidspunkt,
            navIdent = saksbehandlerident,
            navn = saksbehandlernavn,
        )

        modified = tidspunkt
        val change =
            createChange(
                behandlingId = this.id,
                saksbehandlerident = saksbehandlerident,
                felt = BehandlingChangedEvent.Felt.AVSLUTTET_AV_SAKSBEHANDLER_TIDSPUNKT,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = tidspunkt.toString(),
            )
        return BehandlingChangedEvent(behandling = this, changeList = listOfNotNull(change))
    }

    fun Behandling.setGosysOppgaveUpdate(
        tildeltEnhet: String,
        mappeId: Long?,
        kommentar: String,
        saksbehandlerident: String,
    ): BehandlingChangedEvent {
        val tidspunkt = LocalDateTime.now()

        gosysOppgaveUpdate = GosysOppgaveUpdate(
            oppgaveUpdateTildeltEnhetsnummer = tildeltEnhet,
            oppgaveUpdateMappeId = mappeId,
            oppgaveUpdateKommentar = kommentar
        )

        modified = tidspunkt
        val change =
            createChange(
                behandlingId = this.id,
                saksbehandlerident = saksbehandlerident,
                felt = BehandlingChangedEvent.Felt.GOSYS_OPPGAVE_UPDATE,
                fraVerdi = null,
                tilVerdi = gosysOppgaveUpdate.toString(),
            )
        return BehandlingChangedEvent(behandling = this, changeList = listOfNotNull(change))
    }

    fun Behandling.setIgnoreGosysOppgave(
        ignoreGosysOppgaveNewValue: Boolean,
        saksbehandlerident: String,
    ): BehandlingChangedEvent {
        val tidspunkt = LocalDateTime.now()
        val gammelVerdi = ignoreGosysOppgave
        ignoreGosysOppgave = ignoreGosysOppgaveNewValue
        modified = tidspunkt
        modified = tidspunkt
        val change =
            createChange(
                behandlingId = this.id,
                saksbehandlerident = saksbehandlerident,
                felt = BehandlingChangedEvent.Felt.IGNORE_GOSYS_OPPGAVE,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = ignoreGosysOppgave.toString(),
            )
        return BehandlingChangedEvent(behandling = this, changeList = listOfNotNull(change))
    }

    fun Behandling.setAvsluttet(
        saksbehandlerident: String
    ): BehandlingChangedEvent {
        val gammelVerdi = ferdigstilling?.avsluttet
        val tidspunkt = LocalDateTime.now()
        ferdigstilling!!.avsluttet = tidspunkt
        modified = tidspunkt
        val change =
            createChange(
                behandlingId = this.id,
                saksbehandlerident = saksbehandlerident,
                felt = BehandlingChangedEvent.Felt.AVSLUTTET_TIDSPUNKT,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = tidspunkt.toString(),
            )
        return BehandlingChangedEvent(behandling = this, changeList = listOfNotNull(change))
    }

    fun Behandling.addSaksdokument(
        saksdokument: Saksdokument,
        saksbehandlerident: String
    ): BehandlingChangedEvent? {
        if (saksdokumenter.none { it.journalpostId == saksdokument.journalpostId && it.dokumentInfoId == saksdokument.dokumentInfoId }) {
            val tidspunkt = LocalDateTime.now()
            saksdokumenter.add(saksdokument)
            modified = tidspunkt
            val change = createChange(
                saksbehandlerident = saksbehandlerident,
                felt = BehandlingChangedEvent.Felt.SAKSDOKUMENT,
                fraVerdi = null,
                tilVerdi = saksdokument.toString(),
                behandlingId = id,
            )
            return BehandlingChangedEvent(behandling = this, changeList = listOfNotNull(change))
        }
        return null
    }

    fun Behandling.addSaksdokumenter(
        saksdokumentList: List<Saksdokument>,
        saksbehandlerident: String
    ): BehandlingChangedEvent {
        val existingSaksdokumenter = saksdokumenter.joinToString()
        val tidspunkt = LocalDateTime.now()
        saksdokumenter.addAll(saksdokumentList)
        modified = tidspunkt
        val change = createChange(
            saksbehandlerident = saksbehandlerident,
            felt = BehandlingChangedEvent.Felt.SAKSDOKUMENT,
            fraVerdi = existingSaksdokumenter,
            tilVerdi = saksdokumenter.joinToString(),
            behandlingId = id,
        )
        return BehandlingChangedEvent(behandling = this, changeList = listOfNotNull(change))
    }

    fun Behandling.removeSaksdokument(
        saksdokument: Saksdokument,
        saksbehandlerident: String
    ): BehandlingChangedEvent {
        val tidspunkt = LocalDateTime.now()
        saksdokumenter.removeIf { it.id == saksdokument.id }
        modified = tidspunkt
        val change = createChange(
            saksbehandlerident = saksbehandlerident,
            felt = BehandlingChangedEvent.Felt.SAKSDOKUMENT,
            fraVerdi = saksdokument.toString(),
            tilVerdi = null,
            behandlingId = id,
        )
        return BehandlingChangedEvent(behandling = this, changeList = listOfNotNull(change))
    }

    fun Behandling.removeSaksdokumenter(
        saksdokumentListForRemoval: List<Saksdokument>,
        saksbehandlerident: String
    ): BehandlingChangedEvent {
        val existingSaksdokumenter = saksdokumenter.joinToString()
        val tidspunkt = LocalDateTime.now()
        saksdokumenter.removeIf { saksdokumentListForRemoval.any { saksdokumentForRemoval -> saksdokumentForRemoval.journalpostId == it.journalpostId && saksdokumentForRemoval.dokumentInfoId == it.dokumentInfoId } }

        modified = tidspunkt
        val change = createChange(
            saksbehandlerident = saksbehandlerident,
            felt = BehandlingChangedEvent.Felt.SAKSDOKUMENT,
            fraVerdi = existingSaksdokumenter,
            tilVerdi = saksdokumenter.joinToString(),
            behandlingId = id,
        )
        return BehandlingChangedEvent(behandling = this, changeList = listOfNotNull(change))
    }

    fun Behandling.clearSaksdokumenter(
        saksbehandlerident: String
    ): BehandlingChangedEvent {
        val tidspunkt = LocalDateTime.now()
        val oldValue = saksdokumenter.joinToString()
        saksdokumenter.clear()
        modified = tidspunkt
        val change = createChange(
            saksbehandlerident = saksbehandlerident,
            felt = BehandlingChangedEvent.Felt.SAKSDOKUMENT,
            fraVerdi = oldValue,
            tilVerdi = null,
            behandlingId = id,
        )
        return BehandlingChangedEvent(behandling = this, changeList = listOfNotNull(change))
    }

    fun Behandling.setFeilregistrering(
        feilregistrering: Feilregistrering,
        saksbehandlerident: String
    ): BehandlingChangedEvent {
        val tidspunkt = LocalDateTime.now()
        modified = tidspunkt
        this.feilregistrering = feilregistrering
        val change = createChange(
            saksbehandlerident = saksbehandlerident,
            felt = BehandlingChangedEvent.Felt.FEILREGISTRERING,
            fraVerdi = null,
            tilVerdi = feilregistrering.toString(),
            behandlingId = id,
        )
        return BehandlingChangedEvent(behandling = this, changeList = listOfNotNull(change))
    }
}