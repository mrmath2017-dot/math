package com.smartmath.teacher.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartmath.teacher.data.gemini.GeminiMathService
import com.smartmath.teacher.data.model.MathSolution
import com.smartmath.teacher.data.model.SolverUiState
import com.smartmath.teacher.data.model.TeacherVoice
import com.smartmath.teacher.data.tts.EdgeTtsService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class MathSolverViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("smart_math_prefs", Context.MODE_PRIVATE)
    private val edgeTtsService = EdgeTtsService(application)
    private val geminiService = GeminiMathService(getSavedApiKey())

    private val _uiState = MutableStateFlow<SolverUiState>(SolverUiState.Idle)
    val uiState: StateFlow<SolverUiState> = _uiState.asStateFlow()

    private val _selectedBitmap = MutableStateFlow<Bitmap?>(null)
    val selectedBitmap: StateFlow<Bitmap?> = _selectedBitmap.asStateFlow()

    private val _questionText = MutableStateFlow("")
    val questionText: StateFlow<String> = _questionText.asStateFlow()

    private val _apiKey = MutableStateFlow(getSavedApiKey())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _selectedVoice = MutableStateFlow(TeacherVoice.LAITH)
    val selectedVoice: StateFlow<TeacherVoice> = _selectedVoice.asStateFlow()

    private val _isPlayingAudio = MutableStateFlow(false)
    val isPlayingAudio: StateFlow<Boolean> = _isPlayingAudio.asStateFlow()

    private val _isLoadingAudio = MutableStateFlow(false)
    val isLoadingAudio: StateFlow<Boolean> = _isLoadingAudio.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var currentAudioFile: File? = null

    private fun getSavedApiKey(): String {
        return prefs.getString("gemini_api_key", "") ?: ""
    }

    fun saveApiKey(newKey: String) {
        prefs.edit().putString("gemini_api_key", newKey.trim()).apply()
        _apiKey.value = newKey.trim()
        geminiService.updateApiKey(newKey.trim())
    }

    fun setQuestionText(text: String) {
        _questionText.value = text
    }

    fun setSelectedBitmap(bitmap: Bitmap?) {
        _selectedBitmap.value = bitmap
    }

    fun setVoice(voice: TeacherVoice) {
        if (_selectedVoice.value != voice) {
            _selectedVoice.value = voice
            // Reset current audio so next play uses the new voice
            stopAudio()
            currentAudioFile = null
        }
    }

    fun solve() {
        val text = _questionText.value.trim()
        val bitmap = _selectedBitmap.value

        if (text.isEmpty() && bitmap == null) {
            _uiState.value = SolverUiState.Error("يرجى تصوير المسألة أو كتابة نص التمرين أولاً.")
            return
        }

        if (_apiKey.value.isBlank()) {
            _uiState.value = SolverUiState.Error("يرجى إدخال مفتاح Gemini API في الإعدادات أعلى الشاشة.")
            return
        }

        viewModelScope.launch {
            stopAudio()
            currentAudioFile = null
            _uiState.value = SolverUiState.Loading("جاري فحص المسألة والتأكد من مطابقتها للمنهج السعودي...")

            try {
                val solution = geminiService.solveMathProblem(text, bitmap)

                if (!solution.isSaudiCurriculum) {
                    _uiState.value = SolverUiState.NotSaudiCurriculum(
                        solution.rejectionReason ?: "عذراً يا بطل، هذا التمرين خارج مفردات المناهج السعودية المقررة."
                    )
                } else {
                    _uiState.value = SolverUiState.Success(solution)
                    // Automatically pre-load voice audio
                    prepareTeacherAudio(solution.teacherExplanationShami)
                }
            } catch (e: Exception) {
                _uiState.value = SolverUiState.Error(e.localizedMessage ?: "حدث خطأ غير متوقع أثناء المعالجة")
            }
        }
    }

    private fun prepareTeacherAudio(script: String) {
        if (script.isBlank()) return
        viewModelScope.launch {
            _isLoadingAudio.value = true
            try {
                currentAudioFile = edgeTtsService.synthesizeSpeech(
                    text = script,
                    voiceName = _selectedVoice.value.voiceName
                )
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingAudio.value = false
            }
        }
    }

    fun toggleAudioPlayback() {
        if (_isPlayingAudio.value) {
            pauseAudio()
            return
        }

        val state = _uiState.value
        if (state !is SolverUiState.Success) return

        if (currentAudioFile != null && currentAudioFile!!.exists()) {
            playAudioFile(currentAudioFile!!)
        } else {
            // Synthesize and play
            viewModelScope.launch {
                _isLoadingAudio.value = true
                try {
                    val file = edgeTtsService.synthesizeSpeech(
                        text = state.solution.teacherExplanationShami,
                        voiceName = _selectedVoice.value.voiceName
                    )
                    currentAudioFile = file
                    playAudioFile(file)
                } catch (e: Exception) {
                    _uiState.value = SolverUiState.Error("تعذر تشغيل الصوت الشامي: ${e.message}")
                } finally {
                    _isLoadingAudio.value = false
                }
            }
        }
    }

    private fun playAudioFile(file: File) {
        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    prepare()
                    setOnCompletionListener {
                        _isPlayingAudio.value = false
                    }
                }
            }
            mediaPlayer?.start()
            _isPlayingAudio.value = true
        } catch (e: Exception) {
            e.printStackTrace()
            stopAudio()
        }
    }

    private fun pauseAudio() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _isPlayingAudio.value = false
            }
        }
    }

    fun stopAudio() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaPlayer = null
            _isPlayingAudio.value = false
        }
    }

    fun reset() {
        stopAudio()
        currentAudioFile = null
        _questionText.value = ""
        _selectedBitmap.value = null
        _uiState.value = SolverUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        stopAudio()
    }
}
