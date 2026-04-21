package no.nav.klage.dokument.clients.clamav

data class ScanResult(
    val filename: String,
    val result: ClamAvResult,
    val virus: String = "",
    val error: String = "",
)

enum class ClamAvResult {
    FOUND, OK, ERROR
}