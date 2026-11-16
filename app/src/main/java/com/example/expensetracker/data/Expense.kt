package com.example.expensetracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single expense. Amounts are stored in the smallest currency unit
 * (e.g. cents) as a Long to avoid floating-point rounding errors.
 */
@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amountCents: Long,
    val category: String,
    val dateMillis: Long
)
