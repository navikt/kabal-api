package no.nav.klage.oppgave.domain.kafka

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.kjetland.jackson.jsonSchema.JsonSchemaConfig
import com.kjetland.jackson.jsonSchema.JsonSchemaDraft
import com.kjetland.jackson.jsonSchema.JsonSchemaGenerator
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal class KlageStatistikkTilDVHTest {

    //@Test
    fun createJsonSchema() {
        val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

        val config = JsonSchemaConfig.vanillaJsonSchemaDraft4().withJsonSchemaDraft(JsonSchemaDraft.DRAFT_07)

        val schemaGen = JsonSchemaGenerator(objectMapper, config)
        val jsonSchema = schemaGen.generateJsonSchema(KlageStatistikkTilDVH::class.java)

        println(objectMapper.writeValueAsString(jsonSchema))

        val klageStatistikkTilDVH = KlageStatistikkTilDVH(
            eventId = UUID.randomUUID(),
            ansvarligEnhetKode = "kode",
            ansvarligEnhetType = "type",
            avsender = "avsender",
            behandlingId = "arsta-rstz-xct-trstrst34-sft",
            behandlingIdKabal = "arst-arsdt-drt-j8z-89",
            behandlingStartetKA = LocalDate.now(),
            behandlingStatus = KlagebehandlingState.MOTTATT,
            behandlingType = "behType",
            beslutter = "beslutter",
            endringstid = LocalDateTime.now(),
            hjemmel = listOf("8-14"),
            klager = KlageStatistikkTilDVH.Part(verdi = "8005138513", KlageStatistikkTilDVH.PartIdType.VIRKSOMHET),
            opprinneligFagsaksystem = "K9Sak",
            overfoertKA = LocalDate.now(),
            resultat = "resultatet",
            sakenGjelder = KlageStatistikkTilDVH.Part(verdi = "20127529618", KlageStatistikkTilDVH.PartIdType.PERSON),
            saksbehandler = "Z405060",
            saksbehandlerEnhet = "4291",
            tekniskTid = LocalDateTime.now(),
            vedtakId = "ers-8sdt-80s-89u",
            vedtaksdato = LocalDate.now(),
            versjon = 1,
            ytelseType = "OMS"
        )

        println(objectMapper.writeValueAsString(klageStatistikkTilDVH))
    }

}