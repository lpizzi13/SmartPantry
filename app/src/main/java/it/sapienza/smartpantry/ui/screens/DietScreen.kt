package it.sapienza.smartpantry.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import it.sapienza.smartpantry.model.DietSection
import it.sapienza.smartpantry.model.DietViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietScreen(dietViewModel: DietViewModel = viewModel()) {
    val uiState by dietViewModel.uiState.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = menuExpanded,
            onExpandedChange = { menuExpanded = !menuExpanded }
        ) {
            OutlinedTextField(
                value = uiState.selectedSection.label,
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpanded)
                },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                uiState.sections.forEach { section ->
                    DropdownMenuItem(
                        text = { Text(section.label) },
                        onClick = {
                            dietViewModel.onSectionSelected(section)
                            menuExpanded = false
                        }
                    )
                }
            }
        }

        when (uiState.selectedSection) {
            DietSection.WEEKLY_DIET_PLAN -> WeeklyDietPlanContent(
                daysOfWeek = uiState.daysOfWeek,
                expandedDayIndex = uiState.expandedDayIndex,
                onDayClicked = dietViewModel::onDayClicked
            )

            DietSection.NEW_DIET -> NewDietContent()
        }
    }
}

@Composable
private fun WeeklyDietPlanContent(
    daysOfWeek: List<String>,
    expandedDayIndex: Int?,
    onDayClicked: (Int) -> Unit
) {

    daysOfWeek.forEachIndexed { index, day ->
        val isExpanded = expandedDayIndex == index

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = day,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    TextButton(
                        onClick = { onDayClicked(index) }
                    ) {
                        Text(if (isExpanded) "Hide" else "Show")
                    }
                }

                if (isExpanded) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    Text(
                        text = "Section for $day",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun NewDietContent() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize())
    }
}
