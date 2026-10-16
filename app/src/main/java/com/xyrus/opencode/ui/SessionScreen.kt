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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.xyrus.opencode.TextItem
import com.xyrus.opencode.TranscriptItem
import com.xyrus.opencode.bridge.Agent
import com.xyrus.opencode.bridge.PermissionPrompt
import com.xyrus.opencode.bridge.Project
import com.xyrus.opencode.bridge.Provider
import com.xyrus.opencode.bridge.QuestionPrompt
import com.xyrus.opencode.ui.components.GlassButton
import com.xyrus.opencode.ui.components.GlassCard
import com.xyrus.opencode.ui.components.GlowButton
import com.xyrus.opencode.ui.components.SectionHeader
import com.xyrus.opencode.ui.theme.Accent

private enum class Picker { Model, Agent, Project }

/** Attached-session view: rich transcript, pickers, and interactive prompts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(viewModel: BridgeViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var draft by rememberSaveable { mutableStateOf("") }
    var picker by remember { mutableStateOf<Picker?>(null) }

    // Follow the stream as it grows, the way a terminal would.
    LaunchedEffect(state.transcript.size) {
        if (state.transcript.isNotEmpty()) {
            listState.animateScrollToItem(state.transcript.lastIndex)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                state.attachedSession?.title ?: "Session",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (state.streaming) {
                                Text(
                                    "working…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = viewModel::disconnect) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = { picker = Picker.Model },
                        label = {
                            Text(
                                state.selectedModel?.label
                                    ?: state.activeModel?.label
                                    ?: "Model",
                            )
                        },
                    )
                    AssistChip(
                        onClick = { picker = Picker.Agent },
                        label = { Text(state.selectedAgent ?: "Agent") },
                    )
                    AssistChip(
                        onClick = { picker = Picker.Project },
                        label = { Text(state.selectedProject?.name ?: "Project") },
                    )
                }
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("Message opencode") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                )
                IconButton(
                    onClick = {
                        if (state.streaming) viewModel.abort() else {
                            viewModel.send(draft)
                            draft = ""
                        }
                    },
                    enabled = state.streaming || (state.canSend && draft.isNotBlank()),
                ) {
                    if (state.streaming) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop")
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        },
    ) { padding ->
        if (state.transcript.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "opencode",
                        style = MaterialTheme.typography.displaySmall.copy(letterSpacing = 3.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "New session. Send a message to begin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.transcript, key = { it.id }) { item ->
                when (item) {
                    is TextItem -> TextRow(item)
                    is PartItem -> PartRow(item)
                }
            }
        }
    }

    when (picker) {
        Picker.Model -> ModelSheet(
            providers = state.providers,
            selected = state.selectedModel,
            onPick = { model, provider, label ->
                viewModel.chooseModel(model, provider, label)
                picker = null
            },
            onDismiss = { picker = null },
        )

        Picker.Agent -> AgentSheet(
            agents = state.agents,
            selected = state.selectedAgent,
            onPick = { viewModel.chooseAgent(it); picker = null },
            onDismiss = { picker = null },
        )

        Picker.Project -> ProjectSheet(
            projects = state.projects,
            selected = state.selectedProject,
            onPick = { viewModel.chooseProject(it); picker = null },
            onDismiss = { picker = null },
        )

        null -> Unit
    }

    state.pendingPermission?.let { prompt ->
        PermissionDialog(prompt) { reply -> viewModel.replyPermission(reply) }
    }

    state.pendingQuestion?.let { prompt ->
        QuestionDialog(prompt) { answers -> viewModel.replyQuestion(answers) }
    }
}

// --- Transcript rows ---------------------------------------------------------

@Composable
private fun TextRow(item: TextItem) {
    val isUser = item.role == "user"
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = if (isUser) "you" else "opencode",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = item.text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isUser) Accent else MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun PartRow(item: PartItem) {
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                if (item.subtitle.isNotBlank()) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (item.state == "running" || item.state == "pending") {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
            } else if (item.isError) {
                Text(
                    "error",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (item.body.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            val body = if (expanded) item.body else item.body.take(180)
            Text(
                text = if (expanded || item.body.length <= 180) body else "$body…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { expanded = !expanded },
            )
        }
    }
}

// --- Picker sheets -----------------------------------------------------------

@Composable
private fun PickerRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) Accent else MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = "selected", tint = Accent)
        }
    }
}

@Composable
private fun SheetSearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSheet(
    providers: List<Provider>,
    selected: ModelChoice?,
    onPick: (String, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            SheetSearchField(query, onQueryChange = { query = it })
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
            ) {
                providers.forEach { provider ->
                    val models = provider.models.filter { model ->
                        query.isBlank() ||
                            (model.name ?: model.id.orEmpty()).contains(query, ignoreCase = true) ||
                            model.id.orEmpty().contains(query, ignoreCase = true)
                    }
                    if (models.isNotEmpty()) {
                        item(key = "provider-${provider.id}") {
                            SectionHeader(title = provider.name ?: provider.id.orEmpty())
                        }
                        items(models, key = { "${provider.id}-${it.id}" }) { model ->
                            val modelID = model.id.orEmpty()
                            val providerID = model.providerID ?: provider.id.orEmpty()
                            PickerRow(
                                title = model.name ?: modelID,
                                subtitle = providerID,
                                selected = selected?.modelID == modelID &&
                                    selected?.providerID == providerID,
                                onClick = { onPick(modelID, providerID, model.name ?: modelID) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentSheet(
    agents: List<Agent>,
    selected: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            SheetSearchField(query, onQueryChange = { query = it })
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
            ) {
                items(
                    agents.filter { agent ->
                        query.isBlank() ||
                            agent.name.orEmpty().contains(query, ignoreCase = true) ||
                            agent.description.orEmpty().contains(query, ignoreCase = true)
                    },
                    key = { it.name.orEmpty() },
                ) { agent ->
                    val name = agent.name.orEmpty()
                    PickerRow(
                        title = name,
                        subtitle = agent.description,
                        selected = selected == name,
                        onClick = { onPick(name) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectSheet(
    projects: List<Project>,
    selected: Project?,
    onPick: (Project) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            SheetSearchField(query, onQueryChange = { query = it })
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
            ) {
                items(
                    projects.filter { project ->
                        query.isBlank() ||
                            project.name.orEmpty().contains(query, ignoreCase = true) ||
                            project.worktree.orEmpty().contains(query, ignoreCase = true)
                    },
                    key = { it.id.orEmpty() },
                ) { project ->
                    PickerRow(
                        title = project.name ?: project.id.orEmpty(),
                        subtitle = project.worktree,
                        selected = selected?.id == project.id,
                        onClick = { onPick(project) },
                    )
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
        title = { Text(prompt.permission ?: "Permission requested") },
        text = {
            Column {
                Text(
                    "opencode wants to run a tool.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (prompt.patterns.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    prompt.patterns.forEach { pattern ->
                        Text(
                            "• $pattern",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            GlowButton("Allow once", onClick = { onReply("once") })
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton("Reject", onClick = { onReply("reject") })
                GlassButton("Always", onClick = { onReply("always") })
            }
        },
    )
}

@Composable
private fun QuestionDialog(prompt: QuestionPrompt, onSubmit: (List<List<String>>) -> Unit) {
    var selections by remember(prompt.requestID) {
        mutableStateOf(prompt.questions.map { emptySet<String>() })
    }
    val ready = selections.all { it.isNotEmpty() }

    AlertDialog(
        onDismissRequest = {},
        title = { Text("opencode has a question") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                prompt.questions.forEachIndexed { qi, question ->
                    if (qi > 0) Spacer(Modifier.height(12.dp))
                    question.header?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Accent,
                        )
                    }
                    question.question?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(4.dp))
                    question.options.forEach { option ->
                        val label = option.label ?: return@forEach
                        val checked = label in selections[qi]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selections = selections.mapIndexed { i, set ->
                                        if (i != qi) {
                                            set
                                        } else if (question.multiple == true) {
                                            if (label in set) set - label else set + label
                                        } else {
                                            setOf(label)
                                        }
                                    }
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val boxColor = if (checked) Accent else Color.Transparent
                            val borderColor = if (checked) Accent else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(boxColor)
                                    .border(1.dp, borderColor, RoundedCornerShape(4.dp)),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            GlowButton("Submit", onClick = { onSubmit(selections.map { it.toList() }) }, enabled = ready)
        },
    )
}