package no.nav.klage.oppgave.util

import io.mockk.every
import io.mockk.mockk
import no.nav.klage.auth.AzureTokenType
import no.nav.klage.auth.TexasClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken

class TokenUtilTest {
    private val texasClient = mockk<TexasClient>()
    private val tokenUtil =
        TokenUtil(
            texasClient = texasClient,
            kodeverkScope = "kodeverk",
            safScope = "saf",
            safSelvbetjeningAudience = "dev-fss:teamdokumenthandtering:safselvbetjening",
            dokarkivScope = "dokarkiv",
            kabalDocumentScope = "kabal-document",
            kabalSmartEditorApiScope = "kabal-smart-editor-api",
            kakaApiScope = "kaka-api",
            kabalFileApiScope = "kabal-file-api",
            kabalInnstillingerScope = "kabal-innstillinger",
            klageNotificationsApiScope = "klage-notifications-api",
            klageLookupScope = "klage-lookup",
            kabalApiScope = "kabal-api",
            krrProxyScope = "krr-proxy",
            dokDistKanalScope = "dokdistkanal",
            klageFssProxyScope = "klage-fss-proxy",
            klankeScope = "klanke",
            gosysOppgaveScope = "gosys-oppgave",
        )

    @AfterEach
    fun clearContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `reads ident, token type and calling app from an on-behalf-of token`() {
        authenticate(claims = mapOf("NAVident" to "Z123456", "azp_name" to "dev-gcp:klage:kabal-frontend"))

        assertThat(tokenUtil.getIdent()).isEqualTo("Z123456")
        assertThat(tokenUtil.getCurrentTokenType()).isEqualTo(AzureTokenType.OBO)
        assertThat(tokenUtil.getCallingApplication()).isEqualTo("dev-gcp:klage:kabal-frontend")
        assertThat(tokenUtil.getAccessTokenFrontendSent()).isEqualTo("token")
    }

    @Test
    fun `client credentials token has calling app but no ident`() {
        authenticate(claims = mapOf("idtyp" to "app", "azp_name" to "dev-gcp:klage:klage-lookup"))

        assertThat(tokenUtil.getCurrentTokenType()).isEqualTo(AzureTokenType.CC)
        assertThat(tokenUtil.getCallingApplication()).isEqualTo("dev-gcp:klage:klage-lookup")
        assertThrows<RuntimeException> { tokenUtil.getIdent() }
    }

    @Test
    fun `without a token there is no ident or calling app`() {
        assertThat(tokenUtil.getCurrentTokenType()).isEqualTo(AzureTokenType.UNAUTHENTICATED)
        assertThrows<RuntimeException> { tokenUtil.getIdent() }
        assertThrows<RuntimeException> { tokenUtil.getCallingApplication() }
        assertThrows<IllegalStateException> { tokenUtil.getAccessTokenFrontendSent() }
    }

    @Test
    fun `reads pid from the current TokenX token`() {
        authenticate(claims = mapOf("pid" to "12345678910"))

        assertThat(tokenUtil.getSubjectFromTokenXToken()).isEqualTo("12345678910")
        assertThat(tokenUtil.getCurrentTokenType()).isEqualTo(AzureTokenType.UNAUTHENTICATED)
        assertThrows<RuntimeException> { tokenUtil.getIdent() }
    }

    @Test
    fun `fails when there is no TokenX token`() {
        assertThrows<IllegalStateException> { tokenUtil.getSubjectFromTokenXToken() }

        authenticate(claims = mapOf("NAVident" to "Z123456"))
        assertThrows<IllegalStateException> { tokenUtil.getSubjectFromTokenXToken() }
    }

    @Test
    fun `asks Texas for tokens with the right targets`() {
        every { texasClient.onBehalfOfCurrentUser(target = any()) } answers { "obo:${firstArg<String>()}" }
        every { texasClient.machineToMachineToken(target = any()) } answers { "m2m:${firstArg<String>()}" }
        every { texasClient.tokenXExchangeForCurrentUser(target = any()) } answers { "tokenx:${firstArg<String>()}" }

        val graph = "https://graph.microsoft.com/.default"
        val expected =
            listOf(
                tokenUtil.getOnBehalfOfTokenWithSafSelvbetjeningScope() to "tokenx:dev-fss:teamdokumenthandtering:safselvbetjening",
                tokenUtil.getSaksbehandlerAccessTokenWithGraphScope() to "obo:$graph",
                tokenUtil.getAppAccessTokenWithGraphScope() to "m2m:$graph",
                tokenUtil.getSaksbehandlerAccessTokenWithKodeverkScope() to "obo:kodeverk",
                tokenUtil.getSaksbehandlerAccessTokenWithSafScope() to "obo:saf",
                tokenUtil.getAppAccessTokenWithSafScope() to "m2m:saf",
                tokenUtil.getSaksbehandlerAccessTokenWithDokarkivScope() to "obo:dokarkiv",
                tokenUtil.getSaksbehandlerAccessTokenWithKabalFileApiScope() to "obo:kabal-file-api",
                tokenUtil.getAppAccessTokenWithKabalFileApiScope() to "m2m:kabal-file-api",
                tokenUtil.getSaksbehandlerAccessTokenWithKabalSmartEditorApiScope() to "obo:kabal-smart-editor-api",
                tokenUtil.getAppAccessTokenWithKabalSmartEditorApiScope() to "m2m:kabal-smart-editor-api",
                tokenUtil.getAppAccessTokenWithKabalDocumentScope() to "m2m:kabal-document",
                tokenUtil.getSaksbehandlerAccessTokenWithKakaApiScope() to "obo:kaka-api",
                tokenUtil.getAppAccessTokenWithKakaApiScope() to "m2m:kaka-api",
                tokenUtil.getUserAccessTokenWithKabalInnstillingerScope() to "obo:kabal-innstillinger",
                tokenUtil.getAppAccessTokenWithKabalInnstillingerScope() to "m2m:kabal-innstillinger",
                tokenUtil.getOnBehalfOfTokenWithDokDistKanalScope() to "obo:dokdistkanal",
                tokenUtil.getAppAccessTokenWithDokDistKanalScope() to "m2m:dokdistkanal",
                tokenUtil.getAppAccessTokenWithKlageNotificationsApiScope() to "m2m:klage-notifications-api",
                tokenUtil.getOnBehalfOfTokenWithKrrProxyScope() to "obo:krr-proxy",
                tokenUtil.getAppAccessTokenWithKrrProxyScope() to "m2m:krr-proxy",
                tokenUtil.getOnBehalfOfTokenWithKlageFSSProxyScope() to "obo:klage-fss-proxy",
                tokenUtil.getAppAccessTokenWithKlageFSSProxyScope() to "m2m:klage-fss-proxy",
                tokenUtil.getOnBehalfOfTokenWithKlankeScope() to "obo:klanke",
                tokenUtil.getAppAccessTokenWithKlankeScope() to "m2m:klanke",
                tokenUtil.getSaksbehandlerAccessTokenWithGosysOppgaveScope() to "obo:gosys-oppgave",
                tokenUtil.getAppAccessTokenWithGosysOppgaveScope() to "m2m:gosys-oppgave",
                tokenUtil.getSaksbehandlerAccessTokenWithKlageLookupScope() to "obo:klage-lookup",
                tokenUtil.getAppAccessTokenWithKlageLookupScope() to "m2m:klage-lookup",
                tokenUtil.getAppAccessTokenWithKabalApiScope() to "m2m:kabal-api",
            )

        expected.forEach { (actual, wanted) -> assertThat(actual).isEqualTo(wanted) }
    }

    private fun authenticate(claims: Map<String, Any>) {
        val jwt =
            Jwt
                .withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", "subject")
                .claims { it.putAll(claims) }
                .build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt)
    }
}
