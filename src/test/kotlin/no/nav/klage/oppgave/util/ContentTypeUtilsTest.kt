package no.nav.klage.oppgave.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.ContentDisposition

internal class ContentTypeUtilsTest {
    @Test
    fun `percent sign is replaced in fallback filename and encoded in extended filename`() {
        val value = contentDispositionHeaderValue(filename = "80% uføretrygd.pdf", type = "inline")

        assertThat(value).isEqualTo(
            "inline; filename=\"80_ uf_retrygd.pdf\"; filename*=UTF-8''80%25%20uf%C3%B8retrygd.pdf",
        )
    }

    @Test
    fun `parsed header keeps the exact filename`() {
        val filename = "Vedtak - innvilgelse av 80% \"uføretrygd\".pdf"

        val value = contentDispositionHeaderValue(filename = filename, inline = false)
        val parsed = ContentDisposition.parse(value)

        assertThat(parsed.type).isEqualTo("attachment")
        assertThat(parsed.filename).isEqualTo(filename)
    }

    @Test
    fun `filename without percent sign is unchanged`() {
        val value = contentDispositionHeaderValue(filename = "vedtak.pdf", type = "inline")

        assertThat(value).isEqualTo("inline; filename=\"vedtak.pdf\"; filename*=UTF-8''vedtak.pdf")
    }
}
