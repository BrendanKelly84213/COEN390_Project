package com.example.frontend.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

import com.example.frontend.components.TopBar
import com.example.frontend.ui.theme.sageGreen
import androidx.compose.ui.tooling.preview.Preview
import com.example.frontend.ui.theme.FrontendTheme

@Composable
fun ProfileScreen(
    onBackClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    userName: String = "SidWatch User",
    userEmail: String = "user@example.com"
) {
    Scaffold(
        containerColor = sageGreen,
        topBar = {
            TopBar(
                onSettingsClick = onSettingsClick,
                onLogoutClick = onLogoutClick,
                onBackClick = onBackClick
            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            Spacer(modifier = Modifier.height(12.dp))

            // Profile avatar
            Surface(
                modifier = Modifier.size(100.dp),
                shape = CircleShape,
                color = Color(0xFF2E5939)
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = userName
                            .split(" ")
                            .mapNotNull { it.firstOrNull()?.toString() }
                            .take(2)
                            .joinToString("")
                            .uppercase(),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineLarge
                    )
                }
            }

            // User name and email
            Text(
                text = userName,
                style = MaterialTheme.typography.headlineMedium
            )

            Text(
                text = userEmail,
                style = MaterialTheme.typography.bodyLarge
            )

            // Account information
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Account Information",
                        style = MaterialTheme.typography.titleLarge
                    )

                    HorizontalDivider()

                    ProfileInfoRow(
                        label = "Name",
                        value = userName
                    )

                    ProfileInfoRow(
                        label = "Email",
                        value = userEmail
                    )

                }
            }

            // Edit profile button placeholder
            Button(
                onClick = {
                    // TODO: Implement profile editing
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E5939)
                )
            ) {
                Text("Edit Profile")
            }
        }
    }
}

@Composable
private fun ProfileInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ProfileScreenPreview() {
    FrontendTheme {
        ProfileScreen(
            userName = "Scott Svendsen",
            userEmail = "scott@example.com"
        )
    }
}
