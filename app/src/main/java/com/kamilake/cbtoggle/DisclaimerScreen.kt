package com.kamilake.cbtoggle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kamilake.cbtoggle.core.R as CoreR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisclaimerScreen(onAccept: () -> Unit, onExit: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(CoreR.string.disclaimer_title)) }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(CoreR.string.disclaimer_body),
                    Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = onExit) { Text(stringResource(CoreR.string.disclaimer_exit)) }
                Button(onClick = onAccept) { Text(stringResource(CoreR.string.disclaimer_accept)) }
            }
        }
    }
}
