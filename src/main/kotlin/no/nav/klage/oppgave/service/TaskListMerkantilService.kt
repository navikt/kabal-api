package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.domain.klage.TaskListMerkantil
import no.nav.klage.oppgave.repositories.TaskListMerkantilRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
class TaskListMerkantilService(
    private val taskListMerkantilRepository: TaskListMerkantilRepository,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val saksbehandlerService: SaksbehandlerService,
) {

    @Transactional
    fun createTaskForMerkantil(behandlingId: UUID, reason: String) {
        taskListMerkantilRepository.save(
            TaskListMerkantil(
                behandlingId = behandlingId,
                reason = reason,
                created = LocalDateTime.now(),
                dateHandled = null,
                handledBy = null,
                handledByName = null,
                comment = null,
            )
        )
    }

    @Transactional
    fun setCommentAndMarkTaskAsCompleted(taskId: UUID, inputComment: String) {
        val task = taskListMerkantilRepository.findById(taskId)
            .orElseThrow { IllegalArgumentException("Task with id $taskId not found") }
        val innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
        val innloggetName = saksbehandlerService.getNameForIdentDefaultIfNull(
            navIdent = innloggetIdent
        )

        task.dateHandled = LocalDateTime.now()
        task.handledBy = innloggetIdent
        task.handledByName = innloggetName
        task.comment = inputComment
    }
}