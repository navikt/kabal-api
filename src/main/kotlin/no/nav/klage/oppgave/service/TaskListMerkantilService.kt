package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.api.view.TaskListMerkantilView
import no.nav.klage.oppgave.domain.TaskListMerkantil
import no.nav.klage.oppgave.repositories.BehandlingRepository
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
    private val behandlingRepository: BehandlingRepository,
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
    fun setCommentAndMarkTaskAsCompleted(taskId: UUID, inputComment: String): TaskListMerkantilView {
        val task = taskListMerkantilRepository.findById(taskId)
            .orElseThrow { IllegalArgumentException("Task with id $taskId not found") }
        if (task.dateHandled != null) {
            throw IllegalStateException("Task with id $taskId is already handled")
        }
        val innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
        val innloggetName = saksbehandlerService.getNameForIdentDefaultIfNull(
            navIdent = innloggetIdent
        )

        task.dateHandled = LocalDateTime.now()
        task.handledBy = innloggetIdent
        task.handledByName = innloggetName
        task.comment = inputComment

        return task.toTaskListMerkantilView()
    }

    fun getTaskListMerkantil(): List<TaskListMerkantilView> {
        return taskListMerkantilRepository.findAll().sortedByDescending { it.created }
            .map { it.toTaskListMerkantilView() }
    }

    fun TaskListMerkantil.toTaskListMerkantilView(): TaskListMerkantilView {
        return TaskListMerkantilView(
            id = id,
            behandlingId = behandlingId,
            reason = reason,
            created = created,
            dateHandled = dateHandled,
            handledBy = handledBy,
            handledByName = handledByName,
            comment = comment,
            typeId = behandlingRepository.findByIdEager(behandlingId).type.id
        )
    }
}