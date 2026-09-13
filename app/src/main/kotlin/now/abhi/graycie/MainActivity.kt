package now.abhi.graycie

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import now.abhi.graycie.ui.GraycieTheme
import now.abhi.graycie.ui.ManagerScreen

class MainActivity : ComponentActivity() {
    private val controller by lazy { ManagerController.get(this) }
    private lateinit var viewModel: ManagerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(0x00000000),
            navigationBarStyle = SystemBarStyle.dark(0xff12100e.toInt()),
        )
        viewModel = ViewModelProvider(this, ManagerViewModel.factory(application))[ManagerViewModel::class.java]
        setContent {
            GraycieTheme {
                ManagerScreen(
                    viewModel = viewModel,
                    openAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The configuration activity itself must always remain in color.
        controller.engine.onForeground(packageName, true)
        controller.publishState()
        viewModel.onResume()
    }
}
