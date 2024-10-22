package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.*
import no.nav.klage.oppgave.util.getLogger
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "endringslogginnslag", schema = "klage")
class Endringslogginnslag(
    @Column(name = "saksbehandlerident")
    val saksbehandlerident: String?, //subjekt?
    @Enumerated(EnumType.STRING)
    @Column(name = "kilde")
    val kilde: KildeSystem,
    @Enumerated(EnumType.STRING)
    @Column(name = "handling")
    val handling: Handling,
    @Enumerated(EnumType.STRING)
    @Column(name = "felt")
    val felt: Felt,
    @Column(name = "fraverdi")
    val fraVerdi: String?,
    @Column(name = "tilverdi")
    val tilVerdi: String?,
    @Column(name = "behandling_id")
    val behandlingId: UUID,
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "tidspunkt")
    val tidspunkt: LocalDateTime = LocalDateTime.now()
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
            tidspunkt: LocalDateTime
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
                val handling = when {
                    fraVerdi == null && tilVerdi != null -> Handling.NY
                    fraVerdi != null && tilVerdi == null -> Handling.SLETTING
                    else -> Handling.ENDRING
                }
                return Endringslogginnslag(
                    saksbehandlerident = saksbehandlerident,
                    kilde = KildeSystem.KABAL,
                    handling = handling,
                    felt = felt,
                    fraVerdi = fraVerdi,
                    tilVerdi = tilVerdi,
                    behandlingId = behandlingId,
                    tidspunkt = tidspunkt
                )
            }
        }
    }
}

enum class Handling {
    NY, ENDRING, SLETTING
}

enum class KildeSystem {
    KABAL, ADMIN
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
    SMARTDOKUMENT_OPPRETTET,
    SENDT_TIL_TRYGDERETTEN_TIDSPUNKT,
    KJENNELSE_MOTTATT_TIDSPUNKT,
    FRIST_DATO,
    OPPGAVE_ID,
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
    OPPGAVE_RETURNED,
    IGNORE_GOSYS_OPPGAVE,
    NY_BEHANDLING_ETTER_TR_OPPHEVET,
    KLAGEBEHANDLING_MOTTATT,
    ANKEBEHANDLING_MOTTATT,
    ANKE_I_TRYGDERETTEN_OPPRETTET,
    BEHANDLING_ETTER_TR_OPPHEVET_OPPRETTET,
    ANKEBEHANDLING_OPPRETTET_BASERT_PAA_ANKE_I_TRYGDERETTEN,
    TILBAKEKREVING,
    GOSYSOPPGAVE_ID,
}
