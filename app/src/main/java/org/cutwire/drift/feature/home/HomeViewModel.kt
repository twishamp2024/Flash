package org.cutwire.drift.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.cutwire.drift.core.model.AspectRatio
import org.cutwire.drift.core.model.Project
import org.cutwire.drift.core.storage.ProjectRepository
import javax.inject.Inject

sealed interface HomeEvent {
    data class NavigateToEditor(val projectId: String) : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<HomeEvent>()
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    init {
        loadProjects()
    }

    private fun loadProjects() {
        viewModelScope.launch {
            projectRepository.getAllProjects().collect { projects ->
                _uiState.update {
                    it.copy(
                        recentProjects = projects,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun openNewProjectDialog() {
        _uiState.update {
            it.copy(
                showNewProjectDialog = true,
                newProjectName = "Project ${it.recentProjects.size + 1}",
                selectedAspectRatio = AspectRatio.RATIO_9_16
            )
        }
    }

    fun dismissNewProjectDialog() {
        _uiState.update { it.copy(showNewProjectDialog = false) }
    }

    fun setAspectRatio(aspectRatio: AspectRatio) {
        _uiState.update { it.copy(selectedAspectRatio = aspectRatio) }
    }

    fun setNewProjectName(name: String) {
        _uiState.update { it.copy(newProjectName = name) }
    }

    fun createProject() {
        val currentState = _uiState.value
        viewModelScope.launch {
            val project = projectRepository.createNewProject(
                name = currentState.newProjectName,
                aspectRatio = currentState.selectedAspectRatio
            )
            _uiState.update { it.copy(showNewProjectDialog = false) }
            _events.emit(HomeEvent.NavigateToEditor(project.id))
        }
    }

    fun confirmDeleteProject(project: Project) {
        _uiState.update { it.copy(projectToDelete = project) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(projectToDelete = null) }
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch {
            projectRepository.deleteProject(project.id)
            _uiState.update { it.copy(projectToDelete = null) }
        }
    }
}
