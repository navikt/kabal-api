package no.nav.klage.oppgave.domain.klage

import no.nav.klage.dokument.domain.dokumenterunderarbeid.Adresse
import no.nav.klage.kodeverk.FlowState
import no.nav.klage.kodeverk.FradelingReason
import no.nav.klage.kodeverk.Utfall
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
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
    ): BehandlingEndretEvent {
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

        val endringslogginnslag = mutableListOf<Endringslogginnslag>()

        endringslogg(
            saksbehandlerident = utfoerendeIdent,
            felt = Felt.TILDELT_TIDSPUNKT,
            fraVerdi = gammelVerdiTidspunkt?.format(DateTimeFormatter.ISO_LOCAL_DATE),
            tilVerdi = tidspunkt.format(DateTimeFormatter.ISO_LOCAL_DATE),
        )?.let { endringslogginnslag.add(it) }

        endringslogg(
            saksbehandlerident = utfoerendeIdent,
            felt = Felt.TILDELT_SAKSBEHANDLERIDENT,
            fraVerdi = gammelVerdiSaksbehandlerident,
            tilVerdi = nyVerdiSaksbehandlerident,
        )?.let { endringslogginnslag.add(it) }

        endringslogg(
            saksbehandlerident = utfoerendeIdent,
            felt = Felt.TILDELT_ENHET,
            fraVerdi = gammelVerdiEnhet,
            tilVerdi = nyVerdiEnhet,
        )
            ?.let { endringslogginnslag.add(it) }

        return BehandlingEndretEvent(behandling = this, endringslogginnslag = endringslogginnslag)
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
    ): BehandlingEndretEvent {
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

        val endringslogginnslag = mutableListOf<Endringslogginnslag>()

        endringslogg(
            saksbehandlerident = utfoerendeIdent,
            felt = Felt.MEDUNDERSKRIVER_FLOW_STATE_ID,
            fraVerdi = gammelVerdiMedunderskriverFlowState.id,
            tilVerdi = nyMedunderskriverFlowState.id,
        )?.let { endringslogginnslag.add(it) }

        return BehandlingEndretEvent(behandling = this, endringslogginnslag = endringslogginnslag)
    }

    fun Behandling.setMedunderskriverNavIdent(
        nyMedunderskriverNavIdent: String?,
        utfoerendeIdent: String,
        utfoerendeNavn: String
    ): BehandlingEndretEvent {
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

        val endringslogginnslag = mutableListOf<Endringslogginnslag>()

        endringslogg(
            saksbehandlerident = utfoerendeIdent,
            felt = Felt.MEDUNDERSKRIVERIDENT,
            fraVerdi = gammelVerdiMedunderskriverNavIdent,
            tilVerdi = nyMedunderskriverNavIdent,
        )?.let { endringslogginnslag.add(it) }

        return BehandlingEndretEvent(behandling = this, endringslogginnslag = endringslogginnslag)
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
    ): BehandlingEndretEvent {
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

        val endringslogginnslag = mutableListOf<Endringslogginnslag>()

        endringslogg(
            saksbehandlerident = utfoerendeIdent,
            felt = Felt.ROL_FLOW_STATE_ID,
            fraVerdi = oldValue.id,
            tilVerdi = rolFlowState.id,
        )?.let { endringslogginnslag.add(it) }

        return BehandlingEndretEvent(behandling = this, endringslogginnslag = endringslogginnslag)
    }

    fun Behandling.setROLReturnedDate(
        setNull: Boolean,
        utfoerendeIdent: String
    ): BehandlingEndretEvent {
        val oldValue = rolReturnedDate
        val now = LocalDateTime.now()

        rolReturnedDate = if (setNull) null else now
        modified = now

        val endringslogginnslag = mutableListOf<Endringslogginnslag>()

        endringslogg(
            saksbehandlerident = utfoerendeIdent,
            felt = Felt.ROL_RETURNED_TIDSPUNKT,
            fraVerdi = oldValue.toString(),
            tilVerdi = rolReturnedDate.toString(),
        )?.let { endringslogginnslag.add(it) }

        return BehandlingEndretEvent(behandling = this, endringslogginnslag = endringslogginnslag)
    }

    fun Behandling.setROLIdent(
        newROLIdent: String?,
        utfoerendeIdent: String,
        utfoerendeNavn: String,
    ): BehandlingEndretEvent {
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

        val endringslogginnslag = mutableListOf<Endringslogginnslag>()

        endringslogg(
            saksbehandlerident = utfoerendeIdent,
            felt = Felt.ROL_IDENT,
            fraVerdi = oldValue,
            tilVerdi = rolIdent,
        )?.let { endringslogginnslag.add(it) }

        return BehandlingEndretEvent(behandling = this, endringslogginnslag = endringslogginnslag)
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
    ): BehandlingEndretEvent {
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

        val endringslogginnslag = mutableListOf<Endringslogginnslag>()

        endringslogg(
            saksbehandlerident = utfoerendeIdent,
            felt = Felt.SATT_PAA_VENT,
            fraVerdi = gammelSattPaaVent.toString(),
            tilVerdi = nyVerdi.toString(),
        )?.let { endringslogginnslag.add(it) }

        return BehandlingEndretEvent(behandling = this, endringslogginnslag = endringslogginnslag)
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
    ): BehandlingEndretEvent {
        val gammelVerdi = mottattKlageinstans
        val tidspunkt = LocalDateTime.now()
        mottattKlageinstans = nyVerdi
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.MOTTATT_KLAGEINSTANS_TIDSPUNKT,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = nyVerdi.toString(),
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.setFrist(
        nyVerdi: LocalDate,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdi = frist
        val tidspunkt = LocalDateTime.now()
        frist = nyVerdi
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.FRIST_DATO,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = nyVerdi.toString(),
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.setGosysOppgaveId(
        nyVerdi: Long,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdi = gosysOppgaveId
        val tidspunkt = LocalDateTime.now()
        gosysOppgaveId = nyVerdi
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.GOSYSOPPGAVE_ID,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = nyVerdi.toString(),
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.setInnsendingshjemler(
        nyVerdi: Set<Hjemmel>,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdi = hjemler
        val tidspunkt = LocalDateTime.now()
        hjemler = nyVerdi
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.INNSENDINGSHJEMLER_ID_LIST,
                fraVerdi = gammelVerdi.joinToString { it.id },
                tilVerdi = nyVerdi.joinToString { it.id },
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.setFullmektig(
        partId: PartId?,
        address: Adresse?,
        name: String?,
        utfoerendeIdent: String,
        utfoerendeNavn: String,
    ): BehandlingEndretEvent {
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

        val endringslogg =
            endringslogg(
                saksbehandlerident = utfoerendeIdent,
                felt = Felt.FULLMEKTIG,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = partId.toString(),
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
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
    ): BehandlingEndretEvent {
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
        val endringslogg =
            endringslogg(
                saksbehandlerident = utfoerendeIdent,
                felt = Felt.KLAGER,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = klager.toString(),
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
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
    ): BehandlingEndretEvent {
        val gammelVerdi = registreringshjemler
        val tidspunkt = LocalDateTime.now()
        registreringshjemler = nyVerdi.toMutableSet()
        modified = tidspunkt
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.REGISTRERINGSHJEMLER_ID_LIST,
                fraVerdi = gammelVerdi.joinToString { it.id },
                tilVerdi = nyVerdi.joinToString { it.id },
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.setUtfall(
        nyVerdi: Utfall?,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdi = utfall
        val tidspunkt = LocalDateTime.now()
        utfall = nyVerdi
        modified = tidspunkt
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.UTFALL_ID,
                fraVerdi = gammelVerdi?.id,
                tilVerdi = utfall?.id,
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.setExtraUtfallSet(
        nyVerdi: Set<Utfall>,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdi = extraUtfallSet
        val tidspunkt = LocalDateTime.now()
        extraUtfallSet = nyVerdi
        modified = tidspunkt
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.EXTRA_UTFALL_SET,
                fraVerdi = gammelVerdi.joinToString { it.id },
                tilVerdi = extraUtfallSet.joinToString { it.id },
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.setTilbakekreving(
        nyVerdi: Boolean,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdi = tilbakekreving
        val tidspunkt = LocalDateTime.now()
        tilbakekreving = nyVerdi
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.TILBAKEKREVING,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = tilbakekreving.toString(),
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.setAvsluttetAvSaksbehandler(
        saksbehandlerident: String,
        saksbehandlernavn: String,
    ): BehandlingEndretEvent {
        val gammelVerdi = ferdigstilling?.avsluttetAvSaksbehandler
        val tidspunkt = LocalDateTime.now()

        ferdigstilling = Ferdigstilling(
            avsluttet = null,
            avsluttetAvSaksbehandler = tidspunkt,
            navIdent = saksbehandlerident,
            navn = saksbehandlernavn,
        )

        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.AVSLUTTET_AV_SAKSBEHANDLER_TIDSPUNKT,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = tidspunkt.toString(),
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.setGosysOppgaveUpdate(
        tildeltEnhet: String,
        mappeId: Long?,
        kommentar: String,
        saksbehandlerident: String,
    ): BehandlingEndretEvent {
        val tidspunkt = LocalDateTime.now()

        gosysOppgaveUpdate = GosysOppgaveUpdate(
            oppgaveUpdateTildeltEnhetsnummer = tildeltEnhet,
            oppgaveUpdateMappeId = mappeId,
            oppgaveUpdateKommentar = kommentar
        )

        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.GOSYS_OPPGAVE_UPDATE,
                fraVerdi = null,
                tilVerdi = gosysOppgaveUpdate.toString(),
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.setIgnoreGosysOppgave(
        ignoreGosysOppgaveNewValue: Boolean,
        saksbehandlerident: String,
    ): BehandlingEndretEvent {
        val tidspunkt = LocalDateTime.now()
        val gammelVerdi = ignoreGosysOppgave
        ignoreGosysOppgave = ignoreGosysOppgaveNewValue
        modified = tidspunkt
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.IGNORE_GOSYS_OPPGAVE,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = ignoreGosysOppgave.toString(),
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.setAvsluttet(
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdi = ferdigstilling?.avsluttet
        val tidspunkt = LocalDateTime.now()
        ferdigstilling!!.avsluttet = tidspunkt
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.AVSLUTTET_TIDSPUNKT,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = tidspunkt.toString(),
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.addSaksdokument(
        saksdokument: Saksdokument,
        saksbehandlerident: String
    ): BehandlingEndretEvent? {
        if (saksdokumenter.none { it.journalpostId == saksdokument.journalpostId && it.dokumentInfoId == saksdokument.dokumentInfoId }) {
            val tidspunkt = LocalDateTime.now()
            saksdokumenter.add(saksdokument)
            modified = tidspunkt
            val endringslogg = Endringslogginnslag.endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.SAKSDOKUMENT,
                fraVerdi = null,
                tilVerdi = saksdokument.toString(),
                behandlingId = id,
            )
            return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
        }
        return null
    }

    fun Behandling.addSaksdokumenter(
        saksdokumentList: List<Saksdokument>,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val existingSaksdokumenter = saksdokumenter.joinToString()
        val tidspunkt = LocalDateTime.now()
        saksdokumenter.addAll(saksdokumentList)
        modified = tidspunkt
        val endringslogg = Endringslogginnslag.endringslogg(
            saksbehandlerident = saksbehandlerident,
            felt = Felt.SAKSDOKUMENT,
            fraVerdi = existingSaksdokumenter,
            tilVerdi = saksdokumenter.joinToString(),
            behandlingId = id,
        )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.removeSaksdokument(
        saksdokument: Saksdokument,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val tidspunkt = LocalDateTime.now()
        saksdokumenter.removeIf { it.id == saksdokument.id }
        modified = tidspunkt
        val endringslogg = Endringslogginnslag.endringslogg(
            saksbehandlerident = saksbehandlerident,
            felt = Felt.SAKSDOKUMENT,
            fraVerdi = saksdokument.toString(),
            tilVerdi = null,
            behandlingId = id,
        )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.removeSaksdokumenter(
        saksdokumentList: List<Saksdokument>,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val existingSaksdokumenter = saksdokumenter.joinToString()
        val tidspunkt = LocalDateTime.now()
        saksdokumenter.removeAll(saksdokumentList)
        modified = tidspunkt
        val endringslogg = Endringslogginnslag.endringslogg(
            saksbehandlerident = saksbehandlerident,
            felt = Felt.SAKSDOKUMENT,
            fraVerdi = existingSaksdokumenter,
            tilVerdi = saksdokumenter.joinToString(),
            behandlingId = id,
        )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.clearSaksdokumenter(
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val tidspunkt = LocalDateTime.now()
        val oldValue = saksdokumenter.joinToString()
        saksdokumenter.clear()
        modified = tidspunkt
        val endringslogg = Endringslogginnslag.endringslogg(
            saksbehandlerident = saksbehandlerident,
            felt = Felt.SAKSDOKUMENT,
            fraVerdi = oldValue,
            tilVerdi = null,
            behandlingId = id,
        )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.setFeilregistrering(
        feilregistrering: Feilregistrering,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val tidspunkt = LocalDateTime.now()
        modified = tidspunkt
        this.feilregistrering = feilregistrering
        val endringslogg = Endringslogginnslag.endringslogg(
            saksbehandlerident = saksbehandlerident,
            felt = Felt.FEILREGISTRERING,
            fraVerdi = null,
            tilVerdi = feilregistrering.toString(),
            behandlingId = id,
        )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    private fun Behandling.endringslogg(
        saksbehandlerident: String,
        felt: Felt,
        fraVerdi: String?,
        tilVerdi: String?,
    ): Endringslogginnslag? {
        return Endringslogginnslag.endringslogg(
            saksbehandlerident = saksbehandlerident,
            felt = felt,
            fraVerdi = fraVerdi,
            tilVerdi = tilVerdi,
            behandlingId = this.id,
        )
    }

}