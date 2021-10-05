package no.nav.klage.oppgave.service

import com.ninjasquad.springmockk.MockkBean
import no.nav.klage.oppgave.config.ElasticsearchServiceConfiguration
import no.nav.klage.oppgave.domain.elasticsearch.EsKlagebehandling
import no.nav.klage.oppgave.domain.elasticsearch.EsKlagebehandling.Status.*
import no.nav.klage.oppgave.domain.kodeverk.Tema
import no.nav.klage.oppgave.domain.kodeverk.Type
import no.nav.klage.oppgave.repositories.EsKlagebehandlingRepository
import no.nav.klage.oppgave.repositories.InnloggetSaksbehandlerRepository
import org.assertj.core.api.Assertions.assertThat
import org.elasticsearch.index.query.QueryBuilders
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate
import org.springframework.data.elasticsearch.core.SearchHits
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder
import org.springframework.data.elasticsearch.core.query.Query
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*


@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@Testcontainers
@SpringBootTest(classes = [ElasticsearchServiceConfiguration::class])
@ImportAutoConfiguration(
    ElasticsearchRestClientAutoConfiguration::class,
    ElasticsearchDataAutoConfiguration::class
)
class ElasticsearchServiceStatusTest {

    companion object {
        @Container
        @JvmField
        val esContainer: TestElasticsearchContainer = TestElasticsearchContainer.instance
    }

    @MockkBean(relaxed = true)
    lateinit var innloggetSaksbehandlerRepository: InnloggetSaksbehandlerRepository

    @Autowired
    lateinit var service: ElasticsearchService

    @Autowired
    lateinit var esTemplate: ElasticsearchRestTemplate

    @Autowired
    lateinit var repo: EsKlagebehandlingRepository

    @Test
    @Order(1)
    fun `es is running`() {
        assertThat(esContainer.isRunning).isTrue
        service.recreateIndex()
    }

    @Test
    @Order(2)
    fun `index has been created by service`() {
        val indexOps = esTemplate.indexOps(IndexCoordinates.of("klagebehandling"))
        assertThat(indexOps.exists()).isTrue
    }

    @Test
    @Order(3)
    fun `status count works`() {
        repo.save(getKlagebehandling(IKKE_TILDELT))
        repo.save(getKlagebehandling(TILDELT))
        repo.save(getKlagebehandling(MEDUNDERSKRIVER_VALGT))
        repo.save(getKlagebehandling(SENDT_TIL_MEDUNDERSKRIVER))
        repo.save(getKlagebehandling(RETURNERT_TIL_SAKSBEHANDLER))
        repo.save(getKlagebehandling(FULLFOERT))

        val query: Query = NativeSearchQueryBuilder()
            .withQuery(QueryBuilders.matchAllQuery())
            .build()
        val searchHits: SearchHits<EsKlagebehandling> = esTemplate.search(query, EsKlagebehandling::class.java)
        assertThat(searchHits.totalHits).isEqualTo(6L)

        assertThat(service.countIkkeTildelt()).isEqualTo(1)
        assertThat(service.countTildelt()).isEqualTo(1)
        assertThat(service.countMedunderskriverValgt()).isEqualTo(1)
        assertThat(service.countSendtTilMedunderskriver()).isEqualTo(1)
        assertThat(service.countReturnertTilSaksbehandler()).isEqualTo(1)
        assertThat(service.countAvsluttet()).isEqualTo(1)
    }

    private fun getKlagebehandling(status: EsKlagebehandling.Status) = EsKlagebehandling(
        id = UUID.randomUUID().toString(),
        versjon = 1L,
        tildeltEnhet = "4219",
        tema = Tema.OMS.id,
        type = Type.KLAGE.id,
        tildeltSaksbehandlerident = null,
        innsendt = LocalDate.of(2019, 10, 1),
        mottattFoersteinstans = LocalDate.of(2019, 11, 1),
        mottattKlageinstans = LocalDateTime.of(2019, 12, 1, 0, 0),
        frist = LocalDate.of(2020, 12, 1),
        hjemler = listOf(),
        created = LocalDateTime.now(),
        modified = LocalDateTime.now(),
        kilde = "K9",
        temaNavn = "",
        typeNavn = "",
        status = status
    )

}