package org.cutwire.drift.engine.command

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.cutwire.drift.core.model.Project
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the stack of timeline editing commands for Undo and Redo operations.
 * Enforces a maximum history limit and exposes reactive StateFlows for UI state.
 */
@Singleton
class CommandManager @Inject constructor() {

    companion object {
        const val DEFAULT_MAX_HISTORY = 50
    }

    private val undoStack = ArrayDeque<EditCommand>()
    private val redoStack = ArrayDeque<EditCommand>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _lastAction = MutableStateFlow<String?>(null)
    val lastAction: StateFlow<String?> = _lastAction.asStateFlow()

    /**
     * Executes a new command on the given [currentProject], pushes it to the undo stack,
     * clears the redo stack, and returns the modified [Project].
     */
    fun execute(command: EditCommand, currentProject: Project): Project {
        val newProject = command.execute(currentProject)

        undoStack.addLast(command)
        if (undoStack.size > DEFAULT_MAX_HISTORY) {
            undoStack.removeFirst()
        }
        redoStack.clear()

        updateState(command.description)
        return newProject
    }

    /**
     * Undoes the most recent command and returns the restored [Project].
     * Returns [currentProject] unchanged if the undo stack is empty.
     */
    fun undo(currentProject: Project): Project {
        if (undoStack.isEmpty()) return currentProject

        val command = undoStack.removeLast()
        val restoredProject = command.undo(currentProject)
        redoStack.addLast(command)

        val nextAction = undoStack.lastOrNull()?.description
        updateState(nextAction)
        return restoredProject
    }

    /**
     * Redoes the most recently undone command and returns the re-applied [Project].
     * Returns [currentProject] unchanged if the redo stack is empty.
     */
    fun redo(currentProject: Project): Project {
        if (redoStack.isEmpty()) return currentProject

        val command = redoStack.removeLast()
        val restoredProject = command.execute(currentProject)
        undoStack.addLast(command)

        updateState(command.description)
        return restoredProject
    }

    /**
     * Clears all undo and redo history.
     */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
        updateState(null)
    }

    val undoCount: Int
        get() = undoStack.size

    val redoCount: Int
        get() = redoStack.size

    private fun updateState(lastDescription: String?) {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        _lastAction.value = lastDescription
    }
}
