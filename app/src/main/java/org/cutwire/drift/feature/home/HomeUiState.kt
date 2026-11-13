package org.cutwire.drift.feature.home

import org.cutwire.drift.core.model.AspectRatio
import org.cutwire.drift.core.model.Project

data class HomeUiState(
    val recentProjects: List<Project> = emptyList(),
    val isLoading: Boolean = true,
    val showNewProjectDialog: Boolean = false,
    val selectedAspectRatio: AspectRatio = AspectRatio.RATIO_9_16,
    val newProjectName: String = "",
    val projectToDelete: Project? = null
)
