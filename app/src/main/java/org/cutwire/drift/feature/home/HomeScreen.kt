package org.cutwire.drift.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.cutwire.drift.core.designsystem.theme.*
import org.cutwire.drift.core.model.AspectRatio
import org.cutwire.drift.core.model.Project
import org.cutwire.drift.core.model.formatDuration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToEditor: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAboutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.NavigateToEditor -> onNavigateToEditor(event.projectId)
            }
        }
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }

    Scaffold(
        containerColor = DriftBackground,
        topBar = {
            HomeTopBar(onAboutClick = { showAboutDialog = true })
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openNewProjectDialog() },
                containerColor = DriftAccent,
                contentColor = DriftOnAccent,
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "New Project")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "New Project",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = DriftAccent
                )
            } else if (uiState.recentProjects.isEmpty()) {
                EmptyProjectsPlaceholder(
                    onNewProjectClick = { viewModel.openNewProjectDialog() }
                )
            } else {
                ProjectsGrid(
                    projects = uiState.recentProjects,
                    onProjectClick = onNavigateToEditor,
                    onDeleteClick = { viewModel.confirmDeleteProject(it) }
                )
            }
        }
    }

    // New Project Dialog / Sheet
    if (uiState.showNewProjectDialog) {
        NewProjectBottomSheet(
            selectedRatio = uiState.selectedAspectRatio,
            projectName = uiState.newProjectName,
            onNameChange = { viewModel.setNewProjectName(it) },
            onRatioSelected = { viewModel.setAspectRatio(it) },
            onCreateClick = { viewModel.createProject() },
            onDismiss = { viewModel.dismissNewProjectDialog() }
        )
    }

    // Delete Confirmation Dialog
    uiState.projectToDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteDialog() },
            containerColor = DriftSurfaceVariant,
            title = {
                Text(
                    text = "Delete Project?",
                    color = DriftTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${project.name}\"? This action cannot be undone.",
                    color = DriftTextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteProject(project) }
                ) {
                    Text("Delete", color = DriftError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissDeleteDialog() }
                ) {
                    Text("Cancel", color = DriftTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun HomeTopBar(onAboutClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Stylized Flash Logo Pill
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DriftAccent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = "Flash",
                    tint = DriftOnAccent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Flash",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = DriftTextPrimary
                )
                Text(
                    text = "Mobile Studio",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = DriftAccent
                )
            }
        }

        Surface(
            color = DriftSurfaceVariant,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DriftBorder),
            modifier = Modifier.clickable { onAboutClick() }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "About",
                    tint = DriftAccent,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "v1.0 • About",
                    fontSize = 11.sp,
                    color = DriftTextSecondary
                )
            }
        }
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DriftSurfaceVariant,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DriftAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = "Flash",
                        tint = DriftOnAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "About Flash",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DriftTextPrimary
                    )
                    Text(
                        text = "Version 1.0 (Build 1)",
                        fontSize = 12.sp,
                        color = DriftAccent
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Flash is a professional, mobile-first, native Android video editing workstation designed for touch ergonomics.",
                    fontSize = 13.sp,
                    color = DriftTextPrimary,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Engine Highlights:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = DriftAccent
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "• Multi-track timeline (Video, Audio, Text)\n• Microsecond integer timeline clock (Long Us)\n• Atomic Command-based Undo / Redo engine\n• Real-time OpenGL ES 3.0 GPU shader filters\n• Dynamic text overlays with Canvas ARGB_8888\n• Audio mixing, speed ramping & waveform analysis\n• On-device AI silence detection & auto subtitles\n• AndroidX Media3 Transformer hardware export\n• Zero watermarks, zero accounts, offline-first",
                    fontSize = 12.sp,
                    color = DriftTextSecondary,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "License:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = DriftAccent
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Released under the permissive MIT License.\nIndependent Clean-Room Architecture.",
                    fontSize = 12.sp,
                    color = DriftTextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "© 2026 twishamp2024. All rights reserved.",
                    fontSize = 11.sp,
                    color = DriftTextTertiary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = DriftAccent, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun EmptyProjectsPlaceholder(
    onNewProjectClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(DriftSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.VideoLibrary,
                contentDescription = null,
                tint = DriftAccent,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "No projects yet",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = DriftTextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Start creating by dropping in clips, trimming, adding effects, and exporting high-bitrate video.",
            fontSize = 14.sp,
            color = DriftTextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onNewProjectClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = DriftAccent,
                contentColor = DriftOnAccent
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create First Project", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ProjectsGrid(
    projects: List<Project>,
    onProjectClick: (String) -> Unit,
    onDeleteClick: (Project) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Recent Projects",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = DriftTextPrimary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(projects, key = { it.id }) { project ->
                ProjectCard(
                    project = project,
                    onClick = { onProjectClick(project.id) },
                    onDeleteClick = { onDeleteClick(project) }
                )
            }
        }
    }
}

@Composable
private fun ProjectCard(
    project: Project,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = DriftSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, DriftBorder)
    ) {
        Column {
            // Thumbnail Preview Placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(project.aspectRatio.floatRatio.coerceIn(0.7f, 1.8f))
                    .background(DriftSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = DriftTextTertiary,
                    modifier = Modifier.size(32.dp)
                )

                // Aspect ratio badge
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "${project.aspectRatio.widthRatio}:${project.aspectRatio.heightRatio}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = DriftAccent,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Duration badge
                if (project.durationUs > 0) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = project.durationUs.formatDuration(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = DriftTextPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Info row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DriftTextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = dateFormat.format(Date(project.updatedAt)),
                        fontSize = 11.sp,
                        color = DriftTextSecondary
                    )
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = DriftTextTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewProjectBottomSheet(
    selectedRatio: AspectRatio,
    projectName: String,
    onNameChange: (String) -> Unit,
    onRatioSelected: (AspectRatio) -> Unit,
    onCreateClick: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DriftSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = DriftBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "New Project",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = DriftTextPrimary
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = projectName,
                onValueChange = onNameChange,
                label = { Text("Project Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = DriftTextPrimary,
                    unfocusedTextColor = DriftTextPrimary,
                    focusedBorderColor = DriftAccent,
                    unfocusedBorderColor = DriftBorder,
                    focusedLabelColor = DriftAccent,
                    unfocusedLabelColor = DriftTextSecondary
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Canvas Aspect Ratio",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = DriftTextSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Aspect Ratio Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AspectRatio.values().forEach { ratio ->
                    val isSelected = ratio == selectedRatio
                    AspectRatioCard(
                        ratio = ratio,
                        isSelected = isSelected,
                        onClick = { onRatioSelected(ratio) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = onCreateClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DriftAccent,
                    contentColor = DriftOnAccent
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = "Start Editing",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AspectRatioCard(
    ratio: AspectRatio,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) DriftSurfaceVariant else DriftBackground)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) DriftAccent else DriftBorder,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Miniature ratio representation box
        Box(
            modifier = Modifier
                .height(30.dp)
                .aspectRatio(ratio.floatRatio)
                .background(if (isSelected) DriftAccent else DriftTextTertiary)
                .clip(RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${ratio.widthRatio}:${ratio.heightRatio}",
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) DriftAccent else DriftTextSecondary
        )
    }
}
