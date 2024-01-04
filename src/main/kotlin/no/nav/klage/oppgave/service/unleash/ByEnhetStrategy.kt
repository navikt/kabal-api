package no.nav.klage.oppgave.service.unleash

/*
@Component
class ByEnhetStrategy(private val azureGateway: AzureGateway) : Strategy {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)

        const val PARAM = "valgtEnhet"
    }

    override fun getName(): String {
        return "byEnhet"
    }

    override fun isEnabled(parameters: MutableMap<String, String>): Boolean {
        return false
    }

    override fun isEnabled(parameters: Map<String, String>, unleashContext: UnleashContext): Boolean =
        try {
            unleashContext.userId.map {
                val saksbehandlersEnheter: List<String> = getSaksbehandlersEnheter(unleashContext)
                val enabledEnheter: List<String>? = getEnabledEnheter(parameters)
                enabledEnheter?.intersect(saksbehandlersEnheter.toSet())?.isNotEmpty() ?: false
            }.orElse(false)
        } catch (ex: Exception) {
            logger.warn("Unable to retrieve saksbehandlers enheter", ex)
            false
        }

    private fun getSaksbehandlersEnheter(unleashContext: UnleashContext): List<String> {
        logger.debug("Getting enheter for saksbehandler ${unleashContext.userId.get()}")
        //unleashContext.userId.get() skal være satt til innlogget saksbehandlers ident, så vi kaller like gjerne azureGateway.getDataOmInnloggetSaksbehandler()
        return listOf(azureGateway.getDataOmInnloggetSaksbehandler().enhet).map { it.enhetId }
    }

    private fun getEnabledEnheter(parameters: Map<String, String>?): List<String>? =
        parameters?.get(PARAM)?.split(',')

}

 */