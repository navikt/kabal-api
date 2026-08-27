package no.nav.klage.oppgave.domain

data class AuditLogEvent(
    val navIdent: String,
    val action: Action = Action.ACCESS,
    val decision: Decision = Decision.PERMIT,
    val personFnr: String?,
    val logLevel: Level = Level.INFO,
    val message: String?,
) {
    enum class Level {
        INFO,
        WARN,
    }

    enum class Action(
        val value: String,
    ) {
        ACCESS("audit:access"),
    }

    enum class Decision {
        PERMIT,
        DENY,
    }
}
