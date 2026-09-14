package app.tuji.android.profile

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tuji.android.R
import app.tuji.android.core.community.ProfileEditFailure
import app.tuji.android.core.community.ProfileForm
import app.tuji.android.core.design.ProfileAvatar
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiNavBar
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable

/**
 * 編輯個人資料 — iOS's `EditProfileView`. One screen for the whole profile,
 * and everything on it is public: that is why there is nothing to consent to,
 * and why the UID sits at the bottom, unchangeable, as the thing every author
 * link points at.
 */
@Composable
fun EditProfileScreen(
    state: EditProfileViewModel.State,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onNickname: (String) -> Unit,
    onBio: (String) -> Unit,
    onImage: (ByteArray) -> Unit,
    onUseDefaultAvatar: () -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    val draft = state.draft

    Column(Modifier.fillMaxSize().background(TujiColor.Paper)) {
        TujiNavBar(
            onLeading = onBack,
            leadingLabel = stringResource(R.string.atlas_back),
            trailing = {
                Text(
                    stringResource(if (state.saving) R.string.profile_saving else R.string.profile_save),
                    style = TujiType.bodyStrong,
                    color = if (state.canSave) TujiColor.Ink else TujiColor.Ink3,
                    modifier = Modifier
                        .tujiClickable(enabled = state.canSave, onClick = onSave)
                        .padding(horizontal = TujiSpace.S2, vertical = TujiSpace.S3),
                )
            },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TujiSpace.S4)
                .padding(bottom = TujiSpace.S5),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S4),
        ) {
            HeroAvatar(state, enabled = !state.saving && !state.loading, onClick = { picking = true })

            Field(label = stringResource(R.string.profile_nickname)) {
                FormInput(
                    value = draft.nickname,
                    onValueChange = onNickname,
                    placeholder = stringResource(R.string.profile_nickname_placeholder),
                    valid = draft.nicknameValid,
                    enabled = !state.loading,
                )
                // Not required: an empty 暱稱 falls back to the UID, which is a
                // valid public identity rather than an error.
                Hint(stringResource(R.string.profile_nickname_hint))
            }

            Field(label = stringResource(R.string.profile_bio)) {
                FormInput(
                    value = draft.bio,
                    onValueChange = onBio,
                    placeholder = stringResource(R.string.profile_bio_placeholder),
                    valid = draft.bioValid,
                    enabled = !state.loading,
                    singleLine = false,
                )
                Row(Modifier.fillMaxWidth()) {
                    Hint(stringResource(R.string.profile_bio_hint), Modifier.weight(1f))
                    Text(
                        "${draft.bioRemaining}",
                        style = TujiType.label,
                        color = if (draft.bioValid) TujiColor.Ink3 else TujiColor.Alert,
                    )
                }
            }

            Field(label = "UID") {
                Text(
                    state.uid ?: "—",
                    style = TujiType.monoLabel,
                    color = TujiColor.Ink3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TujiColor.Paper2.copy(alpha = 0.06f))
                        .padding(TujiSpace.S3),
                )
                Hint(stringResource(R.string.profile_uid_hint))
            }

            state.failure?.let {
                Text(it.message(), style = TujiType.label, color = TujiColor.Alert, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (picking) {
        AvatarIntake(
            hasCustomAvatar = draft.hasCustomAvatar,
            onImage = onImage,
            onUseDefault = onUseDefaultAvatar,
            onClose = { picking = false },
        )
    }
}

@Composable
private fun HeroAvatar(state: EditProfileViewModel.State, enabled: Boolean, onClick: () -> Unit) {
    val label = stringResource(R.string.avatar_change)
    val preview = remember(state.pendingImage) {
        state.pendingImage?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = TujiSpace.S2)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
    ) {
        Box(Modifier.tujiClickable(enabled = enabled, onClick = onClick)) {
            val ring = Modifier.size(104.dp).clip(CircleShape).border(2.dp, TujiColor.Current, CircleShape)
            if (preview != null) {
                Image(preview, contentDescription = null, contentScale = ContentScale.Crop, modifier = ring)
            } else {
                Box(ring, contentAlignment = Alignment.Center) {
                    ProfileAvatar(avatar = state.draft.avatar.takeIf(ProfileForm::isPicture), size = 104.dp)
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(TujiColor.Paper)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(TujiColor.BrandPrimary),
                contentAlignment = Alignment.Center,
            ) {
                TujiGlyph.Camera(size = 14.dp, tint = TujiColor.Ink)
            }
        }
        Text(stringResource(R.string.profile_avatar_hint), style = TujiType.label, color = TujiColor.Ink3)
    }
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
        Text(label, style = TujiType.label.copy(letterSpacing = 2.sp), color = TujiColor.Ink3)
        content()
    }
}

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = TujiType.label, color = TujiColor.Ink3, modifier = modifier)
}

/** Paper ground and a rule, red while the value is over its limit — iOS's form field. */
@Composable
internal fun FormInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    valid: Boolean,
    enabled: Boolean,
    singleLine: Boolean = true,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        maxLines = if (singleLine) 1 else 4,
        textStyle = TujiType.bodySm.copy(color = TujiColor.Ink),
        cursorBrush = SolidColor(TujiColor.Current),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .background(TujiColor.Paper)
            .border(TujiBorder.Bw1, if (valid) TujiColor.Rule else TujiColor.Alert)
            .padding(TujiSpace.S3),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = TujiType.bodySm, color = TujiColor.Ink3)
                inner()
            }
        },
    )
}

@Composable
private fun ProfileEditFailure.message(): String = stringResource(
    when (this) {
        ProfileEditFailure.NicknameTooLong -> R.string.profile_error_nickname_long
        ProfileEditFailure.NicknameRejected -> R.string.profile_error_nickname_rejected
        ProfileEditFailure.BioTooLong -> R.string.profile_error_bio_long
        ProfileEditFailure.BioRejected -> R.string.profile_error_bio_rejected
        ProfileEditFailure.AvatarRejected -> R.string.profile_error_avatar_rejected
        ProfileEditFailure.ModerationUnavailable -> R.string.profile_error_moderation
        ProfileEditFailure.InvalidImage -> R.string.profile_error_image
        ProfileEditFailure.Failed -> R.string.profile_error_failed
    },
)
