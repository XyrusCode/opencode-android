package com.xyrus.opencode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xyrus.opencode.BridgeViewModel
import com.xyrus.opencode.ModelChoice
import com.xyrus.opencode.PartItem
import com.xyrus.opencode.PickerTarget
import com.xyrus.opencode.TextItem
import com.xyrus.opencode.TranscriptItem
import com.xyrus.opencode.UiState
import com.xyrus.opencode.bridge.Agent
import com.xyrus.opencode.bridge.PermissionPrompt
import com.xyrus.opencode.bridge.Project
import com.xyrus.opencode.bridge.Provider
import com.xyrus.opencode.bridge.QuestionPrompt
import com.xyrus.opencode.ui.components.GlassButton
import com.xyrus.opencode.ui.components.GlowButton
import com.xyrus.opencode.ui.components.SectionHeader
import com.xyrus.opencode.ui.theme.Accent
import com.xyrus.opencode.ui.theme.Bg
import com.xyrus.opencode.ui.theme.BorderCol
import com.xyrus.opencode.ui.theme.Panel
import com.xyrus.opencode.ui.theme.Panel2
import com.xyrus.opencode.ui.theme.PlexMono
import com.xyrus.opencode.ui.theme.TextCol
import com.xyrus.opencode.ui.theme.TextDim

/** The opencode wordmark, shown centered when a session has no messages. */
@Composable
private fun Wordmark(size: Int = 34) {
    Text(
        text = "opencode",
        style = MaterialTheme.typography.displaySmall.copy(letterSpacing = 3.sp),
        fontFamily = PlexMono,
        fontWeight = FontWeight.SemiBold,
        color = TextCol.copy(alpha = 0.85f),
        modifier = Modifier,
    )
}

/** Attached-session chat surface: transcript plus a pinned composer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(viewModel: BridgeViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.transcript.size, state.transcript.lastOrNull()?.let { (it as? TextItem)?.text?.length }) {
        if (state.transcript.isNotEmpty()) listState.animateScrollToItem(state.transcript.lastIndex)
    }

    Scaffold(
        modifier = modifier,
        containerColor = Bg,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = viewModel::disconnect) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextDim)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        state.attachedSession?.title ?: "opencode",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextCol,
                        maxLines = 1,
                    )
                    if (state.streaming) {
                        Text("working…", style = MaterialTheme.typography.bodySmall, color = Accent)
                    }
                }
            }
        },
        bottomBar = {
            Composer(
                draft = draft,
                onDraftChange = { draft = it },
                state = state,
                onSend = { viewModel.send(draft); draft = "" },
                onStop = viewModel::abort,
                onOpenModel = { viewModel.showPicker(PickerTarget.Model) },
                onOpenAgent = { viewModel.showPicker(PickerTarget.Agent) },
                onOpenProject = { viewModel.showPicker(PickerTarget.Project) },
            )
        },
    ) { padding ->
        if (state.transcript.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Wordmark()
                    Spacer(Modifier.padding(6.dp))
                    Text(
                        "New session",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextDim,
                    )
                }
            }
            return@Scaffold
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.transcript, key = { it.id }) { item ->
                when (item) {
                    is TextItem -> if (item.role == "user") UserBubble(item) else AssistantText(item)
                    is PartItem -> PartCard(item)
                }
            }
        }
    }

    when (state.openPicker) {
        PickerTarget.Model -> ModelSheet(
            providers = state.providers,
            selected = state.selectedModel,
            onPick = { m, p, label -> viewModel.chooseModel(m, p, label); viewModel.showPicker(null) },
            onDismiss = { viewModel.showPicker(null) },
        )
        PickerTarget.Agent -> AgentSheet(
            agents = state.agents,
            selected = state.selectedAgent,
            onPick = { viewModel.chooseAgent(it); viewModel.showPicker(null) },
            onDismiss = { viewModel.showPicker(null) },
        )
        PickerTarget.Project -> ProjectSheet(
            projects = state.projects,
            selected = state.selectedProject,
            onPick = { viewModel.chooseProject(it); viewModel.showPicker(null) },
            onDismiss = { viewModel.showPicker(null) },
        )
        null -> Unit
    }

    state.pendingPermission?.let { PermissionDialog(it) { r -> viewModel.replyPermission(r) } }
    state.pendingQuestion?.let { QuestionDialog(it) { a -> viewModel.replyQuestion(a) } }
}

// --- Transcript -------------------------------------------------------------

@Composable
private fun UserBubble(item: TextItem) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            text = item.text,
            style = MaterialTheme.typography.bodyMedium,
            color = TextCol,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Panel2)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun AssistantText(item: TextItem) {
    ChatMarkdown(text = item.text, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun PartCard(item: PartItem) {
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Panel)
            .border(1.dp, BorderCol, shape)
            .clickable { expanded = !expanded }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(partIcon(item.type), contentDescription = null, tint = if (item.isError) MaterialTheme.colorScheme.error else Accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                item.title,
                style = MaterialTheme.typography.labelLarge,
                color = TextCol,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            if (item.state == "running" || item.state == "pending") {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Accent)
            } else {
                Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = TextDim, modifier = Modifier.size(16.dp))
            }
        }
        if (item.subtitle.isNotBlank()) {
            Spacer(Modifier.padding(top = 4.dp))
            Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = TextDim, maxLines = 1)
        }
        if (item.body.isNotBlank()) {
            Spacer(Modifier.padding(top = 6.dp))
            if (expanded) {
                ChatMarkdown(text = item.body, modifier = Modifier.fillMaxWidth())
            } else {
                Text(
                    item.body.take(160),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim,
                    maxLines = 3,
                )
            }
        }
    }
}

private fun partIcon(type: String) = when (type) {
    "tool" -> Icons.Filled.Build
    "file" -> Icons.AutoMirrored.Filled.InsertDriveFile
    "patch", "snapshot" -> Icons.Filled.Difference
    else -> Icons.AutoMirrored.Filled.Article
}

// --- Composer ---------------------------------------------------------------

@Composable
private fun Composer(
    draft: String,
    onDraftChange: (String) -> Unit,
    state: UiState,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onOpenModel: () -> Unit,
    onOpenAgent: () -> Unit,
    onOpenProject: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Panel)
                .border(1.dp, BorderCol, shape)
                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                TextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    placeholder = { Text("Ask anything, / for commands, @ for context…", color = TextDim, maxLines = 1) },
                    modifier = Modifier.weight(1f),
                    maxLines = 5,
                    colors = transparentFieldColors(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextCol),
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (state.canSend || state.streaming) Accent else Panel2)
                        .clickable(enabled = state.streaming || (state.canSend && draft.isNotBlank())) {
                            if (state.streaming) onStop() else onSend()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.streaming) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop", tint = Bg, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = "Send", tint = if (state.canSend) Bg else TextDim, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        // opencode's mode chips, attached under the composer.
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ComposerChip(state.selectedModel?.label ?: "Model", onOpenModel)
            ComposerChip(state.selectedAgent ?: "Agent", onOpenAgent)
            ComposerChip(state.selectedProject?.name ?: "Project", onOpenProject)
        }
    }
}

@Composable
private fun ComposerChip(label: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onClick)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextDim, maxLines = 1)
        Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = TextDim, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    cursorColor = Accent,
)

// --- Picker sheets (with search) ---------------------------------------------

@Composable
private fun PickerRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = if (selected) Accent else TextCol, maxLines = 1)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextDim, maxLines = 1)
            }
        }
        if (selected) Icon(Icons.Filled.Check, contentDescription = "selected", tint = Accent, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SheetSearchField(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search", color = TextDim) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextDim) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = transparentFieldColors(),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextCol),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSheet(providers: List<Provider>, selected: ModelChoice?, onPick: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Panel) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            SheetSearchField(query, onQueryChange = { query = it })
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                providers.forEach { provider ->
                    val models = provider.models.filter {
                        query.isBlank() || (it.name ?: it.id.orEmpty()).contains(query, true) || it.id.orEmpty().contains(query, true)
                    }
                    if (models.isNotEmpty()) {
                        item(key = "p-${provider.id}") { SectionHeader(title = provider.name ?: provider.id.orEmpty()) }
                        items(models, key = { "${provider.id}-${it.id}" }) { model ->
                            val modelID = model.id.orEmpty()
                            val providerID = model.providerID ?: provider.id.orEmpty()
                            PickerRow(model.name ?: modelID, providerID, selected?.modelID == modelID && selected?.providerID == providerID) { onPick(modelID, providerID, model.name ?: modelID) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentSheet(agents: List<Agent>, selected: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Panel) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            SheetSearchField(query, onQueryChange = { query = it })
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                items(agents.filter { query.isBlank() || it.name.orEmpty().contains(query, true) || it.description.orEmpty().contains(query, true) }, key = { it.name.orEmpty() }) { agent ->
                    val name = agent.name.orEmpty()
                    PickerRow(name, agent.description, selected == name) { onPick(name) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectSheet(projects: List<Project>, selected: Project?, onPick: (Project) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Panel) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            SheetSearchField(query, onQueryChange = { query = it })
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                items(projects.filter { query.isBlank() || it.name.orEmpty().contains(query, true) || it.worktree.orEmpty().contains(query, true) }, key = { it.id.orEmpty() }) { project ->
                    PickerRow(project.name ?: project.id.orEmpty(), project.worktree, selected?.id == project.id) { onPick(project) }
                }
            }
        }
    }
}

// --- Interactive prompts ------------------------------------------------------

@Composable
private fun PermissionDialog(prompt: PermissionPrompt, onReply: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = { onReply("reject") },
        containerColor = Panel,
        title = { Text(prompt.permission ?: "Permission requested", color = TextCol) },
        text = {
            Column {
                Text("opencode wants to run a tool.", style = MaterialTheme.typography.bodyMedium, color = TextCol)
                if (prompt.patterns.isNotEmpty()) {
                    Spacer(Modifier.padding(top = 8.dp))
                    prompt.patterns.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = TextDim) }
                }
            }
        },
        confirmButton = { GlowButton("Allow once", onClick = { onReply("once") }) },
        dismissButton = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton("Reject", onClick = { onReply("reject") })
            GlassButton("Always", onClick = { onReply("always") })
        } },
    )
}

@Composable
private fun QuestionDialog(prompt: QuestionPrompt, onSubmit: (List<List<String>>) -> Unit) {
    var selections by remember(prompt.requestID) { mutableStateOf(prompt.questions.map { emptySet<String>() }) }
    val ready = selections.all { it.isNotEmpty() }
    AlertDialog(
        onDismissRequest = {},
        containerColor = Panel,
        title = { Text("opencode has a question", color = TextCol) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                prompt.questions.forEachIndexed { qi, question ->
                    if (qi > 0) Spacer(Modifier.padding(top = 12.dp))
                    question.header?.let { Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Accent) }
                    question.question?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextCol) }
                    Spacer(Modifier.padding(top = 4.dp))
                    question.options.forEach { option ->
                        val label = option.label ?: return@forEach
                        val checked = label in selections[qi]
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                selections = selections.mapIndexed { i, set ->
                                    if (i != qi) set else if (question.multiple == true) { if (label in set) set - label else set + label } else setOf(label)
                                }
                            }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(18.dp).clip(RoundedCornerShape(4.dp))
                                    .background(if (checked) Accent else Color.Transparent)
                                    .border(1.dp, if (checked) Accent else TextDim, RoundedCornerShape(4.dp)),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium, color = TextCol)
                        }
                    }
                }
            }
        },
        confirmButton = { GlowButton("Submit", onClick = { onSubmit(selections.map { it.toList() }) }, enabled = ready) },
    )
}