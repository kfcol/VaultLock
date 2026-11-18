package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AllowlistScope
import com.example.ui.VaultLockUiState
import com.example.ui.components.AppIconThumbnail
import com.example.ui.components.StatusPill

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AllowlistScreen(
  uiState: VaultLockUiState,
  onSelectScope: (AllowlistScope) -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onToggleShowSystemApps: (Boolean) -> Unit,
  onToggleShowOnlyAllowed: (Boolean) -> Unit,
  onToggleAppAllowed: (String, AllowlistScope, Boolean) -> Unit,
  onSelectAll: (List<String>, AllowlistScope) -> Unit,
  onSelectNone: (AllowlistScope) -> Unit,
  onCopyAllowlist: (AllowlistScope, AllowlistScope) -> Unit,
  modifier: Modifier = Modifier,
) {
  val effectiveScope =
    if (uiState.preferences.separateAllowlists) uiState.activeAllowlistTab
    else AllowlistScope.LOCK_NOW

  val currentAllowedSet =
    if (effectiveScope == AllowlistScope.LOCK_NOW) uiState.lockNowAllowedPackages
    else uiState.scheduledAllowedPackages

  val queryLower = uiState.allowlistSearchQuery.trim().lowercase()
  val filteredApps =
    remember(
      uiState.installedApps,
      queryLower,
      uiState.showSystemAppsInAllowlist,
      uiState.showOnlyAllowedInAllowlist,
      currentAllowedSet,
    ) {
      uiState.installedApps.filter { app ->
        val matchesSystem = uiState.showSystemAppsInAllowlist || !app.isSystemApp
        val matchesAllowed =
          !uiState.showOnlyAllowedInAllowlist || currentAllowedSet.contains(app.packageName)
        val matchesQuery =
          queryLower.isEmpty() ||
            app.label.lowercase().contains(queryLower) ||
            app.packageName.lowercase().contains(queryLower)
        matchesSystem && matchesAllowed && matchesQuery
      }
    }

  var showAlwaysAllowedDetails by remember { mutableStateOf(false) }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 600.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Allowed Apps",
              style = MaterialTheme.typography.displaySmall,
              color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
              text = "No app limit — allow everything you actually need.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          StatusPill(
            text = "${currentAllowedSet.size} allowed",
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            dotColor = MaterialTheme.colorScheme.secondary,
          )
        }
      }

      if (uiState.preferences.separateAllowlists) {
        item {
          SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            AllowlistScope.entries.forEachIndexed { idx, scope ->
              val count =
                if (scope == AllowlistScope.LOCK_NOW) uiState.lockNowAllowedPackages.size
                else uiState.scheduledAllowedPackages.size
              SegmentedButton(
                selected = effectiveScope == scope,
                onClick = { onSelectScope(scope) },
                shape = SegmentedButtonDefaults.itemShape(index = idx, count = 2),
                label = { Text("${scope.displayTitle} ($count)") },
              )
            }
          }
        }
      }

      // Always allowed info card
      item {
        Surface(
          shape = MaterialTheme.shapes.medium,
          color = MaterialTheme.colorScheme.surfaceContainer,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier =
            Modifier.fillMaxWidth()
              .clip(MaterialTheme.shapes.medium)
              .clickable { showAlwaysAllowedDetails = !showAlwaysAllowedDetails },
        ) {
          Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Always Allowed (Core System)",
                  style = MaterialTheme.typography.titleMedium,
                )
                Text(
                  text = "Launcher, System UI, Keyboards, Dialer, Emergency & VaultLock",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Icon(
                imageVector =
                  if (showAlwaysAllowedDetails) Icons.Filled.ExpandLess
                  else Icons.Filled.ExpandMore,
                contentDescription = "Toggle details",
              )
            }

            AnimatedVisibility(visible = showAlwaysAllowedDetails) {
              Column(
                modifier = Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Text(
                  text =
                    "Resolved dynamically (${uiState.alwaysAllowedPackages.size} packages) so your keyboard, home launcher, incoming calls, and emergency dialer never lock you out.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }

      // Search & Filters
      item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = uiState.allowlistSearchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search installed apps or packages...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
              if (uiState.allowlistSearchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchQueryChange("") }) {
                  Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                }
              }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().testTag("allowlist_search_input"),
          )

          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            FilterChip(
              selected = uiState.showOnlyAllowedInAllowlist,
              onClick = { onToggleShowOnlyAllowed(!uiState.showOnlyAllowedInAllowlist) },
              label = { Text("Allowed Only") },
            )
            FilterChip(
              selected = uiState.showSystemAppsInAllowlist,
              onClick = { onToggleShowSystemApps(!uiState.showSystemAppsInAllowlist) },
              label = { Text("Include System Apps") },
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedButton(
              onClick = {
                onSelectAll(filteredApps.map { it.packageName }, effectiveScope)
              },
              modifier = Modifier.weight(1f).testTag("select_all_apps_button"),
              shape = RoundedCornerShape(12.dp),
            ) {
              Icon(Icons.Filled.AllInclusive, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.size(6.dp))
              Text("Select All (${filteredApps.size})")
            }

            OutlinedButton(
              onClick = { onSelectNone(effectiveScope) },
              modifier = Modifier.weight(1f).testTag("select_none_apps_button"),
              shape = RoundedCornerShape(12.dp),
            ) {
              Text("Select None")
            }
          }

          if (uiState.preferences.separateAllowlists) {
            val otherScope =
              if (effectiveScope == AllowlistScope.LOCK_NOW) AllowlistScope.SCHEDULED
              else AllowlistScope.LOCK_NOW
            TextButton(
              onClick = { onCopyAllowlist(otherScope, effectiveScope) },
              modifier = Modifier.align(Alignment.End),
            ) {
              Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.size(6.dp))
              Text("Copy from ${otherScope.displayTitle}")
            }
          }
        }
      }

      if (uiState.isLoadingApps) {
        item {
          Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator()
          }
        }
      } else if (filteredApps.isEmpty()) {
        item {
          Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(28.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text("No matching apps", style = MaterialTheme.typography.titleMedium)
              Text(
                text = "Try clearing your search or enabling 'Include System Apps'.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      } else {
        items(filteredApps, key = { it.packageName }) { app ->
          val isAlwaysAllowed = uiState.alwaysAllowedPackages.contains(app.packageName)
          val isAllowed = isAlwaysAllowed || currentAllowedSet.contains(app.packageName)

          Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border =
              BorderStroke(
                1.dp,
                if (isAllowed) MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.outlineVariant,
              ),
            modifier =
              Modifier.fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(enabled = !isAlwaysAllowed) {
                  onToggleAppAllowed(app.packageName, effectiveScope, !isAllowed)
                }
                .testTag("app_row_${app.packageName}"),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              AppIconThumbnail(
                packageName = app.packageName,
                contentDescription = app.label,
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = app.label,
                  style = MaterialTheme.typography.titleMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text =
                    if (isAlwaysAllowed) "Always allowed (core OS)"
                    else app.packageName,
                  style = MaterialTheme.typography.bodyMedium,
                  color =
                    if (isAlwaysAllowed) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              Switch(
                checked = isAllowed,
                enabled = !isAlwaysAllowed,
                onCheckedChange = { checked ->
                  onToggleAppAllowed(app.packageName, effectiveScope, checked)
                },
              )
            }
          }
        }
      }

      item { Spacer(Modifier.height(16.dp)) }
    }
  }
}
