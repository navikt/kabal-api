package no.nav.klage.oppgave.util

import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.text.SimpleDateFormat

fun ourJsonMapper(): JsonMapper {
    val jsonMapper = JsonMapper
        .builder()
        .addModule(
            KotlinModule.Builder().build()
        )
        .defaultDateFormat(SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS"))
        .build()

    return jsonMapper
}