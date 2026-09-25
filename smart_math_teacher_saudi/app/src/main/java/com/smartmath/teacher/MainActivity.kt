package com.smartmath.teacher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.smartmath.teacher.ui.screens.HomeScreen
import com.smartmath.teacher.ui.theme.SmartMathTeacherTheme
import com.smartmath.teacher.viewmodel.MathSolverViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MathSolverViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartMathTeacherTheme {
                // Force RTL Layout Direction for full Arabic native experience
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        HomeScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
