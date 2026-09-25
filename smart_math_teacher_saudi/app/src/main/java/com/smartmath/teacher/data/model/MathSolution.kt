package com.smartmath.teacher.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MathSolution(
    @SerialName("is_saudi_curriculum")
    val isSaudiCurriculum: Boolean = true,

    @SerialName("rejection_reason")
    val rejectionReason: String? = null,

    @SerialName("grade_level")
    val gradeLevel: String = "",

    @SerialName("topic")
    val topic: String = "",

    @SerialName("notebook_steps")
    val notebookSteps: List<String> = emptyList(),

    @SerialName("teacher_explanation_shami")
    val teacherExplanationShami: String = ""
)

enum class TeacherVoice(val voiceName: String, val displayName: String, val gender: String) {
    LAITH("ar-SY-LaithNeural", "الأستاذ ليث (شامي)", "ذكر"),
    AMANY("ar-SY-AmanyNeural", "المعلمة أماني (شامية)", "أنثى")
}

sealed interface SolverUiState {
    data object Idle : SolverUiState
    data class Loading(val message: String) : SolverUiState
    data class Success(val solution: MathSolution) : SolverUiState
    data class NotSaudiCurriculum(val reason: String) : SolverUiState
    data class Error(val message: String) : SolverUiState
}
