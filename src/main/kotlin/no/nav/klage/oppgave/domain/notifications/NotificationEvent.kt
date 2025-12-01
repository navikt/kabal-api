package no.nav.klage.oppgave.domain.notifications

import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.ytelse.Ytelse
import java.time.LocalDateTime
import java.util.*

sealed class CreateNotificationEvent(
    open val type: NotificationType,
    open val message: String,
    open val recipientNavIdent: String,
    open val sourceCreatedAt: LocalDateTime,
    open val actorNavIdent: String,
    open val actorNavn: String,
) {
    enum class NotificationType {
        MELDING, LOST_ACCESS
    }
}

data class CreateMeldingNotificationEvent(
    override val type: NotificationType,
    override val message: String,
    override val recipientNavIdent: String,
    override val actorNavIdent: String,
    override val actorNavn: String,
    override val sourceCreatedAt: LocalDateTime,
    val meldingId: UUID,
    val behandlingId: UUID,
    val behandlingType: Type,
    val saksnummer: String,
    val ytelse: Ytelse,
) : CreateNotificationEvent(
    type = type,
    message = message,
    recipientNavIdent = recipientNavIdent,
    sourceCreatedAt = sourceCreatedAt,
    actorNavIdent = actorNavIdent,
    actorNavn = actorNavn,
)

data class CreateLostAccessNotificationEvent(
    override val type: NotificationType,
    override val message: String,
    override val recipientNavIdent: String,
    override val actorNavIdent: String,
    override val actorNavn: String,
    override val sourceCreatedAt: LocalDateTime,
    val behandlingId: UUID,
    val behandlingType: Type,
    val saksnummer: String,
    val ytelse: Ytelse,
) : CreateNotificationEvent(
    type = type,
    message = message,
    recipientNavIdent = recipientNavIdent,
    sourceCreatedAt = sourceCreatedAt,
    actorNavIdent = actorNavIdent,
    actorNavn = actorNavn,
)