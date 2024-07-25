package no.nav.klage.oppgave.config

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class AsyncLogFilter : Filter {

    companion object {
        private val secureLogger = getSecureLogger()
    }

    override fun doFilter(request: ServletRequest?, response: ServletResponse?, chain: FilterChain?) {
        if (request != null) {
            if (request is HttpServletRequest) {
                printHeaderData(request)
                secureLogger.debug("AsyncLogFilter. Path = {}", request.pathInfo)
                secureLogger.debug("AsyncLogFilter. RequestURI = {}", request.requestURI)
            } else {
                secureLogger.debug("AsyncLogFilter. Request is not HttpServletRequest. It was: {}", request.javaClass.simpleName)
            }
        }

        chain?.doFilter(request, response)
    }

    private fun printHeaderData(request: HttpServletRequest) {
        var headers = ""
        for (headerName in request.headerNames) {
            headers += headerName + ": " + request.getHeader(headerName) + "\n"
        }
        secureLogger.debug("AsyncLogFilter. Headers: {}", headers)
    }

}