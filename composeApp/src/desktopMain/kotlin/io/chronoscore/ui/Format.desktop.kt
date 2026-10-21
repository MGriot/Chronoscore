package io.chronoscore.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun formatDateTime(epochMillis: Long): String =
    SimpleDateFormat("d MMM yyyy · HH:mm", Locale.getDefault()).format(Date(epochMillis))
