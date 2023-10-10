package no.nav.klage.oppgave.domain.klage

import no.nav.klage.kodeverk.FlowState
import no.nav.klage.kodeverk.Utfall
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object BehandlingSetters {

    fun Behandling.setTildeling(
        nyVerdiSaksbehandlerident: String?,
        nyVerdiEnhet: String?,
        saksbehandlerident: String
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
        if (tildeling != null) {
            tildelingHistorikk.add(TildelingHistorikk(tildeling = tildeling!!.copy()))
        }
        tildeling = if (nyVerdiSaksbehandlerident == null) {
            null
        } else {
            Tildeling(nyVerdiSaksbehandlerident, nyVerdiEnhet, tidspunkt)
        }
        modified = tidspunkt

        val endringslogginnslag = mutableListOf<Endringslogginnslag>()

        endringslogg(
            saksbehandlerident = saksbehandlerident,
            felt = Felt.TILDELT_TIDSPUNKT,
            fraVerdi = gammelVerdiTidspunkt?.format(DateTimeFormatter.ISO_LOCAL_DATE),
            tilVerdi = tidspunkt.format(DateTimeFormatter.ISO_LOCAL_DATE),
            tidspunkt = tidspunkt
        )?.let { endringslogginnslag.add(it) }

        endringslogg(
            saksbehandlerident = saksbehandlerident,
            felt = Felt.TILDELT_SAKSBEHANDLERIDENT,
            fraVerdi = gammelVerdiSaksbehandlerident,
            tilVerdi = nyVerdiSaksbehandlerident,
            tidspunkt = tidspunkt
        )?.let { endringslogginnslag.add(it) }

        endringslogg(
            saksbehandlerident = saksbehandlerident,
            felt = Felt.TILDELT_ENHET,
            fraVerdi = gammelVerdiEnhet,
            tilVerdi = nyVerdiEnhet,
            tidspunkt = tidspunkt
        )
            ?.let { endringslogginnslag.add(it) }

        return BehandlingEndretEvent(behandling = this, endringslogginnslag = endringslogginnslag)
    }

    fun Behandling.setMedunderskriverFlowState(
        nyMedunderskriverFlowState: FlowState,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdiMedunderskriverFlowState = medunderskriverFlowState
        val tidspunkt = LocalDateTime.now()

        medunderskriverFlowState = nyMedunderskriverFlowState
        modified = tidspunkt

        val endringslogginnslag = mutableListOf<Endringslogginnslag>()

        endringslogg(
            saksbehandlerident = saksbehandlerident,
            felt = Felt.MEDUNDERSKRIVER_FLOW_STATE_ID,
            fraVerdi = gammelVerdiMedunderskriverFlowState.id,
            tilVerdi = nyMedunderskriverFlowState.id,
            tidspunkt = tidspunkt
        )?.let { endringslogginnslag.add(it) }

        return BehandlingEndretEvent(behandling = this, endringslogginnslag = endringslogginnslag)
    }

    fun Behandling.setMedunderskriverNavIdent(
        nyMedunderskriverNavIdent: String?,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdiMedunderskriverNavIdent = medunderskriver?.saksbehandlerident
        val tidspunkt = LocalDateTime.now()

        medunderskriver = MedunderskriverTildeling(
            saksbehandlerident = nyMedunderskriverNavIdent,
            tidspunkt = tidspunkt,
        )

        if (medunderskriverFlowState == FlowState.RETURNED || nyMedunderskriverNavIdent == null) {
            medunderskriverFlowState = FlowState.NOT_SENT
        }

        modified = tidspunkt

        val endringslogginnslag = mutableListOf<Endringslogginnslag>()

        endringslogg(
            saksbehandlerident = saksbehandlerident,
            felt = Felt.MEDUNDERSKRIVERIDENT,
            fraVerdi = gammelVerdiMedunderskriverNavIdent,
            tilVerdi = nyMedunderskriverNavIdent,
            tidspunkt = tidspunkt
        )?.let { endringslogginnslag.add(it) }

        return BehandlingEndretEvent(behandling = this, endringslogginnslag = endringslogginnslag)
    }

    fun Behandling.setROLFlowState(
        newROLFlowStateState: FlowState,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val oldValue = rolFlowState
        val now = LocalDateTime.now()

        rolFlowState = newROLFlowStateState
        modified = now

        val endringslogginnslag = mutableListOf<Endringslogginnslag>()

        endringslogg(
            saksbehandlerident = saksbehandlerident,
            felt = Felt.ROL_FLOW_STATE_ID,
            fraVerdi = oldValue.id,
            tilVerdi = rolFlowState.id,
            tidspunkt = now
        )?.let { endringslogginnslag.add(it) }

        return BehandlingEndretEvent(behandling = this, endringslogginnslag = endringslogginnslag)
    }

    fun Behandling.setROLReturnedDate(
        setNull: Boolean,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val oldValue = rolReturnedDate
        val now = LocalDateTime.now()

        rolReturnedDate = if (setNull) null else now
        modified = now

        val endringslogginnslag = mutableListOf<Endringslogginnslag>()

        endringslogg(
            saksbehandlerident = saksbehandlerident,
            felt = Felt.ROL_RETURNED_TIDSPUNKT,
            fraVerdi = oldValue.toString(),
            tilVerdi = rolReturnedDate.toString(),
            tidspunkt = now
        )?.let { endringslogginnslag.add(it) }

        return BehandlingEndretEvent(behandling = this, endringslogginnslag = endringslogginnslag)
    }

    fun Behandling.setROLIdent(
        newROLIdent: String?,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val oldValue = rolIdent
        val now = LocalDateTime.now()

        rolIdent = newROLIdent
        modified = now

        if (rolFlowState == FlowState.RETURNED) {
            rolFlowState = FlowState.NOT_SENT
        }

        val endringslogginnslag = mutableListOf<Endringslogginnslag>()

        endringslogg(
            saksbehandlerident = saksbehandlerident,
            felt = Felt.ROL_IDENT,
            fraVerdi = oldValue,
            tilVerdi = rolIdent,
            tidspunkt = now
        )?.let { endringslogginnslag.add(it) }

        return BehandlingEndretEvent(behandling = this, endringslogginnslag = endringslogginnslag)
    }

    fun Behandling.setSattPaaVent(
        nyVerdi: SattPaaVent?,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelSattPaaVent = sattPaaVent
        val tidspunkt = LocalDateTime.now()

        sattPaaVent = nyVerdi
        modified = tidspunkt

        val endringslogginnslag = mutableListOf<Endringslogginnslag>()

        endringslogg(
            saksbehandlerident = saksbehandlerident,
            felt = Felt.SATT_PAA_VENT,
            fraVerdi = gammelSattPaaVent.toString(),
            tilVerdi = nyVerdi.toString(),
            tidspunkt = tidspunkt
        )?.let { endringslogginnslag.add(it) }

        return BehandlingEndretEvent(behandling = this, endringslogginnslag = endringslogginnslag)
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
                tidspunkt = tidspunkt
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
                tidspunkt = tidspunkt
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
                tidspunkt = tidspunkt
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.setFullmektig(
        nyVerdi: PartId?,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdi = klager.prosessfullmektig
        val tidspunkt = LocalDateTime.now()
        if (nyVerdi == null) {
            klager.prosessfullmektig = null
        } else {
            klager.prosessfullmektig = Prosessfullmektig(partId = nyVerdi, skalPartenMottaKopi = false)
        }
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.FULLMEKTIG,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = nyVerdi.toString(),
                tidspunkt = tidspunkt
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.setKlager(
        nyVerdi: PartId,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdi = klager
        val tidspunkt = LocalDateTime.now()

        klager.partId = nyVerdi

        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.KLAGER,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = klager.toString(),
                tidspunkt = tidspunkt
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
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
                tidspunkt = tidspunkt
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
                tidspunkt = tidspunkt
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
                tidspunkt = tidspunkt
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.setAvsluttetAvSaksbehandler(
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdi = avsluttetAvSaksbehandler
        val tidspunkt = LocalDateTime.now()
        avsluttetAvSaksbehandler = tidspunkt
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.AVSLUTTET_AV_SAKSBEHANDLER_TIDSPUNKT,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = tidspunkt.toString(),
                tidspunkt = tidspunkt
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.setAvsluttet(
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdi = avsluttet
        val tidspunkt = LocalDateTime.now()
        avsluttet = tidspunkt
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.AVSLUTTET_TIDSPUNKT,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = tidspunkt.toString(),
                tidspunkt = tidspunkt
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
                tidspunkt = tidspunkt
            )
            return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
        }
        return null
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
            tidspunkt = tidspunkt
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
            tidspunkt = tidspunkt
        )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    private fun Behandling.endringslogg(
        saksbehandlerident: String,
        felt: Felt,
        fraVerdi: String?,
        tilVerdi: String?,
        tidspunkt: LocalDateTime
    ): Endringslogginnslag? {
        return Endringslogginnslag.endringslogg(
            saksbehandlerident = saksbehandlerident,
            felt = felt,
            fraVerdi = fraVerdi,
            tilVerdi = tilVerdi,
            behandlingId = this.id,
            tidspunkt = tidspunkt
        )
    }

}