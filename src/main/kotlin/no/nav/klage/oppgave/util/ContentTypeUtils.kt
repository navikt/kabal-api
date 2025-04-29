package no.nav.klage.oppgave.util

import org.apache.tika.config.TikaConfig
import org.apache.tika.mime.MimeType
import org.springframework.http.MediaType

/**
 * Converts a media type to a file extension.
 *
 * @param mediaType The media type to convert.
 * @return The file extension corresponding to the media type. Including leading dot.
 */
fun mediaTypeToFileExtension(mediaType: MediaType): String {
    val config = TikaConfig.getDefaultConfig()

    val mimeType: MimeType = config.mimeRepository.forName(mediaType.toString())
    return mimeType.extension
}