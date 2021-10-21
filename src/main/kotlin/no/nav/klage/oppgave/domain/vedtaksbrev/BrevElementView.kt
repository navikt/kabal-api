package no.nav.klage.oppgave.domain.vedtaksbrev

import no.nav.klage.oppgave.domain.vedtaksbrev.enums.BrevElementInputType
import no.nav.klage.oppgave.domain.vedtaksbrev.enums.BrevElementKey

data class BrevElementView(
    val key: BrevElementKey,
    val displayText: String? = null,
    val content: String? = null,
    val placeholderText: String? = null,
    val elementInputType: BrevElementInputType?,
    val contentList: List<String>? = null
)