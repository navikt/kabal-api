package no.nav.klage.oppgave.config

/*
@Configuration
class FeatureToggleConfig {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        const val KLAGE_GENERELL_TILGANG = "klage.generellTilgang"
    }

    @Value("\${spring.application.name}")
    lateinit var appName: String

    @Autowired
    lateinit var environment: Environment

    @Value("\${UNLEASH_URL}")
    private lateinit var unleashUrl: String

    @Bean
    @Profile("dev-gcp", "prod-gcp")
    fun unleash(
        byClusterStrategy: ByClusterStrategy,
        byEnhetStrategy: ByEnhetStrategy,
        byUserStrategy: ByUserStrategy
    ): Unleash? {
        val unleashConfig = UnleashConfig.builder()
            .appName(appName)
            .instanceId(environment.activeProfiles.first())
            .unleashAPI(unleashUrl)
            .build()
        logger.info(
            "Unleash settes opp med appName {}, instanceId {} og url {}",
            appName,
            environment.activeProfiles.first(),
            unleashUrl
        )
        return DefaultUnleash(unleashConfig, byClusterStrategy, byEnhetStrategy, byUserStrategy)
    }

    @Bean
    @Profile("local")
    fun unleashMock(): Unleash? {
        val fakeUnleash = FakeUnleash()
        fakeUnleash.enableAll()
        return fakeUnleash
    }
}
*/