import com.google.gson.JsonObject

inline fun <R> catching(block: () -> R) = try {
    block()
} catch (e: Throwable) {
    null
}

operator fun JsonObject.set(property: String, value: String?) {
    if (value != null) addProperty(property, value)
}

fun JsonObject.getString(key: String) = getAsJsonPrimitive(key)?.asString