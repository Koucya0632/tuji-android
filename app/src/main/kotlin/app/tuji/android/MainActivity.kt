package app.tuji.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.tuji.android.core.design.TujiFace
import app.tuji.android.core.design.TujiTheme
import app.tuji.android.spike.FuriganaSpikeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as TujiApplication
        setContent {
            // M0 draws the Japanese catalogue, so the JP face is the one under
            // test. The real switch is 設定 → 語言, which arrives with M3.
            TujiTheme(face = TujiFace.JP) {
                FuriganaSpikeScreen(catalog = app.catalog)
            }
        }
    }
}
