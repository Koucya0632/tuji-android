package app.tuji.android.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * A text field with a ground and a focus ring, and no underline.
 *
 * Material's `TextField` draws a filled container with a bottom indicator and a
 * floating label — a look that belongs to Material, not to 紙與墨. The
 * substitute is the system's own vocabulary: the field is [TujiColor.Paper2]
 * because that is what "input field" means in the paper scale, and focus is a
 * [TujiBorder.Bw2] ring in [TujiColor.Current] because with no radius and no
 * shadow, focus has to come from a stroke.
 */
@Composable
fun TujiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isPassword: Boolean = false,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val type = TujiType

    Column(modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = type.body.copy(color = TujiColor.Ink),
            cursorBrush = SolidColor(TujiColor.Current),
            keyboardOptions = keyboardOptions,
            visualTransformation =
                if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .background(TujiColor.Paper2)
                .border(
                    width = if (focused) TujiBorder.Bw2 else TujiBorder.Bw1,
                    color = if (focused) TujiColor.Current else TujiColor.Rule,
                    shape = RoundedCornerShape(TujiRadius.R0),
                )
                .padding(horizontal = TujiSpace.S3, vertical = TujiSpace.S2),
            decorationBox = { inner ->
                Box(contentAlignment = androidx.compose.ui.Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = type.body, color = TujiColor.Ink3)
                    }
                    inner()
                }
            },
        )
    }
}
