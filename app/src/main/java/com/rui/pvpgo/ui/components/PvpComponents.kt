package com.rui.pvpgo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rui.pvpgo.ui.theme.PvpColors
import com.rui.pvpgo.ui.theme.PvpRadius
import com.rui.pvpgo.ui.theme.PvpSpacing

@Composable
fun PvpScreen(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    listOf(PvpColors.CanvasStart, PvpColors.CanvasMiddle, PvpColors.CanvasEnd)
                )
            )
    ) {
        content()
    }
}

@Composable
fun PvpCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(PvpSpacing.Md),
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(PvpRadius.Card),
        colors = CardDefaults.cardColors(containerColor = PvpColors.SurfaceCard),
        border = BorderStroke(1.dp, PvpColors.BorderDefault)
    ) {
        Box(Modifier.padding(contentPadding)) { content() }
    }
}

@Composable
fun PvpSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        if (action != null && onAction != null) {
            Button(
                onClick = onAction,
                modifier = Modifier.heightIn(min = PvpSpacing.TouchTarget),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = PvpColors.BrandBlue
                ),
                contentPadding = PaddingValues(horizontal = PvpSpacing.Sm, vertical = PvpSpacing.Xs)
            ) {
                Text(action, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun PvpPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = PvpSpacing.TouchTarget),
        shape = RoundedCornerShape(PvpRadius.Button),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = PvpColors.SurfaceRaised,
            disabledContentColor = PvpColors.TextSecondary
        )
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PvpSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = PvpSpacing.TouchTarget)
            .border(1.dp, PvpColors.BorderDefault, RoundedCornerShape(PvpRadius.Button)),
        shape = RoundedCornerShape(PvpRadius.Button),
        colors = ButtonDefaults.buttonColors(
            containerColor = PvpColors.SurfaceRaised,
            contentColor = PvpColors.TextPrimary,
            disabledContainerColor = PvpColors.SurfaceCard,
            disabledContentColor = PvpColors.TextSecondary
        )
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PvpBadge(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = PvpColors.SurfaceRaised,
    foreground: Color = PvpColors.TextPrimary
) {
    Surface(
        modifier = modifier,
        color = background,
        contentColor = foreground,
        shape = RoundedCornerShape(PvpRadius.Small),
        border = BorderStroke(1.dp, background)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun PvpSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = PvpColors.TextSecondary) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PvpRadius.Card),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = PvpColors.SurfaceInput,
            unfocusedContainerColor = PvpColors.SurfaceInput,
            disabledContainerColor = PvpColors.SurfaceCard,
            focusedBorderColor = PvpColors.BrandBlue,
            unfocusedBorderColor = PvpColors.BorderDefault,
            focusedTextColor = PvpColors.TextPrimary,
            unfocusedTextColor = PvpColors.TextPrimary,
            cursorColor = PvpColors.BrandYellow
        )
    )
}

@Composable
fun PvpMetricRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PvpSpacing.Sm),
        content = content
    )
}

@Composable
fun PvpEmptyState(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    PvpCard(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(PvpSpacing.Sm)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = PvpColors.TextSecondary)
            action?.invoke()
        }
    }
}
