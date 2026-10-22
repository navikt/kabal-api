package no.nav.klage.oppgave.clients.saf.graphql

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SakTest {
    @Test
    fun `fagsak gives fagsakId as saksnummer`() {
        assertThat(sak(sakstype = "FAGSAK").getSaksnummer()).isEqualTo("fagsak-1")
    }

    @Test
    fun `generell sak gives arkivsaksnummer as saksnummer`() {
        assertThat(sak(sakstype = "GENERELL_SAK", fagsakId = null).getSaksnummer()).isEqualTo("140411735")
    }

    @Test
    fun `arkivsak gives arkivsaksnummer as saksnummer`() {
        assertThat(sak(sakstype = "ARKIVSAK", fagsakId = null).getSaksnummer()).isEqualTo("140411735")
    }

    @Test
    fun `fagsak without fagsakId gives arkivsaksnummer as saksnummer`() {
        assertThat(sak(sakstype = "FAGSAK", fagsakId = null).getSaksnummer()).isEqualTo("140411735")
    }

    @Test
    fun `sak without sakstype gives arkivsaksnummer as saksnummer`() {
        assertThat(sak(sakstype = null, fagsakId = null).getSaksnummer()).isEqualTo("140411735")
    }

    private fun sak(
        sakstype: String?,
        fagsakId: String? = "fagsak-1",
    ) = Sak(
        datoOpprettet = null,
        fagsakId = fagsakId,
        fagsaksystem = "FS22",
        sakstype = sakstype,
        arkivsaksnummer = "140411735",
    )
}
