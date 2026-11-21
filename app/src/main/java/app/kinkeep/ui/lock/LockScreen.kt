package app.kinkeep.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kinkeep.R

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val bg = MaterialTheme.colorScheme.primary
    val fg = MaterialTheme.colorScheme.onPrimary
    Surface(Modifier.fillMaxSize(), color = bg) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displayMedium, color = fg)
            Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.titleMedium, color = fg)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onUnlock, colors = ButtonDefaults.buttonColors(containerColor = fg, contentColor = bg)) {
                Text(stringResource(R.string.lock_button))
            }
        }
    }
}
