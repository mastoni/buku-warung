package id.skmnetwork.bukuwarung.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.AppShapes
import id.skmnetwork.bukuwarung.ui.theme.AppSpacing

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    if (onClick != null) {
        Card(
            onClick = onClick,
            shape = AppShapes.CardShape,
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            modifier = modifier
        ) {
            content()
        }
    } else {
        Card(
            shape = AppShapes.CardShape,
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            modifier = modifier
        ) {
            content()
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = AppColors.GreenPrimary,
    contentColor: Color = Color.White
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = AppShapes.ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Text(text = text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = AppColors.RedExpense
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = AppShapes.ButtonShape,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Text(text = text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = isError,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        shape = AppShapes.TextFieldShape,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun AppEmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppColors.CardBorder,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(AppSpacing.md))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextSecondary,
                style = MaterialTheme.typography.titleMedium
            )
            if (description != null) {
                Spacer(Modifier.height(AppSpacing.xs))
                Text(
                    text = description,
                    color = AppColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (actionText != null && onActionClick != null) {
                Spacer(Modifier.height(AppSpacing.md))
                PrimaryButton(
                    text = actionText,
                    onClick = onActionClick,
                    modifier = Modifier.padding(horizontal = AppSpacing.xl)
                )
            }
        }
    }
}
