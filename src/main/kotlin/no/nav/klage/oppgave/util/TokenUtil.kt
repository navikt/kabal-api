package no.nav.klage.oppgave.util

import no.nav.klage.auth.AzureTokenType
import no.nav.klage.auth.TexasClient
import no.nav.klage.auth.callingApp
import no.nav.klage.auth.currentAzureToken
import no.nav.klage.auth.currentAzureTokenType
import no.nav.klage.auth.currentTokenXToken
import no.nav.klage.auth.navIdent
import no.nav.klage.auth.pid
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class TokenUtil(
    private val texasClient: TexasClient,
    @Value($$"api://${KODEVERK_SCOPE}/.default")
    private val kodeverkScope: String,
    @Value($$"api://${SAF_SCOPE}/.default")
    private val safScope: String,
    @Value($$"${SAFSELVBETJENING_AUDIENCE}")
    private val safSelvbetjeningAudience: String,
    @Value($$"api://${DOKARKIV_SCOPE}/.default")
    private val dokarkivScope: String,
    @Value($$"api://${NAIS_CLUSTER_NAME}.${NAIS_NAMESPACE}.${KABAL_DOCUMENT_APP_NAME}/.default")
    private val kabalDocumentScope: String,
    @Value($$"api://${NAIS_CLUSTER_NAME}.${NAIS_NAMESPACE}.${KABAL_SMART_EDITOR_API_APP_NAME}/.default")
    private val kabalSmartEditorApiScope: String,
    @Value($$"api://${NAIS_CLUSTER_NAME}.${NAIS_NAMESPACE}.${KAKA_API_APP_NAME}/.default")
    private val kakaApiScope: String,
    @Value($$"api://${NAIS_CLUSTER_NAME}.${NAIS_NAMESPACE}.${KABAL_FILE_API_APP_NAME}/.default")
    private val kabalFileApiScope: String,
    @Value($$"api://${NAIS_CLUSTER_NAME}.${NAIS_NAMESPACE}.${KABAL_INNSTILLINGER_APP_NAME}/.default")
    private val kabalInnstillingerScope: String,
    @Value($$"api://${NAIS_CLUSTER_NAME}.${NAIS_NAMESPACE}.${KLAGE_NOTIFICATIONS_API_APP_NAME}/.default")
    private val klageNotificationsApiScope: String,
    @Value($$"api://${NAIS_CLUSTER_NAME}.${NAIS_NAMESPACE}.${KLAGE_LOOKUP_APP_NAME}/.default")
    private val klageLookupScope: String,
    @Value($$"api://${NAIS_CLUSTER_NAME}.${NAIS_NAMESPACE}.${KABAL_API_APP_NAME}/.default")
    private val kabalApiScope: String,
    @Value($$"api://${KRR_PROXY_SCOPE}/.default")
    private val krrProxyScope: String,
    @Value($$"api://${DOK_DIST_KANAL_SCOPE}/.default")
    private val dokDistKanalScope: String,
    @Value($$"api://${FSS_CLUSTER}.klage.klage-fss-proxy/.default")
    private val klageFssProxyScope: String,
    @Value($$"api://${FSS_CLUSTER}.klage.klanke/.default")
    private val klankeScope: String,
    @Value($$"api://${GOSYS_OPPGAVE_SCOPE}/.default")
    private val gosysOppgaveScope: String,
) {
    companion object {
        private const val MICROSOFT_GRAPH_SCOPE = "https://graph.microsoft.com/.default"
    }

    /** The fødselsnummer of the logged in citizen. Only on the request thread of a TokenX request. */
    fun getSubjectFromTokenXToken(): String =
        currentTokenXToken()?.pid ?: throw IllegalStateException("No logged in citizen in this request.")

    fun getOnBehalfOfTokenWithSafSelvbetjeningScope(): String = texasClient.tokenXExchangeForCurrentUser(safSelvbetjeningAudience)

    fun getSaksbehandlerAccessTokenWithGraphScope(): String = texasClient.onBehalfOfCurrentUser(MICROSOFT_GRAPH_SCOPE)

    fun getAppAccessTokenWithGraphScope(): String = texasClient.machineToMachineToken(MICROSOFT_GRAPH_SCOPE)

    fun getSaksbehandlerAccessTokenWithKodeverkScope(): String = texasClient.onBehalfOfCurrentUser(kodeverkScope)

    fun getSaksbehandlerAccessTokenWithSafScope(): String = texasClient.onBehalfOfCurrentUser(safScope)

    fun getAppAccessTokenWithSafScope(): String = texasClient.machineToMachineToken(safScope)

    fun getSaksbehandlerAccessTokenWithDokarkivScope(): String = texasClient.onBehalfOfCurrentUser(dokarkivScope)

    fun getSaksbehandlerAccessTokenWithKabalFileApiScope(): String = texasClient.onBehalfOfCurrentUser(kabalFileApiScope)

    fun getAppAccessTokenWithKabalFileApiScope(): String = texasClient.machineToMachineToken(kabalFileApiScope)

    fun getSaksbehandlerAccessTokenWithKabalSmartEditorApiScope(): String = texasClient.onBehalfOfCurrentUser(kabalSmartEditorApiScope)

    fun getAppAccessTokenWithKabalSmartEditorApiScope(): String = texasClient.machineToMachineToken(kabalSmartEditorApiScope)

    fun getAppAccessTokenWithKabalDocumentScope(): String = texasClient.machineToMachineToken(kabalDocumentScope)

    fun getSaksbehandlerAccessTokenWithKakaApiScope(): String = texasClient.onBehalfOfCurrentUser(kakaApiScope)

    fun getAppAccessTokenWithKakaApiScope(): String = texasClient.machineToMachineToken(kakaApiScope)

    fun getUserAccessTokenWithKabalInnstillingerScope(): String = texasClient.onBehalfOfCurrentUser(kabalInnstillingerScope)

    fun getAppAccessTokenWithKabalInnstillingerScope(): String = texasClient.machineToMachineToken(kabalInnstillingerScope)

    fun getOnBehalfOfTokenWithDokDistKanalScope(): String = texasClient.onBehalfOfCurrentUser(dokDistKanalScope)

    fun getAppAccessTokenWithDokDistKanalScope(): String = texasClient.machineToMachineToken(dokDistKanalScope)

    fun getAppAccessTokenWithKlageNotificationsApiScope(): String = texasClient.machineToMachineToken(klageNotificationsApiScope)

    fun getOnBehalfOfTokenWithKrrProxyScope(): String = texasClient.onBehalfOfCurrentUser(krrProxyScope)

    fun getAppAccessTokenWithKrrProxyScope(): String = texasClient.machineToMachineToken(krrProxyScope)

    fun getOnBehalfOfTokenWithKlageFSSProxyScope(): String = texasClient.onBehalfOfCurrentUser(klageFssProxyScope)

    fun getAppAccessTokenWithKlageFSSProxyScope(): String = texasClient.machineToMachineToken(klageFssProxyScope)

    fun getOnBehalfOfTokenWithKlankeScope(): String = texasClient.onBehalfOfCurrentUser(klankeScope)

    fun getAppAccessTokenWithKlankeScope(): String = texasClient.machineToMachineToken(klankeScope)

    fun getSaksbehandlerAccessTokenWithGosysOppgaveScope(): String = texasClient.onBehalfOfCurrentUser(gosysOppgaveScope)

    fun getAppAccessTokenWithGosysOppgaveScope(): String = texasClient.machineToMachineToken(gosysOppgaveScope)

    fun getSaksbehandlerAccessTokenWithKlageLookupScope(): String = texasClient.onBehalfOfCurrentUser(klageLookupScope)

    fun getAppAccessTokenWithKlageLookupScope(): String = texasClient.machineToMachineToken(klageLookupScope)

    fun getAppAccessTokenWithKabalApiScope(): String = texasClient.machineToMachineToken(kabalApiScope)

    /** The raw Azure token the caller sent. Only on the request thread. */
    fun getAccessTokenFrontendSent(): String = currentAzureToken()?.tokenValue ?: throw IllegalStateException("No token in this request.")

    /** NAVident of the logged in Nav employee. Throws for machine-to-machine and TokenX requests, and off the request thread. */
    fun getIdent(): String = currentAzureToken()?.navIdent ?: throw RuntimeException("Ident not found in token")

    fun getCurrentTokenType(): AzureTokenType = currentAzureTokenType()

    /** azp_name of the calling app, for logging and revision history. Do not use it for access control. */
    fun getCallingApplication(): String = currentAzureToken()?.callingApp ?: throw RuntimeException("Application not found in token")
}
