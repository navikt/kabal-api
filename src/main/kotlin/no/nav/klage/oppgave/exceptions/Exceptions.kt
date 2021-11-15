package no.nav.klage.oppgave.exceptions

class DuplicateOversendelseException(msg: String) : RuntimeException(msg)

class OppgaveNotFoundException(msg: String) : RuntimeException(msg)

class JournalpostNotFoundException(msg: String) : ValidationException(msg)

class DocumentNotFoundInStorageException(msg: String) : ValidationException(msg)

class UtfallNotSetException(msg: String) : ValidationException(msg)

class JournalpostFinalizationException(msg: String) : RuntimeException(msg)

class KlagebehandlingNotFoundException(msg: String) : RuntimeException(msg)

class VedtakNotFoundException(msg: String) : RuntimeException(msg)

class ResultatDokumentNotFoundException(msg: String) : RuntimeException(msg)

class BrevMottakerNotFoundException(msg: String) : RuntimeException(msg)

class MeldingNotFoundException(msg: String) : RuntimeException(msg)

class SaksdokumentNotFoundException(msg: String) : RuntimeException(msg)

class VedtakFinalizedException(msg: String) : RuntimeException(msg)

class KlagebehandlingFinalizedException(msg: String) : RuntimeException(msg)

open class ValidationException(msg: String) : RuntimeException(msg)

class OppgaveIdWrongFormatException(msg: String) : ValidationException(msg)

class OppgaveVersjonWrongFormatException(msg: String) : ValidationException(msg)

class BehandlingsidWrongFormatException(msg: String) : ValidationException(msg)

class NotMatchingUserException(msg: String) : RuntimeException(msg)

class FeatureNotEnabledException(msg: String) : RuntimeException(msg)

class NoSaksbehandlerRoleException(msg: String) : RuntimeException(msg)

class NotOwnEnhetException(msg: String) : RuntimeException(msg)

class MissingTilgangException(msg: String) : RuntimeException(msg)

class OversendtKlageNotValidException(msg: String) : RuntimeException(msg)

class OversendtKlageReceivedBeforeException(msg: String) : RuntimeException(msg)

class KlagebehandlingSamtidigEndretException(msg: String) : RuntimeException(msg)

class KlagebehandlingAvsluttetException(msg: String) : RuntimeException(msg)

class KlagebehandlingManglerMedunderskriverException(msg: String) : RuntimeException(msg)
