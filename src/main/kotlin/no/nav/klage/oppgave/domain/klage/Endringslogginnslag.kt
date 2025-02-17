package no.nav.klage.oppgave.domain.klage

import no.nav.klage.oppgave.util.getLogger
import java.util.*

class Endringslogginnslag(
    val saksbehandlerident: String?, //subjekt?
    val felt: Felt,
    val behandlingId: UUID,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)

        fun endringslogg(
            saksbehandlerident: String?,
            felt: Felt,
            fraVerdi: String?,
            tilVerdi: String?,
            behandlingId: UUID,
        ): Endringslogginnslag? {
            if ((fraVerdi == null && tilVerdi == null) || fraVerdi == tilVerdi) {
                logger.debug(
                    "Returning null from endringslogg. Felt: {}, fraVerdi: {}, tilVerdi: {}, behandlingId: {}",
                    felt.name,
                    fraVerdi,
                    tilVerdi,
                    behandlingId
                )
                return null
            } else {
                return Endringslogginnslag(
                    saksbehandlerident = saksbehandlerident,
                    felt = felt,
                    behandlingId = behandlingId,
                )
            }
        }
    }
}

enum class Felt {
    UTFALL_ID,
    EXTRA_UTFALL_SET,
    INNSENDINGSHJEMLER_ID_LIST,
    MOTTATT_FOERSTEINSTANS_DATO,
    TILDELT_SAKSBEHANDLERIDENT,
    TILDELT_ENHET,
    TILDELT_TIDSPUNKT,
    SAKSDOKUMENT,
    MEDUNDERSKRIVERIDENT,
    REGISTRERINGSHJEMLER_ID_LIST,
    AVSLUTTET_AV_SAKSBEHANDLER_TIDSPUNKT,
    AVSLUTTET_TIDSPUNKT,
    MEDUNDERSKRIVER_FLOW_STATE_ID,
    SATT_PAA_VENT,
    MOTTATT_KLAGEINSTANS_TIDSPUNKT,
    SENDT_TIL_TRYGDERETTEN_TIDSPUNKT,
    KJENNELSE_MOTTATT_TIDSPUNKT,
    FRIST_DATO,
    VARSLET_FRIST,
    VARSLET_BEHANDLINGSTID_UNITS,
    VARSLET_BEHANDLINGSTID_UNIT_TYPE,
    FULLMEKTIG,
    FEILREGISTRERING,
    KLAGER,
    ROL_FLOW_STATE_ID,
    ROL_IDENT,
    ROL_RETURNED_TIDSPUNKT,
    NY_ANKEBEHANDLING_KA,
    GOSYS_OPPGAVE_UPDATE,
    IGNORE_GOSYS_OPPGAVE,
    NY_BEHANDLING_ETTER_TR_OPPHEVET,
    KLAGEBEHANDLING_MOTTATT,
    ANKEBEHANDLING_MOTTATT,
    OMGJOERINGSKRAVBEHANDLING_MOTTATT,
    ANKE_I_TRYGDERETTEN_OPPRETTET,
    BEHANDLING_ETTER_TR_OPPHEVET_OPPRETTET,
    ANKEBEHANDLING_OPPRETTET_BASERT_PAA_ANKE_I_TRYGDERETTEN,
    TILBAKEKREVING,
    GOSYSOPPGAVE_ID,
}