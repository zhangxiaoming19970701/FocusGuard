package com.focusguard.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PinDots(valueLength: Int, maxLength: Int, dark: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(maxLength) { index ->
            Box(
                Modifier
                    .padding(6.dp)
                    .size(16.dp)
                    .background(
                        color = if (index < valueLength) {
                            if (dark) Color.White else MaterialTheme.colorScheme.primary
                        } else {
                            if (dark) Color.DarkGray else MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
fun PinPad(
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    maxLength: Int = 6,
    enabled: Boolean = true,
    dark: Boolean = false,
    confirmLabel: String = "确认"
) {
    val foreground = if (dark) Color.White else MaterialTheme.colorScheme.onSurface
    val buttonColors = if (dark) {
        ButtonDefaults.buttonColors(
            containerColor = Color(0xFF222222),
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF111111),
            disabledContentColor = Color.Gray
        )
    } else ButtonDefaults.buttonColors()

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PinDots(value.length, maxLength, dark)
        Spacer(Modifier.height(16.dp))
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9")
        ).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { digit ->
                    PinKey(digit, enabled, buttonColors) {
                        if (value.length < maxLength) onValueChange(value + digit)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                enabled = enabled && value.isNotEmpty(),
                onClick = { onValueChange(value.dropLast(1)) },
                modifier = Modifier.size(72.dp).semantics { contentDescription = "删除一位" }
            ) {
                Icon(Icons.Rounded.Backspace, contentDescription = null, tint = foreground)
            }
            PinKey("0", enabled, buttonColors) {
                if (value.length < maxLength) onValueChange(value + "0")
            }
            TextButton(
                enabled = enabled && value.length >= 4,
                onClick = onConfirm,
                modifier = Modifier.size(72.dp)
            ) {
                Text(confirmLabel, color = foreground, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PinKey(
    digit: String,
    enabled: Boolean,
    colors: androidx.compose.material3.ButtonColors,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .size(72.dp)
            .semantics { contentDescription = "数字 $digit" }
    ) {
        Text(digit, fontSize = 24.sp)
    }
}
