package no.nav.klage.oppgave.config

import no.nav.slackposter.SlackClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SlackClientConfiguration {
    @Value("\${SLACK_URL}")
    lateinit var url: String

    @Value("\${SLACK_CHANNEL_ID}")
    lateinit var channelId: String

    @Value("\${NAIS_CLUSTER_NAME}")
    lateinit var cluster: String

    @Value("\${spring.application.name}")
    lateinit var appName: String

    @Bean
    fun slackClient(): SlackClient = SlackClient(url, channelId, appName, cluster)
}
