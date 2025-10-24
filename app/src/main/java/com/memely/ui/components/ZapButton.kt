package com.memely.ui.components

import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable

import com.memely.breez.BreezManager

@Composable
fun ZapButton(lud16: String?) {
    Button(onClick = {
        // TODO: show amount chooser and call BreezManager.sendZap
    }) {
        Text(text = "Zap ⚡")
    }
}
