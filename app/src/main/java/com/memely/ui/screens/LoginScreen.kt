package com.memely.ui.screens

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.memely.R
import com.memely.nostr.AmberSignerManager
import com.memely.nostr.KeyStoreManager
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    openUrl: (Intent) -> Unit
) {
    val scope = rememberCoroutineScope()
    var nsecInput by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var showNsecOption by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Logo
        Image(
            painter = painterResource(id = R.mipmap.ic_launcher_foreground),
            contentDescription = "Memely Logo",
            modifier = Modifier
                .size(120.dp)
                .padding(bottom = 16.dp)
        )
        
        Text("🔑 Login to Memely", style = MaterialTheme.typography.h6)
        Spacer(modifier = Modifier.height(24.dp))

        // --- Login with Amber (Recommended) ---
        if (!loading) {
            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        try {
                            // Register launcher for Amber
                            AmberSignerManager.registerActivityLauncher(openUrl)

                            status = "⚡ Waiting for Amber approval..."
                            val result = AmberSignerManager.requestPublicKey()

                            if (result.result != null) {
                                val packageName = result.packageName ?: "com.greenart7c3.nostrsigner"
                                AmberSignerManager.configure(result.result!!, packageName)
                                KeyStoreManager.saveExternalPubkey(result.result!!)
                                KeyStoreManager.saveAmberPackageName(packageName)
                                android.util.Log.d("MemelyLogin", "✅ Amber login successful. Pubkey: ${result.result}, Package: $packageName")
                                status = "✅ Amber login successful\nPubkey: ${result.result!!.take(12)}…"
                                onLoggedIn()
                            } else {
                                status = "❌ Amber did not return a pubkey."
                            }
                        } catch (e: Exception) {
                            status = "❌ Amber connection failed: ${e.message}"
                        } finally {
                            loading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Login with Amber Signer")
            }
        } else {
            CircularProgressIndicator(modifier = Modifier.padding(8.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- "I'm Feeling Reckless" button to reveal NSEC option ---
        if (!showNsecOption) {
            OutlinedButton(
                onClick = { showNsecOption = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colors.error
                ),
                border = androidx.compose.foundation.BorderStroke(
                    2.dp,
                    MaterialTheme.colors.error
                )
            ) {
                Text("☠️ I'm Feeling Reckless")
            }
        } else {
            // --- Login with NSEC (Not recommended) ---
            Text(
                text = "⚠️ Warning: Pasting your private key is insecure",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.error
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = nsecInput,
                onValueChange = { nsecInput = it.trim() },
                label = { Text("Enter your nsec...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = {
                    scope.launch {
                        try {
                            if (!nsecInput.startsWith("nsec")) {
                                status = "❌ Invalid NSEC format"
                                return@launch
                            }

                            KeyStoreManager.importNsec(nsecInput)
                            val pubHex = KeyStoreManager.getPubkeyHex()
                            Log.d("MemelyLogin", "✅ Logged in with NSEC. Pubkey: $pubHex")
                            status = "✅ Logged in with NSEC\nPubkey: ${pubHex?.take(12)}…"
                            
                            onLoggedIn()
                        } catch (e: Exception) {
                            status = "❌ Failed to decode NSEC: ${e.message}"
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text("Login with NSEC")
            }
        }


        Spacer(modifier = Modifier.height(16.dp))

        // --- Status message ---
        status?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.body2,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
