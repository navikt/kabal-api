package no.nav.klage.oppgave.api.filter

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.FilterConfig
import jakarta.servlet.ServletException
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.annotation.WebFilter
import jakarta.servlet.http.HttpServletRequest
import no.nav.klage.oppgave.util.getLogger
import org.springframework.stereotype.Component
import java.io.IOException
import java.time.Duration
import java.time.Instant

@Component
@WebFilter(urlPatterns = ["/klagebehandlinger/*", "/behandlinger/*"])
class TimingFilter : Filter {
    companion object {
        private val logger = getLogger(TimingFilter::class.java)
    }

    @Throws(ServletException::class)
    override fun init(filterConfig: FilterConfig) {
        // empty
    }

    @Throws(IOException::class, ServletException::class)
    override fun doFilter(
        req: ServletRequest,
        resp: ServletResponse,
        chain: FilterChain,
    ) {
        val start = Instant.now()
        try {
            chain.doFilter(req, resp)
        } finally {
            val finish = Instant.now()
            val time = Duration.between(start, finish).toMillis()
            logger.debug("{}: {} ms ", (req as HttpServletRequest).requestURI, time)
        }
    }

    override fun destroy() {
        // empty
    }
}
