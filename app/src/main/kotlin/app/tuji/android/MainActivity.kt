package app.tuji.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.tuji.android.core.design.TujiFace
import app.tuji.android.core.design.TujiTheme
import io.github.jan.supabase.auth.handleDeeplinks

class MainActivity : ComponentActivity() {

    private val app: TujiApplication get() = application as TujiApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Apple sign-in leaves the app and comes back as a deep link. The
        // Activity is `singleTask`, so a cold return arrives here and a warm
        // one in onNewIntent — both have to be handled or the user lands back
        // in Tuji still signed out, with nothing on screen to say why.
        app.supabase.handleDeeplinks(intent)

        setContent {
            // The face for everything before the account is known: the
            // splash and the sign-in screens, which follow the device. Once
            // signed in, the shell re-themes to the account's own language —
            // see `ProvideAppLanguage` — and this outer theme stops deciding.
            TujiTheme(face = TujiFace.forUiLanguage(rememberDeviceLanguage().wire)) {
                TujiRoot(app)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        app.supabase.handleDeeplinks(intent)
    }
}
