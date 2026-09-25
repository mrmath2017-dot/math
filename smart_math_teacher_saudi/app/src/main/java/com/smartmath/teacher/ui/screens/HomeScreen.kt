package com.smartmath.teacher.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartmath.teacher.data.model.MathSolution
import com.smartmath.teacher.data.model.SolverUiState
import com.smartmath.teacher.data.model.TeacherVoice
import com.smartmath.teacher.ui.theme.*
import com.smartmath.teacher.viewmodel.MathSolverViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MathSolverViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val selectedBitmap by viewModel.selectedBitmap.collectAsState()
    val questionText by viewModel.questionText.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val selectedVoice by viewModel.selectedVoice.collectAsState()
    val isPlayingAudio by viewModel.isPlayingAudio.collectAsState()
    val isLoadingAudio by viewModel.isLoadingAudio.collectAsState()

    var showApiKeyDialog by remember { mutableStateOf(false) }

    // Camera Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            viewModel.setSelectedBitmap(bitmap)
        }
    }

    // Gallery Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, it))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                }
                viewModel.setSelectedBitmap(bitmap)
            } catch (e: Exception) {
                Toast.makeText(context, "فشل في تحميل الصورة", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = SaudiGold,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "∑",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "معلم الرياضيات الذكي",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = "المنهج السعودي • صوت شامي",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showApiKeyDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "مفتاح API",
                            tint = if (apiKey.isBlank()) SaudiGold else Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SaudiGreen
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(BackgroundLight)
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // API Key warning banner if not set
            if (apiKey.isBlank()) {
                item {
                    ApiKeyBanner(onSetupClicked = { showApiKeyDialog = true })
                }
            }

            // Input Selection Card
            item {
                InputCard(
                    questionText = questionText,
                    onTextChanged = viewModel::setQuestionText,
                    selectedBitmap = selectedBitmap,
                    onClearImage = { viewModel.setSelectedBitmap(null) },
                    onCapturePhoto = { cameraLauncher.launch(null) },
                    onPickGallery = { galleryLauncher.launch("image/*") },
                    selectedVoice = selectedVoice,
                    onVoiceSelected = viewModel::setVoice,
                    onSolveClicked = viewModel::solve,
                    isLoading = uiState is SolverUiState.Loading
                )
            }

            // Dynamic State Result View
            item {
                when (val state = uiState) {
                    is SolverUiState.Idle -> {
                        InfoCard()
                    }
                    is SolverUiState.Loading -> {
                        LoadingCard(message = state.message)
                    }
                    is SolverUiState.NotSaudiCurriculum -> {
                        NotSaudiCurriculumCard(
                            reason = state.reason,
                            onReset = viewModel::reset
                        )
                    }
                    is SolverUiState.Success -> {
                        SolutionResultSection(
                            solution = state.solution,
                            selectedVoice = selectedVoice,
                            isPlayingAudio = isPlayingAudio,
                            isLoadingAudio = isLoadingAudio,
                            onToggleAudio = viewModel::toggleAudioPlayback,
                            onCopySolution = {
                                val textToCopy = state.solution.notebookSteps.joinToString("\n")
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("خطوات الحل", textToCopy)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "تم نسخ خطوات الحل إلى الحافظة بنجاح!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    is SolverUiState.Error -> {
                        ErrorCard(
                            errorMessage = state.message,
                            onRetry = viewModel::solve
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showApiKeyDialog) {
        ApiKeyDialog(
            currentKey = apiKey,
            onDismiss = { showApiKeyDialog = false },
            onSave = { newKey ->
                viewModel.saveApiKey(newKey)
                showApiKeyDialog = false
            }
        )
    }
}

@Composable
private fun InputCard(
    questionText: String,
    onTextChanged: (String) -> Unit,
    selectedBitmap: Bitmap?,
    onClearImage: () -> Unit,
    onCapturePhoto: () -> Unit,
    onPickGallery: () -> Unit,
    selectedVoice: TeacherVoice,
    onVoiceSelected: (TeacherVoice) -> Unit,
    onSolveClicked: () -> Unit,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "اختر طريقة إدخال التمرين:",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )

            // Camera & Gallery Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onCapturePhoto,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = SaudiGreenLight),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تصوير المسألة", fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = onPickGallery,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("من المعرض", fontSize = 13.sp)
                }
            }

            // Image Preview if captured
            AnimatedVisibility(visible = selectedBitmap != null) {
                selectedBitmap?.let { bitmap ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "صورة المسألة",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = onClearImage,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "حذف الصورة",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Text Input
            OutlinedTextField(
                value = questionText,
                onValueChange = onTextChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("أو اكتب المسألة هنا (مثلاً: حل المعادلة ٢س + ٦ = ١٤)...") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SaudiGreen,
                    unfocusedBorderColor = Color.LightGray
                )
            )

            // Voice selection row
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "صوت المعلم الشامي:",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = TextSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TeacherVoice.values().forEach { voice ->
                        val isSelected = voice == selectedVoice
                        FilterChip(
                            selected = isSelected,
                            onClick = { onVoiceSelected(voice) },
                            label = { Text(voice.displayName, fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (voice == TeacherVoice.LAITH) Icons.Default.RecordVoiceOver else Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SaudiGreen.copy(alpha = 0.15f),
                                selectedLabelColor = SaudiGreenDark
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            // Big Solve Button
            Button(
                onClick = onSolveClicked,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SaudiGreen),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(imageVector = Icons.Default.AutoFixHigh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "فحص وحل وشرح المسألة",
                        style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
                    )
                }
            }
        }
    }
}

@Composable
private fun SolutionResultSection(
    solution: MathSolution,
    selectedVoice: TeacherVoice,
    isPlayingAudio: Boolean,
    isLoadingAudio: Boolean,
    onToggleAudio: () -> Unit,
    onCopySolution: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Curriculum & Grade Tag
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = SaudiGreen.copy(alpha = 0.1f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
                    Text(
                        text = "المنهج السعودي: ${solution.gradeLevel}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = SaudiGreenDark
                    )
                }
                Text(
                    text = solution.topic,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        // NOTEBOOK SOLUTION CARD (صافي للدفتر بدون حشو)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceLight),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.MenuBook, contentDescription = null, tint = SaudiGreen)
                        Text(
                            text = "خطوات الحل للدفتر (صافي):",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                    }

                    TextButton(onClick = onCopySolution) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("نسخ للدفتر", fontSize = 12.sp, color = SaudiGreen)
                    }
                }

                Text(
                    text = "هذه الخطوات نقية ومباشرة بدون حشو لتنقلها فوراً لكتابك أو دفترك:",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                // Clean steps container
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceCard,
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        solution.notebookSteps.forEachIndexed { index, step ->
                            val isLast = index == solution.notebookSteps.lastIndex
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isLast) SaudiGreen else SaudiGold,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${index + 1}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isLast) Color.White else TextPrimary
                                        )
                                    }
                                }

                                Text(
                                    text = step,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isLast) SaudiGreenDark else TextPrimary
                                    )
                                )
                            }
                            if (!isLast) {
                                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }

        // TEACHER VOICE CARD (باللهجة الشامية عبر EdgeTTS)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SaudiGreen.copy(alpha = 0.04f)),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.RecordVoiceOver, contentDescription = null, tint = SaudiGreen)
                        Column {
                            Text(
                                text = "شرح المعلم الصوتي (باللهجة الشامية):",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Text(
                                text = "صوت: ${selectedVoice.displayName} عبر EdgeTTS المجاني",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    // Play / Pause Button with animation
                    val transition = rememberInfiniteTransition(label = "pulse")
                    val pulseScale by transition.animateFloat(
                        initialValue = 1f,
                        targetValue = if (isPlayingAudio) 1.12f else 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "scale"
                    )

                    FilledIconButton(
                        onClick = onToggleAudio,
                        modifier = Modifier
                            .size(48.dp)
                            .scale(pulseScale),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isPlayingAudio) WarningOrange else SaudiGreen
                        ),
                        enabled = !isLoadingAudio
                    ) {
                        if (isLoadingAudio) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlayingAudio) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlayingAudio) "إيقاف مؤقت" else "تشغيل الشرح الصوتي",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Spoken Script Display
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceLight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = solution.teacherExplanationShami,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 22.sp,
                            color = TextPrimary
                        ),
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NotSaudiCurriculumCard(
    reason: String,
    onReset: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = WarningOrange.copy(alpha = 0.08f)),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = WarningOrange.copy(alpha = 0.2f),
                modifier = Modifier.size(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = WarningOrange,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Text(
                text = "المسألة خارج المنهج السعودي",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = WarningOrange
            )

            Text(
                text = reason,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = TextPrimary
            )

            Text(
                text = "هذا التطبيق مخصص لمناهج وزارة التعليم السعودية من الصف الأول الابتدائي حتى الصف الثالث ثانوي فقط.",
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = TextSecondary
            )

            Button(
                onClick = onReset,
                colors = ButtonDefaults.buttonColors(containerColor = WarningOrange),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("تجربة مسألة أخرى من المنهج")
            }
        }
    }
}

@Composable
private fun LoadingCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(
                color = SaudiGreen,
                strokeWidth = 3.dp,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun ErrorCard(errorMessage: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(imageVector = Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(36.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = ErrorRed
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("إعادة المحاولة")
            }
        }
    }
}

@Composable
private fun InfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = SaudiGreen.copy(alpha = 0.12f),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Default.School, contentDescription = null, tint = SaudiGreen)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "كيف يعمل التطبيق؟",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = "١. صوّر المسألة أو اكتبها\n٢. يتحقق التطبيق من كونها ضمن المنهج السعودي\n٣. يعطيك خطوات نقية ومباشرة للدفتر\n٤. يشرحها لك المعلم بصوته الشامي الحقيقي",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

@Composable
private fun ApiKeyBanner(onSetupClicked: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SaudiGold.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = Icons.Default.VpnKey, contentDescription = null, tint = SaudiGold)
                Text(
                    text = "يرجى إدخال مفتاح Gemini API المجاني للبدء في حل التمارين",
                    fontSize = 12.sp,
                    color = TextPrimary
                )
            }
            Button(
                onClick = onSetupClicked,
                colors = ButtonDefaults.buttonColors(containerColor = SaudiGreen),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("إدخال المفتاح", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ApiKeyDialog(
    currentKey: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var keyText by remember { mutableStateOf(currentKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("إعداد مفتاح Gemini API", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "احصل على المفتاح مجاناً من Google AI Studio والصقه هنا:",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    label = { Text("Gemini API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(keyText) },
                colors = ButtonDefaults.buttonColors(containerColor = SaudiGreen)
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
