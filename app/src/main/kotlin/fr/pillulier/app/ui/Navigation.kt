package fr.pillulier.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import fr.pillulier.app.ui.aujourdhui.AujourdhuiEcran
import fr.pillulier.app.ui.medicaments.EditionEcran
import fr.pillulier.app.ui.medicaments.MedicamentsEcran
import fr.pillulier.app.ui.preferences.PreferencesEcran
import fr.pillulier.app.ui.semaine.SemaineEcran
import fr.pillulier.app.ui.stock.StockEcran

private data class Onglet(val route: String, val titre: String, val icone: ImageVector)

private val onglets = listOf(
    Onglet("aujourdhui", "Aujourd'hui", Icons.Filled.Today),
    Onglet("semaine", "Semaine", Icons.Filled.CalendarMonth),
    Onglet("medicaments", "Médicaments", Icons.Filled.Medication),
    Onglet("stock", "Stock", Icons.Filled.Inventory),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PillulierNavigation(controleur: NavHostController = rememberNavController()) {
    val entree by controleur.currentBackStackEntryAsState()
    val routeCourante = entree?.destination?.route

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(onglets.firstOrNull { it.route == routeCourante }?.titre ?: "Pillulier") },
                actions = {
                    IconButton(onClick = { controleur.navigate("preferences") }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Préférences")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                onglets.forEach { onglet ->
                    NavigationBarItem(
                        selected = routeCourante == onglet.route,
                        onClick = {
                            controleur.navigate(onglet.route) {
                                popUpTo(onglets.first().route)
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(onglet.icone, contentDescription = onglet.titre) },
                        label = { Text(onglet.titre) },
                    )
                }
            }
        },
    ) { rembourrage ->
        NavHost(
            navController = controleur,
            startDestination = "aujourdhui",
            modifier = Modifier.padding(rembourrage),
        ) {
            composable("aujourdhui") { AujourdhuiEcran() }
            composable("semaine") { SemaineEcran() }
            composable("medicaments") {
                MedicamentsEcran(surSelection = { id -> controleur.navigate("medicament/$id") })
            }
            composable("medicament/{id}") { entreeRoute ->
                EditionEcran(
                    medicamentId = entreeRoute.arguments?.getString("id")?.toLongOrNull() ?: 0L,
                    surSortie = { controleur.popBackStack() },
                )
            }
            composable("stock") { StockEcran() }
            composable("preferences") { PreferencesEcran() }
        }
    }
}
