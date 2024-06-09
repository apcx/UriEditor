import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogState
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.Charset

@Composable
fun editor(input: String, onCloseRequest: (String) -> Unit) {
    var output by remember { mutableStateOf(input) }
    val dialogState = DialogState(WindowPosition.PlatformDefault, DpSize(800.dp, 600.dp))
    DialogWindow({ onCloseRequest(output) }, dialogState, title = "UriEditor") {
        Column(Modifier.padding(4.dp)) {
            val coroutineScope = rememberCoroutineScope()
            var pageJob: Job? by remember { mutableStateOf(null) }
            var jsonObject by remember { mutableStateOf(input.toObj()) }
            val editMap by remember { mutableStateOf(mutableMapOf<String, String>()) }
            Row {
                Button({
                    pageJob?.cancel()
                    output = input
                    jsonObject = input.toObj()
                    editMap.clear()
                }) { Text("Reset") }

                TextField(
                    output, {
                        output = it
                        pageJob?.cancel()
                        pageJob = coroutineScope.launch {
                            delay(1000)
                            jsonObject = it.toObj()
                            editMap.clear()
                        }
                    }, Modifier.fillMaxWidth().padding(start = 4.dp),
                    placeholder = { Text("paste uri or json here:") }, maxLines = 2,
                    colors = TextFieldDefaults.textFieldColors(if (input == output) Color.Black else Color.Blue)
                )
            }

            if (!jsonObject.isEmpty) Box {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    val rowModifier = Modifier.fillMaxWidth().border(0.5f.dp, Color.LightGray).padding(4.dp)
                    jsonObject.entrySet().forEach { entry ->
                        Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.key, Modifier.width(200.dp), overflow = TextOverflow.Ellipsis, maxLines = 2)

                            val itemScope = rememberCoroutineScope()
                            var itemJob: Job? by remember { mutableStateOf(null) }
                            val value = editMap[entry.key]
                                ?: (entry.value as? JsonPrimitive)?.asString ?: entry.value.toString()
                            TextField(value, {
                                editMap[entry.key] = it
                                itemJob?.cancel()
                                itemJob = itemScope.launch {
                                    delay(1000)
                                    jsonObject.onUpdate(entry.key, it)
                                    output = jsonObject.toUri()
                                    editMap.clear()
                                }
                            }, Modifier.padding(start = 4.dp).width(540.dp), maxLines = 2)

                            if (itemJob?.isActive != true && ("?" in value || value.firstOrNull() == '{')) {
                                var expanding by remember { mutableStateOf(false) }
                                IconButton({ expanding = true }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) }

                                if (expanding) editor(value) {
                                    expanding = false
                                    if (it != value) {
                                        jsonObject.onUpdate(entry.key, it)
                                        output = jsonObject.toUri()
                                        editMap.clear()
                                    }
                                }
                            }
                        }
                    }
                }

                if (pageJob?.isActive == true) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.6f)).clickable {}, Alignment.Center) {
                        Text("Typing ...", color = Color.White)
                    }
                }
            }
        }
    }
}

private fun String.toObj(): JsonObject = try {
    val uri = URI(this)
    val jsonObject = JsonObject()
    jsonObject["scheme"] = uri.scheme
    jsonObject["authority"] = uri.authority
    jsonObject["path"] = uri.path
    jsonObject["fragment"] = uri.fragment
    uri.rawQuery?.split("&")?.forEach {
        val parameter = it.split("=", limit = 2).takeIf { it.size == 2 } ?: return@forEach
        val key = parameter[0]
        val value = URLDecoder.decode(parameter[1], Charset.defaultCharset())
        when (val oldValue = jsonObject[key]) {
            null -> jsonObject[key] = value
            is JsonArray -> oldValue.add(JsonPrimitive(value))
            else -> {
                val jsonArray = JsonArray()
                jsonArray.add(oldValue)
                jsonArray.add(JsonPrimitive(value))
                jsonObject.add(key, jsonArray)
            }
        }
    }
    jsonObject
} catch (e: URISyntaxException) {
    try {
        JsonParser.parseString(this).asJsonObject
    } catch (e: RuntimeException) {
        JsonObject()
    }
}

private fun JsonObject.onUpdate(key: String, value: String) = try {
    when (val oldValue = get(key)) {
        is JsonPrimitive -> when {
            oldValue.isBoolean -> addProperty(key, value.toBoolean())
            oldValue.isNumber -> addProperty(key, value.toDouble())
            else -> addProperty(key, value)
        }

        else -> add(key, JsonParser.parseString(value))
    }
} catch (e: RuntimeException) {
    addProperty(key, value)
}

private fun JsonObject.toUri() = catching {
    val path = getString("path") ?: return@catching null
    val scheme = getString("scheme")?.let { "$it://" } ?: ""
    val authority = getString("authority") ?: ""
    val fragment = getString("fragment")?.let { "#$it" } ?: ""

    val charset = Charset.defaultCharset()
    val query = entrySet().mapNotNull {
        if (it.key == "scheme" || it.key == "authority" || it.key == "path" || it.key == "fragment") return@mapNotNull null
        it.key to ((it.value as? JsonPrimitive)?.asString ?: it.value.toString())
    }.joinToString("&") { "${it.first}=${URLEncoder.encode(it.second, charset)}" }

    "$scheme$authority$path${if (query.isEmpty()) "" else "?$query"}$fragment"
} ?: toString()