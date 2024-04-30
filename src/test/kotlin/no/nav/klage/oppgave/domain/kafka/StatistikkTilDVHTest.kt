package no.nav.klage.oppgave.domain.kafka

import com.kjetland.jackson.jsonSchema.JsonSchemaConfig
import com.kjetland.jackson.jsonSchema.JsonSchemaDraft
import com.kjetland.jackson.jsonSchema.JsonSchemaGenerator
import no.nav.klage.oppgave.util.ourJacksonObjectMapper
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal class StatistikkTilDVHTest {

    @Test
    @Disabled
    fun createJsonSchema() {
        val objectMapper = ourJacksonObjectMapper()

        val config = JsonSchemaConfig.vanillaJsonSchemaDraft4().withJsonSchemaDraft(JsonSchemaDraft.DRAFT_07)

        val schemaGen = JsonSchemaGenerator(objectMapper, config)
        val jsonSchema = schemaGen.generateJsonSchema(StatistikkTilDVH::class.java)

        println(objectMapper.writeValueAsString(jsonSchema))

        val statistikkTilDVH = StatistikkTilDVH(
            eventId = UUID.randomUUID(),
            ansvarligEnhetKode = "kode",
            ansvarligEnhetType = "type",
            avsender = "avsender",
            behandlingId = "arsta-rstz-xct-trstrst34-sft",
            behandlingIdKabal = "arst-arsdt-drt-j8z-89",
            behandlingStartetKA = LocalDate.now(),
            behandlingStatus = BehandlingState.MOTTATT,
            behandlingType = "behType",
            beslutter = "beslutter",
            endringstid = LocalDateTime.now(),
            hjemmel = listOf("8-14"),
            klager = StatistikkTilDVH.Part(verdi = "8005138513", StatistikkTilDVH.PartIdType.VIRKSOMHET),
            opprinneligFagsaksystem = "K9Sak",
            opprinneligFagsakId = "123456789",
            overfoertKA = LocalDate.now(),
            resultat = "resultatet",
            sakenGjelder = StatistikkTilDVH.Part(verdi = "20127529618", StatistikkTilDVH.PartIdType.PERSON),
            saksbehandler = "Z405060",
            saksbehandlerEnhet = "4291",
            tekniskTid = LocalDateTime.now(),
            vedtaksdato = LocalDate.now(),
            versjon = 1,
            ytelseType = "OMS",
        )

        println(objectMapper.writeValueAsString(statistikkTilDVH))
    }

}