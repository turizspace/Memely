package com.memely.ui.screens

import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable

import com.memely.breez.WalletRepository

@Composable
fun WalletScreen() {
    val repo = WalletRepository()
    Text("Balance: TODO")
    Button(onClick = { /* open send dialog */ }) { Text("Send") }
}
