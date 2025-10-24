package com.memely.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateTime {
    fun toReadable(ts: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(ts * 1000))
    }
}
