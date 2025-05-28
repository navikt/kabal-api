package no.nav.klage.oppgave.clients.azure

import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logErrorResponse
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.HttpStatusCode
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@Component
class MicrosoftGraphClient(
    private val microsoftGraphWebClient: WebClient,
    private val tokenUtil: TokenUtil
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)

        private const val userSelect =
            "onPremisesSamAccountName,displayName,givenName,surname,mail,officeLocation,userPrincipalName,id,jobTitle,streetAddress"

        private const val slimUserSelect = "userPrincipalName,onPremisesSamAccountName,displayName"

        private const val groupMemberSelect = "id,mail,onPremisesSamAccountName,displayName"
    }

    @Retryable
    fun getInnloggetSaksbehandler(): AzureUser {
        logger.debug("Fetching data about authenticated user from Microsoft Graph")

        return microsoftGraphWebClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/me")
                    .queryParam("\$select", userSelect)
                    .build()
            }.header("Authorization", "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithGraphScope()}")

            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(
                    response = response,
                    functionName = ::getInnloggetSaksbehandler.name,
                    classLogger = logger,
                )
            }
            .bodyToMono<AzureUser>()
            .block() ?: throw RuntimeException("AzureAD data about authenticated user could not be fetched")
    }

    @Retryable
    fun getSaksbehandler(navIdent: String): AzureUser {
        logger.debug("Fetching data about authenticated user from Microsoft Graph")
        return findUserByNavIdent(navIdent)
    }

    @Retryable
    fun getInnloggetSaksbehandlersGroups(): List<AzureGroup> {
        logger.debug("Fetching data about authenticated users groups from Microsoft Graph")

        return microsoftGraphWebClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/me/memberOf")
                    .build()
            }.header("Authorization", "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithGraphScope()}")
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(
                    response = response,
                    functionName = ::getInnloggetSaksbehandlersGroups.name,
                    classLogger = logger,
                )
            }
            .bodyToMono<AzureGroupList>()
            .block()?.value ?: throw RuntimeException("AzureAD data about authenticated users groups could not be fetched")
    }

    @Retryable
    fun getSaksbehandlersGroups(navIdent: String): List<AzureGroup> {
        logger.debug("Fetching data about users groups from Microsoft Graph")
        val user = findUserByNavIdent(navIdent)
        return getGroupsByUserPrincipalName(user.userPrincipalName)
    }

    @Retryable
    fun getAllDisplayNames(idents: List<List<String>>): Map<String, String> {
        val queryString = idents.map {
            it.joinToString(separator = "','", prefix = "('", postfix = "')")
        }

        val data = Flux.fromIterable(queryString)
            .parallel()
            .runOn(Schedulers.boundedElastic())
            .flatMap {
                getDisplayNames(it)
            }
            .ordered { _: AzureSlimUserList, _: AzureSlimUserList -> 1 }.toIterable()

        return data.flatMap {
            it.value ?: emptyList()
        }.associate {
            it.onPremisesSamAccountName to it.displayName
        }
    }

    private fun getDisplayNames(navIdents: String): Mono<AzureSlimUserList> {
        return try {
            microsoftGraphWebClient.get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("/users")
                        .queryParam("\$filter", "mailnickname in $navIdents")
                        .queryParam("\$select", slimUserSelect)
                        .build()
                }.header("Authorization", "Bearer ${tokenUtil.getAppAccessTokenWithGraphScope()}")
                .retrieve()
                .onStatus(HttpStatusCode::isError) { response ->
                    logErrorResponse(
                        response = response,
                        functionName = ::getDisplayNames.name,
                        classLogger = logger,
                    )
                }
                .bodyToMono()
        } catch (e: Exception) {
            logger.warn("Could not fetch displayname for idents: $navIdents", e)
            Mono.empty()
        }
    }

    @Retryable
    @Cacheable(CacheWithJCacheConfiguration.GROUPMEMBERS_CACHE)
    fun getGroupMembersNavIdents(groupid: String): List<String> {
        val azureGroupMember: List<AzureGroupMember> = microsoftGraphWebClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/groups/{groupid}/members")
                    .queryParam("\$select", groupMemberSelect)
                    .queryParam("\$top", 999)
                    .build(groupid)
            }
            .header("Authorization", "Bearer ${tokenUtil.getAppAccessTokenWithGraphScope()}")
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(
                    response = response,
                    functionName = ::getGroupMembersNavIdents.name,
                    classLogger = logger,
                )
            }
            .bodyToMono<AzureGroupMemberList>().block()?.value
            ?: throw RuntimeException("AzureAD data about group members nav idents could not be fetched")
        return azureGroupMember.map { it.onPremisesSamAccountName }
    }

    @Retryable
    @Cacheable(CacheWithJCacheConfiguration.ANSATTE_I_ENHET_CACHE)
    fun getEnhetensAnsattesNavIdents(enhetNr: String): List<String> {
        return microsoftGraphWebClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/users")
                    .queryParam("\$filter", "streetAddress eq '$enhetNr'")
                    .queryParam("\$count", true)
                    .queryParam("\$top", 500)
                    .queryParam("\$select", slimUserSelect)
                    .build()
            }
            .header("Authorization", "Bearer ${tokenUtil.getAppAccessTokenWithGraphScope()}")
            .header("ConsistencyLevel", "eventual")
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(
                    response = response,
                    functionName = ::getEnhetensAnsattesNavIdents.name,
                    classLogger = logger,
                )
            }
            .bodyToMono<AzureSlimUserList>()
            .block()
            .let { userList -> userList?.value?.map { it.onPremisesSamAccountName } }
            ?: throw RuntimeException("AzureAD data about authenticated user could not be fetched")
    }

    private fun getGroupsByUserPrincipalName(userPrincipalName: String): List<AzureGroup> {
        val aadAzureGroups: List<AzureGroup> = microsoftGraphWebClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/users/{userPrincipalName}/memberOf")
                    .queryParam("\$top", 500)
                    .build(userPrincipalName)
            }
            .header("Authorization", "Bearer ${tokenUtil.getAppAccessTokenWithGraphScope()}")
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(
                    response = response,
                    functionName = ::getGroupsByUserPrincipalName.name,
                    classLogger = logger,
                )
            }
            .bodyToMono<AzureGroupList>().block()?.value?.map { it }
            ?: throw RuntimeException("AzureAD data about groups by user principal name could not be fetched")
        return aadAzureGroups
    }

    private fun findUserByNavIdent(navIdent: String): AzureUser {
        logger.debug("findUserByNavIdent $navIdent")
        return microsoftGraphWebClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/users")
                    .queryParam("\$filter", "onPremisesSamAccountName eq '$navIdent'")
                    .queryParam("\$select", userSelect)
                    .queryParam("\$count", true)
                    .build()
            }
            .header("Authorization", "Bearer ${tokenUtil.getAppAccessTokenWithGraphScope()}")
            .header("ConsistencyLevel", "eventual")
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(
                    response = response,
                    functionName = ::findUserByNavIdent.name,
                    classLogger = logger,
                )
            }
            .bodyToMono<AzureUserList>().block()?.value?.firstOrNull()
            ?: throw RuntimeException("AzureAD data about user by nav ident could not be fetched")
    }
}