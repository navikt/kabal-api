package no.nav.klage.oppgave.util

import no.nav.klage.oppgave.config.SecurityConfiguration
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import org.springframework.stereotype.Service

@Service
class TokenUtil(
    private val ctxHolder: TokenValidationContextHolder,
    private val clientConfigurationProperties: ClientConfigurationProperties,
    private val oAuth2AccessTokenService: OAuth2AccessTokenService,
    private val tokenValidationContextHolder: TokenValidationContextHolder,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun getSubjectFromTokenXToken(): String {
        return ctxHolder.getTokenValidationContext().getClaims(SecurityConfiguration.TOKEN_X).getStringClaim("pid")
    }

    fun getOnBehalfOfTokenWithSafSelvbetjeningScope(): String {
        val clientProperties = clientConfigurationProperties.registration["safselvbetjening-onbehalfof"]!!
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        return response.access_token!!
    }

    fun getSaksbehandlerAccessTokenWithGraphScope(): String {
        val clientProperties = clientConfigurationProperties.registration["azure-onbehalfof"]!!
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        return response.access_token!!
    }

    fun getSaksbehandlerAccessTokenWithPdlScope(): String {
        val clientProperties = clientConfigurationProperties.registration["pdl-onbehalfof"]!!
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        return response.access_token!!
    }

    fun getSaksbehandlerAccessTokenWithKodeverkScope(): String {
        val clientProperties = clientConfigurationProperties.registration["kodeverk-onbehalfof"]!!
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        return response.access_token!!
    }

    fun getAppAccessTokenWithPdlScope(): String {
        val clientProperties = clientConfigurationProperties.registration["pdl-maskintilmaskin"]!!
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        return response.access_token!!
    }

    fun getSaksbehandlerAccessTokenWithSafScope(): String {
        val clientProperties = clientConfigurationProperties.registration["saf-onbehalfof"]!!
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        return response.access_token!!
    }

    fun getAppAccessTokenWithSafScope(): String {
        val clientProperties = clientConfigurationProperties.registration["saf-maskintilmaskin"]!!
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        return response.access_token!!
    }

    fun getSaksbehandlerAccessTokenWithDokarkivScope(): String {
        val clientProperties = clientConfigurationProperties.registration["dokarkiv-onbehalfof"]!!
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        return response.access_token!!
    }

    fun getSaksbehandlerAccessTokenWithKabalFileApiScope(): String {
        val clientProperties = clientConfigurationProperties.registration["kabal-file-api-onbehalfof"]!!
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        return response.access_token!!
    }

    fun getAppAccessTokenWithKabalFileApiScope(): String {
        val clientProperties = clientConfigurationProperties.registration["kabal-file-api-maskintilmaskin"]!!
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        return response.access_token!!
    }

    fun getSaksbehandlerAccessTokenWithKabalSmartEditorApiScope(): String {
        val clientProperties = clientConfigurationProperties.registration["kabal-smart-editor-api-onbehalfof"]!!
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        return response.access_token!!
    }

    fun getAppAccessTokenWithKabalSmartEditorApiScope(): String {
        val clientProperties = clientConfigurationProperties.registration["kabal-smart-editor-api-maskintilmaskin"]!!
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        return response.access_token!!
    }

    fun getAppAccessTokenWithKabalDocumentScope(): String {
        val clientProperties = clientConfigurationProperties.registration["kabal-document-maskintilmaskin"]!!
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        return response.access_token!!
    }

    fun getSaksbehandlerAccessTokenWithKakaApiScope(): String {
        val clientProperties = clientConfigurationProperties.registration["kaka-api-onbehalfof"]!!
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        return response.access_token!!
    }

    fun getAppAccessTokenWithKakaApiScope(): String {
        val clientProperties = clientConfigurationProperties.registration["kaka-api-maskintilmaskin"]!!
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        return response.access_token!!
    }

    fun getAppAccessTokenWithGraphScope(): String {
        val clientProperties = clientConfigurationProperties.registration["app"]!!
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        return response.access_token!!
    }

    fun getUserAccessTokenWithKabalInnstillingerScope(): String {
        val clientProperties = clientConfigurationProperties.registration["kabal-innstillinger-onbehalfof"]!!
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        return response.access_token!!
    }

    fun getAppAccessTokenWithKabalInnstillingerScope(): String {
        val clientProperties = clientConfigurationProperties.registration["kabal-innstillinger-maskintilmaskin"]!!
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        return response.access_token!!
    }

    fun getOnBehalfOfTokenWithRegoppslagScope(): String {
        val clientProperties = clientConfigurationProperties.registration["regoppslag-onbehalfof"]!!
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        return response.access_token!!
    }

    fun getAppAccessTokenWithRegoppslagScope(): String {
        val clientProperties = clientConfigurationProperties.registration["regoppslag-maskintilmaskin"]!!
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        return response.access_token!!
    }

    fun getOnBehalfOfTokenWithDokDistKanalScope(): String {
        val clientProperties = clientConfigurationProperties.registration["dok-dist-kanal-onbehalfof"]!!
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        return response.access_token!!
    }

    fun getAppAccessTokenWithDokDistKanalScope(): String {
        val clientProperties = clientConfigurationProperties.registration["dok-dist-kanal-maskintilmaskin"]!!
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        return response.access_token!!
    }

    fun getAppAccessTokenWithKlageNotificationsApiScope(): String {
        val clientProperties = clientConfigurationProperties.registration["klage-notifications-api-maskintilmaskin"]!!
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        return response.access_token!!
    }

    fun getOnBehalfOfTokenWithKrrProxyScope(): String {
        val clientProperties = clientConfigurationProperties.registration["krr-proxy-onbehalfof"]!!
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        return response.access_token!!
    }

    fun getAppAccessTokenWithKrrProxyScope(): String {
        val clientProperties = clientConfigurationProperties.registration["krr-proxy-maskintilmaskin"]!!
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        return response.access_token!!
    }

    fun getOnBehalfOfTokenWithKlageFSSProxyScope(): String {
        val clientProperties = clientConfigurationProperties.registration["klage-fss-proxy-onbehalfof"]!!
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        return response.access_token!!
    }

    fun getAppAccessTokenWithKlageFSSProxyScope(): String {
        val clientProperties = clientConfigurationProperties.registration["klage-fss-proxy-maskintilmaskin"]!!
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        return response.access_token!!
    }

    fun getAppAccessTokenWithNomScope(): String {
        val clientProperties = clientConfigurationProperties.registration["nom-maskintilmaskin"]!!
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        return response.access_token!!
    }

    fun getSaksbehandlerAccessTokenWithGosysOppgaveScope(): String {
        val clientProperties = clientConfigurationProperties.registration["gosys-oppgave-onbehalfof"]!!
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        return response.access_token!!
    }

    fun getAppAccessTokenWithGosysOppgaveScope(): String {
        val clientProperties = clientConfigurationProperties.registration["gosys-oppgave-maskintilmaskin"]!!
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        return response.access_token!!
    }

    fun getSaksbehandlerAccessTokenWithTilgangsmaskinenScope(): String {
        val clientProperties = clientConfigurationProperties.registration["tilgangsmaskinen-onbehalfof"]!!
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        return response.access_token!!
    }

    fun getAppAccessTokenWithTilgangsmaskinenScope(): String {
        val clientProperties = clientConfigurationProperties.registration["tilgangsmaskinen-maskintilmaskin"]!!
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        return response.access_token!!
    }

    fun getSaksbehandlerAccessTokenWithSkjermedePersonerPipScope(): String {
        val clientProperties = clientConfigurationProperties.registration["skjermede-personer-pip-onbehalfof"]!!
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        return response.access_token!!
    }

    fun getAccessTokenFrontendSent(): String =
        tokenValidationContextHolder.getTokenValidationContext().getJwtToken(SecurityConfiguration.ISSUER_AAD)!!.encodedToken

    fun getIdent(): String =
        tokenValidationContextHolder.getTokenValidationContext().getJwtToken(SecurityConfiguration.ISSUER_AAD)
            ?.jwtTokenClaims?.get("NAVident")?.toString()
            ?: throw RuntimeException("Ident not found in token")

    fun getCallingApplication(): String =
        tokenValidationContextHolder.getTokenValidationContext().getJwtToken(SecurityConfiguration.ISSUER_AAD)
            ?.jwtTokenClaims?.get("azp_name")?.toString()
            ?: throw RuntimeException("Application not found in token")
}
