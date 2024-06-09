import androidx.compose.material.MaterialTheme
import androidx.compose.ui.window.application
import java.io.File
import java.io.FileNotFoundException

fun main() {
    val file = File("UriEditor.txt")
    val fileText = try {
        file.readText()
    } catch (e: FileNotFoundException) {
        "https://cn.bing.com/"
    }
    application {
        MaterialTheme {
            editor(fileText) {
                if (it.isNotBlank() && it != fileText) catching {
                    file.createNewFile()
                    file.writeText(it)
                }
                exitApplication()
            }
        }
    }
}