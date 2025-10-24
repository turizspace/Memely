package com.memely.util

import org.json.JSONObject

object Json {
    fun safeGetString(obj: JSONObject, key: String): String? = if (obj.has(key)) obj.optString(key, null) else null
}
