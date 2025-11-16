import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import androidx.compose.ui.window.ComposeViewport
import com.threemoly.sample.ExampleApp

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        ExampleApp()
    }
}