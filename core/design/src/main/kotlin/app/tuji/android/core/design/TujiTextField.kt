package app.tuji.android.core.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
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
    /**
     * What this field holds, for the password manager — iOS's
     * `textContentType`.
     *
     * Null for the fields nobody stores: a search box, a nickname, the name of
     * a 合集. Marking those is how a keyboard starts offering somebody's home
     * address where a word should go.
     */
    contentType: ContentType? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val edgeWidth by animateDpAsState(
        if (focused) TujiBorder.Bw2 else TujiBorder.Bw1,
        TujiMotion.ease(TujiMotion.D1),
        label = "fieldEdgeWidth",
    )
    val edgeColor by animateColorAsState(
        if (focused) TujiColor.Current else TujiColor.Rule,
        TujiMotion.ease(TujiMotion.D1),
        label = "fieldEdgeColor",
    )
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
                .then(
                    contentType?.let { type ->
                        Modifier.semantics { this.contentType = type }
                    } ?: Modifier,
                )
                .background(TujiColor.Paper2)
                // 瞳黃 means 現在, and a focused field is exactly where the
                // user is — so the edge arrives at the same D1 every other
                // state change in the app takes, rather than snapping.
                .border(
                    width = edgeWidth,
                    color = edgeColor,
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
