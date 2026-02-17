package no.nav.klage.oppgave.clients.klagelookup

import no.nav.klage.kodeverk.AzureGroup
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.service.TilgangService
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logErrorResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono


@Component
class KlageLookupClient(
    private val klageLookupWebClient: WebClient,
    private val tokenUtil: TokenUtil,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @Retryable
    fun getAccess(
        /** fnr, dnr or aktorId */
        brukerId: String,
        navIdent: String?,
        sakId: String?,
        ytelse: Ytelse?,
        fagsystem: Fagsystem?,
    ): TilgangService.Access {
        return runWithTimingAndLogging {
            val token = if (navIdent != null) {
                "Bearer ${tokenUtil.getAppAccessTokenWithKlageLookupScope()}"
            } else {
                "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithKlageLookupScope()}"
            }

            val accessRequest = AccessRequest(
                brukerId = brukerId,
                navIdent = navIdent,
                sak = if (sakId != null && ytelse != null && fagsystem != null) AccessRequest.Sak(
                    sakId = sakId,
                    ytelse = ytelse,
                    fagsystem = fagsystem
                ) else null,
            )

            klageLookupWebClient.post()
                .uri("/access-to-person")
                .bodyValue(accessRequest)
                .header(
                    HttpHeaders.AUTHORIZATION,
                    token,
                )
                .retrieve()
                .onStatus(HttpStatusCode::isError) { response ->
                    logErrorResponse(
                        response = response,
                        functionName = ::getAccess.name,
                        classLogger = logger,
                    )
                }
                .bodyToMono<TilgangService.Access>()
                .block() ?: throw RuntimeException("Could not get access")
        }
    }

    //    @Retryable
    fun getUserInfo(
        navIdent: String,
    ): ExtendedUserResponse? {
        return runWithTimingAndLogging {
            val token = getCorrectBearerToken()
            klageLookupWebClient.get()
                .uri("/users/$navIdent")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    token,
                )
                .retrieve()
                .onStatus({ it.value() == 404 }) {
                    Mono.empty() // Don't treat 404 as error
                }
                .onStatus(HttpStatusCode::isError) { response ->
                    logger.debug("Vi kom ihvertfall hit")
                    logErrorResponse(
                        response = response,
                        functionName = ::getUserInfo.name,
                        classLogger = logger,
                    )
                }
                .bodyToMono<ExtendedUserResponse>()
                .block()
        }
    }

    @Retryable
    fun getUserGroupMemberships(
        navIdent: String,
    ): GroupsResponse {
        return runWithTimingAndLogging {
            val token = getCorrectBearerToken()

            klageLookupWebClient.get()
                .uri("/users/$navIdent/groups")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    token,
                )
                .retrieve()
                .onStatus(HttpStatusCode::isError) { response ->
                    logErrorResponse(
                        response = response,
                        functionName = ::getUserGroupMemberships.name,
                        classLogger = logger,
                    )
                }
                .bodyToMono<GroupsResponse>()
                .block() ?: throw RuntimeException("Could not get group memberships for navIdent $navIdent")
        }
    }

    @Retryable
    fun getUsersInGroup(
        azureGroup: AzureGroup,
    ): UsersResponse {
        return runWithTimingAndLogging {
            val token = getCorrectBearerToken()

            klageLookupWebClient.get()
                .uri("/groups/${azureGroup.id}/users")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    token,
                )
                .retrieve()
                .onStatus(HttpStatusCode::isError) { response ->
                    logErrorResponse(
                        response = response,
                        functionName = ::getUsersInGroup.name,
                        classLogger = logger,
                    )
                }
                .bodyToMono<UsersResponse>()
                .block() ?: throw RuntimeException("Could not get users in group $azureGroup")
        }
    }

    fun <T> runWithTimingAndLogging(block: () -> T): T {
        val start = System.currentTimeMillis()
        try {
            return block.invoke()
        } finally {
            val end = System.currentTimeMillis()
            logger.debug("Time it took to call klage-lookup: ${end - start} millis")
        }
    }

    private fun getCorrectBearerToken(): String {
        return if (tokenUtil.getIdentOrNull() == null) {
            "Bearer ${tokenUtil.getAppAccessTokenWithKlageLookupScope()}"
        } else {
            "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithKlageLookupScope()}"
        }
    }
}