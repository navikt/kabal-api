package no.nav.klage.innsyn

class FileNotFoundInSafException(override val message: String = "Could not find document info in SAF"): RuntimeException()