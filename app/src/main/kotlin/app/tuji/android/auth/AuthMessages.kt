package app.tuji.android.auth

import androidx.annotation.StringRes
import app.tuji.android.R
import app.tuji.android.core.auth.AuthFailure

/**
 * The one place a failure becomes a sentence.
 *
 * It is in the app module, not in `core:auth`, because that is where string
 * resources live — and keeping it here is what lets the service speak in
 * decisions. A `when` with no `else` also means adding a case to [AuthFailure]
 * fails to compile until someone writes what it says, rather than silently
 * falling through to the generic line.
 */
@StringRes
fun AuthFailure.messageRes(): Int = when (this) {
    AuthFailure.InvalidCredentials -> R.string.auth_error_invalid_credentials
    AuthFailure.EmailAlreadyRegistered -> R.string.auth_error_email_taken
    AuthFailure.EmailNotConfirmed -> R.string.auth_error_email_not_confirmed
    AuthFailure.RateLimited -> R.string.auth_error_rate_limited
    AuthFailure.ProviderNotEnabled -> R.string.auth_error_provider_disabled
    AuthFailure.PasswordTooShort -> R.string.auth_error_password_too_short
    AuthFailure.InvalidEmail -> R.string.auth_error_invalid_email
    AuthFailure.Unknown -> R.string.auth_error_unknown
}
