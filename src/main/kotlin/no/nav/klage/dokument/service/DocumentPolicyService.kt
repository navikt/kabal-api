package no.nav.klage.dokument.service

import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeid
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsSmartdokument
import no.nav.klage.dokument.exceptions.DokumentValidationException
import no.nav.klage.dokument.repositories.DokumentUnderArbeidRepository
import no.nav.klage.dokument.util.DuaAccessPolicy
import no.nav.klage.dokument.util.DuaAccessPolicy.Creator
import no.nav.klage.kodeverk.FlowState
import no.nav.klage.oppgave.domain.klage.Behandling
import no.nav.klage.oppgave.domain.klage.BehandlingRole
import no.nav.klage.oppgave.exceptions.MissingTilgangException
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import org.springframework.stereotype.Service
import java.util.*

@Service
class DocumentPolicyService(
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val dokumentUnderArbeidRepository: DokumentUnderArbeidRepository,
) {

    fun validateDokumentUnderArbeidAction(
        behandling: Behandling,
        dokumentType: DuaAccessPolicy.DokumentType,
        parentDokumentType: DuaAccessPolicy.Parent,
        documentRole: BehandlingRole,
        action: DuaAccessPolicy.Action,
        duaMarkertFerdig: Boolean,
        isSystemContext: Boolean = false,
    ) {
        if (duaMarkertFerdig) {
            DuaAccessPolicy.throwDuaFinishedException()
        }

        if (behandling.feilregistrering != null) {
            DuaAccessPolicy.throwFeilregistrertException()
        }

        if (isSystemContext) {
            // If the action is performed in a system context, we allow all actions for now.
            return
        }

        val innloggetSaksbehandler = innloggetSaksbehandlerService.getInnloggetIdent()

        val user = when {
            behandling.ferdigstilling == null && innloggetSaksbehandler == behandling.tildeling?.saksbehandlerident -> DuaAccessPolicy.User.TILDELT_SAKSBEHANDLER
            behandling.ferdigstilling == null && innloggetSaksbehandler == behandling.medunderskriver?.saksbehandlerident -> DuaAccessPolicy.User.TILDELT_MEDUNDERSKRIVER
            behandling.ferdigstilling == null && innloggetSaksbehandler == behandling.rolIdent -> DuaAccessPolicy.User.TILDELT_ROL
            innloggetSaksbehandlerService.isROL() -> DuaAccessPolicy.User.ROL
            innloggetSaksbehandlerService.isSaksbehandler() -> DuaAccessPolicy.User.SAKSBEHANDLER
            else -> throw MissingTilgangException("Bruker har ikke tilgang til å håndtere dokumenter. Mangler rolle.")
        }

        val caseStatus = when {
            behandling.ferdigstilling != null -> DuaAccessPolicy.CaseStatus.FULLFOERT
            behandling.tildeling == null -> DuaAccessPolicy.CaseStatus.LEDIG
            behandling.medunderskriverFlowState != FlowState.SENT && behandling.rolFlowState != FlowState.SENT -> DuaAccessPolicy.CaseStatus.WITH_SAKSBEHANDLER
            behandling.medunderskriverFlowState == FlowState.SENT && behandling.tildeling != null && behandling.rolFlowState == FlowState.SENT -> DuaAccessPolicy.CaseStatus.WITH_MU_AND_ROL
            behandling.medunderskriverFlowState == FlowState.SENT && behandling.tildeling != null && behandling.rolFlowState == FlowState.RETURNED -> DuaAccessPolicy.CaseStatus.WITH_MU_AND_RETURNED_FROM_ROL
            behandling.rolFlowState == FlowState.RETURNED -> DuaAccessPolicy.CaseStatus.RETURNED_FROM_ROL
            behandling.medunderskriverFlowState == FlowState.SENT -> DuaAccessPolicy.CaseStatus.WITH_MU
            behandling.rolFlowState == FlowState.SENT -> DuaAccessPolicy.CaseStatus.WITH_ROL
            else -> throw DokumentValidationException("Ukjent case status for behandling med id ${behandling.id}")
        }

        DuaAccessPolicy.validateDuaAccess(
            user = user,
            caseStatus = caseStatus,
            documentType = dokumentType,
            parent = parentDokumentType,
            creator = Creator.valueOf(documentRole.name),
            action = action
        )
    }

    fun getParentDokumentType(
        parentDuaId: UUID?,
    ): DuaAccessPolicy.Parent {
        if (parentDuaId == null) {
            return DuaAccessPolicy.Parent.NONE
        } else {
            val dua = dokumentUnderArbeidRepository.findById(parentDuaId)
                .orElseThrow { DokumentValidationException("Finner ikke dokument med id $parentDuaId") }

            return if (dua is DokumentUnderArbeidAsSmartdokument) {
                when (dua.smartEditorTemplateId) {
                    "rol-questions" -> {
                        DuaAccessPolicy.Parent.ROL_QUESTIONS
                    }

                    "rol-answers" -> {
                        throw RuntimeException("ROL-svar kan ikke være forelder til et annet dokument")
                    }

                    else -> {
                        DuaAccessPolicy.Parent.SMART_DOCUMENT
                    }
                }
            } else {
                when (dua.getType()) {
                    DokumentUnderArbeid.DokumentUnderArbeidType.JOURNALFOERT -> throw RuntimeException("Journalført dokument kan ikke være forelder til et annet dokument")
                    DokumentUnderArbeid.DokumentUnderArbeidType.SMART -> DuaAccessPolicy.Parent.SMART_DOCUMENT
                    DokumentUnderArbeid.DokumentUnderArbeidType.UPLOADED -> DuaAccessPolicy.Parent.UPLOADED
                }
            }
        }
    }

    fun getDokumentType(
        duaId: UUID,
    ): DuaAccessPolicy.DokumentType {
        val dua = dokumentUnderArbeidRepository.findById(duaId)
            .orElseThrow { DokumentValidationException("Finner ikke dokument med id $duaId") }

        return if (dua is DokumentUnderArbeidAsSmartdokument) {
            when (dua.smartEditorTemplateId) {
                "rol-answers" -> {
                    DuaAccessPolicy.DokumentType.ROL_ANSWERS
                }

                "rol-questions" -> {
                    DuaAccessPolicy.DokumentType.ROL_QUESTIONS
                }

                else -> {
                    DuaAccessPolicy.DokumentType.SMART_DOCUMENT
                }
            }
        } else {
            when (dua.getType()) {
                DokumentUnderArbeid.DokumentUnderArbeidType.JOURNALFOERT -> DuaAccessPolicy.DokumentType.JOURNALFOERT
                DokumentUnderArbeid.DokumentUnderArbeidType.SMART -> DuaAccessPolicy.DokumentType.SMART_DOCUMENT
                DokumentUnderArbeid.DokumentUnderArbeidType.UPLOADED -> DuaAccessPolicy.DokumentType.UPLOADED
            }
        }
    }
}