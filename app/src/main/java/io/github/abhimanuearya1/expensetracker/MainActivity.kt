package io.github.abhimanuearya1.expensetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.abhimanuearya1.expensetracker.ui.ExpenseTrackerApp
import io.github.abhimanuearya1.expensetracker.ui.ExpenseTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the status and navigation bars; the screens add their own insets.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ExpenseTrackerTheme {
                ExpenseTrackerApp()
            }
        }
    }
}
