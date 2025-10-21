package no.nav.klage.dokument.util

import no.nav.klage.dokument.exceptions.DokumentValidationException
import no.nav.klage.oppgave.exceptions.MissingDUARuleException
import no.nav.klage.oppgave.exceptions.MissingTilgangException
import no.nav.klage.oppgave.util.getLogger
import java.util.*

class DuaAccessPolicy {
    // 5 entries
    enum class User {
        SAKSBEHANDLER,
        TILDELT_SAKSBEHANDLER,
        TILDELT_MEDUNDERSKRIVER,
        ROL,
        TILDELT_ROL,
    }

    // 8 entries
    enum class CaseStatus {
        LEDIG,
        WITH_SAKSBEHANDLER,
        WITH_MU,
        WITH_ROL,
        RETURNED_FROM_ROL,
        WITH_MU_AND_ROL,
        WITH_MU_AND_RETURNED_FROM_ROL,
        FULLFOERT,
    }

    // 5 entries
    enum class DokumentType {
        SMART_DOCUMENT,
        UPLOADED,
        JOURNALFOERT,
        ROL_QUESTIONS,
        ROL_ANSWERS,
    }

    // 4 entries
    enum class Parent {
        NONE,
        SMART_DOCUMENT,
        UPLOADED,
        ROL_QUESTIONS,
    }

    // 4 entries
    enum class Creator {
        KABAL_SAKSBEHANDLING,
        KABAL_ROL,
        KABAL_MEDUNDERSKRIVER,
        NONE,
    }

    // 6 entries
    enum class Action {
        CREATE,
        WRITE,
        REMOVE,
        CHANGE_TYPE,
        RENAME,
        FINISH,
    }

    // 20 entries
    private enum class Access {
        ALLOWED,
        NOT_ASSIGNED,
        NOT_ASSIGNED_OR_MU,
        NOT_ASSIGNED_OR_ROL,
        NOT_ASSIGNED_ROL,
        NOT_SAKSBEHANDLER,
        ROL_REQUIRED,
        SENT_TO_MU,
        SENT_TO_ROL,
        SENT_TO_MU_AND_ROL,
        ROL_USER,
        RETURNED_FROM_ROL,
        NOT_SUPPORTED_ROL_QUESTIONS,
        NOT_SUPPORTED_ROL_ANSWERS,
        NOT_SUPPORTED_JOURNALFOERT,
        NOT_SUPPORTED_ATTACHMENT,
        NOT_SUPPORTED_UPLOADED,
        NOT_SUPPORTED_FINISHED,
        NOT_SUPPORTED,
        UNSET,
    }

    companion object {

        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)

        private lateinit var accessMap: Map<String, Access>
        private var accessMapInitialized = false

        fun initializeAccessMapFromCsv(resourcePath: String = "/dua/access_map.csv") {
            if (accessMapInitialized) {
                logger.warn("DUA access map already initialized with ${accessMap.size} entries")
                return
            }
            val start = System.currentTimeMillis()
            val inputStream = DuaAccessPolicy::class.java.getResourceAsStream(resourcePath)
                ?: throw IllegalStateException("Resource '$resourcePath' not found on classpath")

            //setting initial capacity to avoid resizing
            //4872 is the number of entries in the CSV file currently
            val newMap = HashMap<String, Access>(4872)
            inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val cols = line.split(',')
                    require(cols.size == 2) { "Bad CSV line (expected 2 columns): $line" }
                    newMap[cols[0]] = Access.valueOf(cols[1])
                }
            }
            check(newMap.isNotEmpty()) { "Access map loaded from '$resourcePath' is empty" }

            // Atomic publication of an immutable view
            accessMap = Collections.unmodifiableMap(newMap)

            accessMapInitialized = true

            logger.debug(
                "DUA access map initialized with ${newMap.size} entries from resource '$resourcePath' in ${System.currentTimeMillis() - start} ms"
            )
        }

        fun throwDuaFinishedException() {
            throw RuntimeException("Ferdigstilt dokument kan ikke endres. Kontakt Team Klage.")
        }

        fun throwFeilregistrertException() {
            throw RuntimeException("Behandlingen er feilregistrert.")
        }

        fun validateDuaAccess(
            user: User,
            caseStatus: CaseStatus,
            documentType: DokumentType,
            parent: Parent,
            creator: Creator,
            action: Action,
        ) {
            val key = "$user:$caseStatus:$documentType:$parent:$creator:$action"
            val access = accessMap[key]

            if (access == Access.ALLOWED) {
                return
            }

            if (access == null) {
                throw MissingDUARuleException("Handlingen er ikke mulig. Det finnes ikke regel for \"$key\". Kontakt Team Klage.")
            }

            val error = errorMessageMap["$access:$action"]
                ?: throw RuntimeException("Handlingen er ikke mulig. Feilmelding mangler for \"$access:$action\". Kontakt Team Klage.")

            logger.debug("Access denied for key '$key': $error")

            // 20 cases
            when (access) {
                Access.ALLOWED -> throw RuntimeException(error) //should not happen, as it is handled above
                Access.NOT_ASSIGNED -> throw MissingTilgangException(error)
                Access.NOT_ASSIGNED_OR_MU -> throw MissingTilgangException(error)
                Access.NOT_ASSIGNED_OR_ROL -> throw MissingTilgangException(error)
                Access.NOT_ASSIGNED_ROL -> throw MissingTilgangException(error)
                Access.NOT_SAKSBEHANDLER -> throw MissingTilgangException(error)
                Access.ROL_REQUIRED -> throw MissingTilgangException(error)
                Access.SENT_TO_MU -> throw MissingTilgangException(error)
                Access.SENT_TO_ROL -> throw MissingTilgangException(error)
                Access.SENT_TO_MU_AND_ROL -> throw MissingTilgangException(error)
                Access.ROL_USER -> throw MissingTilgangException(error)
                Access.RETURNED_FROM_ROL -> throw MissingTilgangException(error)
                Access.NOT_SUPPORTED_ROL_QUESTIONS -> throw DokumentValidationException(error)
                Access.NOT_SUPPORTED_ROL_ANSWERS -> throw DokumentValidationException(error)
                Access.NOT_SUPPORTED_JOURNALFOERT -> throw DokumentValidationException(error)
                Access.NOT_SUPPORTED_ATTACHMENT -> throw DokumentValidationException(error)
                Access.NOT_SUPPORTED_UPLOADED -> throw DokumentValidationException(error)
                Access.NOT_SUPPORTED_FINISHED -> throw DokumentValidationException(error)
                Access.NOT_SUPPORTED -> throw DokumentValidationException(error)
                Access.UNSET -> throw RuntimeException(error)
            }
        }

        // 120 entries
        private val errorMessageMap = hashMapOf(
            "ALLOWED:CREATE" to "Kan ikke opprette dokumentet. Teknisk feil. Kontakt Team Klage.",
            "NOT_ASSIGNED:CREATE" to "Kun tildelt saksbehandler kan opprette dokumentet.",
            "NOT_ASSIGNED_OR_MU:CREATE" to "Kun tildelt saksbehandler eller tilsendt medunderskriver kan opprette dokumentet.",
            "NOT_ASSIGNED_OR_ROL:CREATE" to "Kun tildelt saksbehandler eller tilsendt ROL kan opprette dokumentet.",
            "NOT_ASSIGNED_ROL:CREATE" to "Kun tilsendt ROL kan opprette dokumentet.",
            "NOT_SAKSBEHANDLER:CREATE" to "Kun saksbehandlere kan opprette dokumentet.",
            "ROL_REQUIRED:CREATE" to "Kan ikke opprette dokumentet før ROL har returnert saken.",
            "SENT_TO_MU:CREATE" to "Kan ikke opprette dokumentet fordi saken er sendt til medunderskriver.",
            "SENT_TO_ROL:CREATE" to "Kan ikke opprette dokumentet fordi saken er sendt til ROL.",
            "SENT_TO_MU_AND_ROL:CREATE" to "Kan ikke opprette dokumentet fordi saken er sendt til både MU og ROL.",
            "ROL_USER:CREATE" to "ROL kan ikke opprette dokumentet.",
            "RETURNED_FROM_ROL:CREATE" to "Kan ikke opprette dokumentet fordi saken er returnert til saksbehandler.",
            "NOT_SUPPORTED_ROL_QUESTIONS:CREATE" to "Kan ikke opprette dokumentet fordi det er Spørsmål til ROL.",
            "NOT_SUPPORTED_ROL_ANSWERS:CREATE" to "Kan ikke opprette dokumentet fordi det er Svar fra ROL.",
            "NOT_SUPPORTED_JOURNALFOERT:CREATE" to "Kan ikke opprette dokumentet fordi det er journalført.",
            "NOT_SUPPORTED_ATTACHMENT:CREATE" to "Kan ikke opprette dokumentet fordi det er et vedlegg.",
            "NOT_SUPPORTED_UPLOADED:CREATE" to "Kan ikke opprette dokumentet fordi det er opplastet.",
            "NOT_SUPPORTED_FINISHED:CREATE" to "Kan ikke opprette dokumentet fordi saken er ferdigstilt.",
            "NOT_SUPPORTED:CREATE" to "Det er umulig å opprette dokumentet.",
            "UNSET:CREATE" to "Kan ikke opprette dokumentet fordi tilgangen ikke er satt opp riktig. Kontakt Team Klage.",
            "ALLOWED:WRITE" to "Kan ikke skrive i innholdet i dokumentet. Teknisk feil. Kontakt Team Klage.",
            "NOT_ASSIGNED:WRITE" to "Kun tildelt saksbehandler kan skrive i innholdet i dokumentet.",
            "NOT_ASSIGNED_OR_MU:WRITE" to "Kun tildelt saksbehandler eller tilsendt medunderskriver kan skrive i innholdet i dokumentet.",
            "NOT_ASSIGNED_OR_ROL:WRITE" to "Kun tildelt saksbehandler eller tilsendt ROL kan skrive i innholdet i dokumentet.",
            "NOT_ASSIGNED_ROL:WRITE" to "Kun tilsendt ROL kan skrive i innholdet i dokumentet.",
            "NOT_SAKSBEHANDLER:WRITE" to "Kun saksbehandlere kan skrive i innholdet i dokumentet.",
            "ROL_REQUIRED:WRITE" to "Kan ikke skrive i innholdet i dokumentet før ROL har returnert saken.",
            "SENT_TO_MU:WRITE" to "Kan ikke skrive i innholdet i dokumentet fordi saken er sendt til medunderskriver.",
            "SENT_TO_ROL:WRITE" to "Kan ikke skrive i innholdet i dokumentet fordi saken er sendt til ROL.",
            "SENT_TO_MU_AND_ROL:WRITE" to "Kan ikke skrive i innholdet i dokumentet fordi saken er sendt til både MU og ROL.",
            "ROL_USER:WRITE" to "ROL kan ikke skrive i innholdet i dokumentet.",
            "RETURNED_FROM_ROL:WRITE" to "Kan ikke skrive i innholdet i dokumentet fordi saken er returnert til saksbehandler.",
            "NOT_SUPPORTED_ROL_QUESTIONS:WRITE" to "Kan ikke skrive i innholdet i dokumentet fordi det er Spørsmål til ROL.",
            "NOT_SUPPORTED_ROL_ANSWERS:WRITE" to "Kan ikke skrive i innholdet i dokumentet fordi det er Svar fra ROL.",
            "NOT_SUPPORTED_JOURNALFOERT:WRITE" to "Kan ikke skrive i innholdet i dokumentet fordi det er journalført.",
            "NOT_SUPPORTED_ATTACHMENT:WRITE" to "Kan ikke skrive i innholdet i dokumentet fordi det er et vedlegg.",
            "NOT_SUPPORTED_UPLOADED:WRITE" to "Kan ikke skrive i innholdet i dokumentet fordi det er opplastet.",
            "NOT_SUPPORTED_FINISHED:WRITE" to "Kan ikke skrive i innholdet i dokumentet fordi saken er ferdigstilt.",
            "NOT_SUPPORTED:WRITE" to "Det er umulig å skrive i innholdet i dokumentet.",
            "UNSET:WRITE" to "Kan ikke skrive i innholdet i dokumentet fordi tilgangen ikke er satt opp riktig. Kontakt Team Klage.",
            "ALLOWED:REMOVE" to "Kan ikke slette/fjerne dokumentet. Teknisk feil. Kontakt Team Klage.",
            "NOT_ASSIGNED:REMOVE" to "Kun tildelt saksbehandler kan slette/fjerne dokumentet.",
            "NOT_ASSIGNED_OR_MU:REMOVE" to "Kun tildelt saksbehandler eller tilsendt medunderskriver kan slette/fjerne dokumentet.",
            "NOT_ASSIGNED_OR_ROL:REMOVE" to "Kun tildelt saksbehandler eller tilsendt ROL kan slette/fjerne dokumentet.",
            "NOT_ASSIGNED_ROL:REMOVE" to "Kun tilsendt ROL kan slette/fjerne dokumentet.",
            "NOT_SAKSBEHANDLER:REMOVE" to "Kun saksbehandlere kan slette/fjerne dokumentet.",
            "ROL_REQUIRED:REMOVE" to "Kan ikke slette/fjerne dokumentet før ROL har returnert saken.",
            "SENT_TO_MU:REMOVE" to "Kan ikke slette/fjerne dokumentet fordi saken er sendt til medunderskriver.",
            "SENT_TO_ROL:REMOVE" to "Kan ikke slette/fjerne dokumentet fordi saken er sendt til ROL.",
            "SENT_TO_MU_AND_ROL:REMOVE" to "Kan ikke slette/fjerne dokumentet fordi saken er sendt til både MU og ROL.",
            "ROL_USER:REMOVE" to "ROL kan ikke slette/fjerne dokumentet.",
            "RETURNED_FROM_ROL:REMOVE" to "Kan ikke slette/fjerne dokumentet fordi saken er returnert til saksbehandler.",
            "NOT_SUPPORTED_ROL_QUESTIONS:REMOVE" to "Kan ikke slette/fjerne dokumentet fordi det er Spørsmål til ROL.",
            "NOT_SUPPORTED_ROL_ANSWERS:REMOVE" to "Kan ikke slette/fjerne dokumentet fordi det er Svar fra ROL.",
            "NOT_SUPPORTED_JOURNALFOERT:REMOVE" to "Kan ikke slette/fjerne dokumentet fordi det er journalført.",
            "NOT_SUPPORTED_ATTACHMENT:REMOVE" to "Kan ikke slette/fjerne dokumentet fordi det er et vedlegg.",
            "NOT_SUPPORTED_UPLOADED:REMOVE" to "Kan ikke slette/fjerne dokumentet fordi det er opplastet.",
            "NOT_SUPPORTED_FINISHED:REMOVE" to "Kan ikke slette/fjerne dokumentet fordi saken er ferdigstilt.",
            "NOT_SUPPORTED:REMOVE" to "Det er umulig å slette/fjerne dokumentet.",
            "UNSET:REMOVE" to "Kan ikke slette/fjerne dokumentet fordi tilgangen ikke er satt opp riktig. Kontakt Team Klage.",
            "ALLOWED:CHANGE_TYPE" to "Kan ikke endre type på dokumentet. Teknisk feil. Kontakt Team Klage.",
            "NOT_ASSIGNED:CHANGE_TYPE" to "Kun tildelt saksbehandler kan endre type på dokumentet.",
            "NOT_ASSIGNED_OR_MU:CHANGE_TYPE" to "Kun tildelt saksbehandler eller tilsendt medunderskriver kan endre type på dokumentet.",
            "NOT_ASSIGNED_OR_ROL:CHANGE_TYPE" to "Kun tildelt saksbehandler eller tilsendt ROL kan endre type på dokumentet.",
            "NOT_ASSIGNED_ROL:CHANGE_TYPE" to "Kun tilsendt ROL kan endre type på dokumentet.",
            "NOT_SAKSBEHANDLER:CHANGE_TYPE" to "Kun saksbehandlere kan endre type på dokumentet.",
            "ROL_REQUIRED:CHANGE_TYPE" to "Kan ikke endre type på dokumentet før ROL har returnert saken.",
            "SENT_TO_MU:CHANGE_TYPE" to "Kan ikke endre type på dokumentet fordi saken er sendt til medunderskriver.",
            "SENT_TO_ROL:CHANGE_TYPE" to "Kan ikke endre type på dokumentet fordi saken er sendt til ROL.",
            "SENT_TO_MU_AND_ROL:CHANGE_TYPE" to "Kan ikke endre type på dokumentet fordi saken er sendt til både MU og ROL.",
            "ROL_USER:CHANGE_TYPE" to "ROL kan ikke endre type på dokumentet.",
            "RETURNED_FROM_ROL:CHANGE_TYPE" to "Kan ikke endre type på dokumentet fordi saken er returnert til saksbehandler.",
            "NOT_SUPPORTED_ROL_QUESTIONS:CHANGE_TYPE" to "Kan ikke endre type på dokumentet fordi det er Spørsmål til ROL.",
            "NOT_SUPPORTED_ROL_ANSWERS:CHANGE_TYPE" to "Kan ikke endre type på dokumentet fordi det er Svar fra ROL.",
            "NOT_SUPPORTED_JOURNALFOERT:CHANGE_TYPE" to "Kan ikke endre type på dokumentet fordi det er journalført.",
            "NOT_SUPPORTED_ATTACHMENT:CHANGE_TYPE" to "Kan ikke endre type på dokumentet fordi det er et vedlegg.",
            "NOT_SUPPORTED_UPLOADED:CHANGE_TYPE" to "Kan ikke endre type på dokumentet fordi det er opplastet.",
            "NOT_SUPPORTED_FINISHED:CHANGE_TYPE" to "Kan ikke endre type på dokumentet fordi saken er ferdigstilt.",
            "NOT_SUPPORTED:CHANGE_TYPE" to "Det er umulig å endre type på dokumentet.",
            "UNSET:CHANGE_TYPE" to "Kan ikke endre type på dokumentet fordi tilgangen ikke er satt opp riktig. Kontakt Team Klage.",
            "ALLOWED:RENAME" to "Kan ikke endre navn på dokumentet. Teknisk feil. Kontakt Team Klage.",
            "NOT_ASSIGNED:RENAME" to "Kun tildelt saksbehandler kan endre navn på dokumentet.",
            "NOT_ASSIGNED_OR_MU:RENAME" to "Kun tildelt saksbehandler eller tilsendt medunderskriver kan endre navn på dokumentet.",
            "NOT_ASSIGNED_OR_ROL:RENAME" to "Kun tildelt saksbehandler eller tilsendt ROL kan endre navn på dokumentet.",
            "NOT_ASSIGNED_ROL:RENAME" to "Kun tilsendt ROL kan endre navn på dokumentet.",
            "NOT_SAKSBEHANDLER:RENAME" to "Kun saksbehandlere kan endre navn på dokumentet.",
            "ROL_REQUIRED:RENAME" to "Kan ikke endre navn på dokumentet før ROL har returnert saken.",
            "SENT_TO_MU:RENAME" to "Kan ikke endre navn på dokumentet fordi saken er sendt til medunderskriver.",
            "SENT_TO_ROL:RENAME" to "Kan ikke endre navn på dokumentet fordi saken er sendt til ROL.",
            "SENT_TO_MU_AND_ROL:RENAME" to "Kan ikke endre navn på dokumentet fordi saken er sendt til både MU og ROL.",
            "ROL_USER:RENAME" to "ROL kan ikke endre navn på dokumentet.",
            "RETURNED_FROM_ROL:RENAME" to "Kan ikke endre navn på dokumentet fordi saken er returnert til saksbehandler.",
            "NOT_SUPPORTED_ROL_QUESTIONS:RENAME" to "Kan ikke endre navn på dokumentet fordi det er Spørsmål til ROL.",
            "NOT_SUPPORTED_ROL_ANSWERS:RENAME" to "Kan ikke endre navn på dokumentet fordi det er Svar fra ROL.",
            "NOT_SUPPORTED_JOURNALFOERT:RENAME" to "Kan ikke endre navn på dokumentet fordi det er journalført.",
            "NOT_SUPPORTED_ATTACHMENT:RENAME" to "Kan ikke endre navn på dokumentet fordi det er et vedlegg.",
            "NOT_SUPPORTED_UPLOADED:RENAME" to "Kan ikke endre navn på dokumentet fordi det er opplastet.",
            "NOT_SUPPORTED_FINISHED:RENAME" to "Kan ikke endre navn på dokumentet fordi saken er ferdigstilt.",
            "NOT_SUPPORTED:RENAME" to "Det er umulig å endre navn på dokumentet.",
            "UNSET:RENAME" to "Kan ikke endre navn på dokumentet fordi tilgangen ikke er satt opp riktig. Kontakt Team Klage.",
            "ALLOWED:FINISH" to "Kan ikke arkivere / sende ut dokumentet. Teknisk feil. Kontakt Team Klage.",
            "NOT_ASSIGNED:FINISH" to "Kun tildelt saksbehandler kan arkivere / sende ut dokumentet.",
            "NOT_ASSIGNED_OR_MU:FINISH" to "Kun tildelt saksbehandler eller tilsendt medunderskriver kan arkivere / sende ut dokumentet.",
            "NOT_ASSIGNED_OR_ROL:FINISH" to "Kun tildelt saksbehandler eller tilsendt ROL kan arkivere / sende ut dokumentet.",
            "NOT_ASSIGNED_ROL:FINISH" to "Kun tilsendt ROL kan arkivere / sende ut dokumentet.",
            "NOT_SAKSBEHANDLER:FINISH" to "Kun saksbehandlere kan arkivere / sende ut dokumentet.",
            "ROL_REQUIRED:FINISH" to "Kan ikke arkivere / sende ut dokumentet før ROL har returnert saken.",
            "SENT_TO_MU:FINISH" to "Kan ikke arkivere / sende ut dokumentet fordi saken er sendt til medunderskriver.",
            "SENT_TO_ROL:FINISH" to "Kan ikke arkivere / sende ut dokumentet fordi saken er sendt til ROL.",
            "SENT_TO_MU_AND_ROL:FINISH" to "Kan ikke arkivere / sende ut dokumentet fordi saken er sendt til både MU og ROL.",
            "ROL_USER:FINISH" to "ROL kan ikke arkivere / sende ut dokumentet.",
            "RETURNED_FROM_ROL:FINISH" to "Kan ikke arkivere / sende ut dokumentet fordi saken er returnert til saksbehandler.",
            "NOT_SUPPORTED_ROL_QUESTIONS:FINISH" to "Kan ikke arkivere / sende ut dokumentet fordi det er Spørsmål til ROL.",
            "NOT_SUPPORTED_ROL_ANSWERS:FINISH" to "Kan ikke arkivere / sende ut dokumentet fordi det er Svar fra ROL.",
            "NOT_SUPPORTED_JOURNALFOERT:FINISH" to "Kan ikke arkivere / sende ut dokumentet fordi det er journalført.",
            "NOT_SUPPORTED_ATTACHMENT:FINISH" to "Kan ikke arkivere / sende ut dokumentet fordi det er et vedlegg.",
            "NOT_SUPPORTED_UPLOADED:FINISH" to "Kan ikke arkivere / sende ut dokumentet fordi det er opplastet.",
            "NOT_SUPPORTED_FINISHED:FINISH" to "Kan ikke arkivere / sende ut dokumentet fordi saken er ferdigstilt.",
            "NOT_SUPPORTED:FINISH" to "Det er umulig å arkivere / sende ut dokumentet.",
            "UNSET:FINISH" to "Kan ikke arkivere / sende ut dokumentet fordi tilgangen ikke er satt opp riktig. Kontakt Team Klage.",
        )
    }
}