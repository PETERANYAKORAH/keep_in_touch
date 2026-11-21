package app.kinkeep.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kinkeep.R
import app.kinkeep.ui.common.ImportPermissions

private val titles = listOf(R.string.onboarding_title_1, R.string.onboarding_title_2, R.string.onboarding_title_3)
private val bodies = listOf(R.string.onboarding_body_1, R.string.onboarding_body_2, R.string.onboarding_body_3)

/** Three short pages. The last one asks for access; Skip lets people look around first. */
@Composable
fun OnboardingScreen(onFinish: (importNow: Boolean) -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val last = titles.lastIndex
    val permissions = remember { ImportPermissions.list() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onFinish(true) }

    Surface(Modifier.fillMaxSize().systemBarsPadding(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(stringResource(R.string.onboarding_page, page + 1, titles.size), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(24.dp))
                Text(stringResource(titles[page]), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(bodies[page]), style = MaterialTheme.typography.bodyLarge)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (page < last) {
                    Button(onClick = { page++ }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_next)) }
                } else {
                    Button(onClick = { launcher.launch(permissions) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_allow)) }
                    TextButton(onClick = { onFinish(false) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_skip)) }
                }
                if (page > 0) TextButton(onClick = { page-- }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_back)) }
            }
        }
    }
}
