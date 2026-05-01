package mentat.music.com.ui

import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.ai.client.generativeai.GenerativeModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mentat.music.com.repository.BlueskyRepository
import mentat.music.com.utils.ImageUtils
import mentat.music.com.ui.theme.*
import mentat.music.com.database.MentatDatabase

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AppNavigation(
    db: FirebaseFirestore,
    model: GenerativeModel,
    database: MentatDatabase
) {
    val navController = rememberNavController()

    val items = listOf(
        Screen.Notificaciones,
        Screen.Timeline
    )

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Color.White
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color.White,
                            indicatorColor = AzulMentat.copy(alpha = 0.3f),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray
                        ),
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->

        NavHost(
            navController = navController,
            startDestination = Screen.Notificaciones.route,
            modifier = Modifier.padding(top = innerPadding.calculateTopPadding())
        ) {

            // --- PANTALLA BUZÓN ---
            composable(Screen.Notificaciones.route) {
                NotificationsScreen(
                    db = db,
                    model = model,
                    onOpenArte = { navController.navigate(Screen.Arte.route) }
                    // PASO 1 PENDIENTE: Descomentar esto cuando actualicemos NotificationsScreen
                    , onOpenArteMaestro = { navController.navigate(Screen.ArteMaestro.route) }
                )
            }

            // --- PANTALLA CAZA (TIMELINE) ---
            composable(Screen.Timeline.route) {
                TimelineScreen(
                    db = db,
                    model = model,
                    database = database
                )
            }

            // --- PANTALLA GALERÍA ORIGINAL ---
            composable(Screen.Arte.route) {
                val scope = rememberCoroutineScope()
                val context = LocalContext.current

                ArtScreen(
                    onClose = { navController.popBackStack() },
                    onPublicar = { texto, obras ->
                        scope.launch {
                            Toast.makeText(context, "⏳ Preparando exposición...", Toast.LENGTH_SHORT).show()
                            try {
                                val bsky = BlueskyRepository()
                                val galeriaParaSubir = withContext(Dispatchers.IO) {
                                    obras.mapNotNull { obra ->
                                        val bitmap = ImageUtils.descargarImagen(context, obra.url)
                                        if (bitmap != null) Pair(bitmap, obra.titulo) else null
                                    }
                                }
                                val exito = if (galeriaParaSubir.isNotEmpty()) {
                                    Toast.makeText(context, "📤 Subiendo galería a Bluesky...", Toast.LENGTH_SHORT).show()
                                    bsky.publicarGaleriaArte(texto, galeriaParaSubir)
                                } else {
                                    Toast.makeText(context, "⚠️ No se pudieron descargar las imágenes.", Toast.LENGTH_SHORT).show()
                                    false
                                }
                                if (exito) {
                                    Toast.makeText(context, "✅ ¡Exposición inaugurada!", Toast.LENGTH_LONG).show()
                                    navController.popBackStack()
                                } else {
                                    Toast.makeText(context, "❌ Error al publicar.", Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "🔥 Error crítico: ${e.message}", Toast.LENGTH_LONG).show()
                                e.printStackTrace()
                            }
                        }
                    }
                )
            }

            // --- PANTALLA NUEVA: OBRAS MAESTRAS (JSONL) ---
            composable(Screen.ArteMaestro.route) {
                val scope = rememberCoroutineScope()
                val context = LocalContext.current

                // PASO 2 PENDIENTE: Descomentar esto cuando creemos ArtMaestroScreen.kt

                ArtMaestroScreen(
                    onClose = { navController.popBackStack() },
                    onPublicar = { texto, obras ->
                        scope.launch {
                            Toast.makeText(context, "⏳ Preparando obra maestra...", Toast.LENGTH_SHORT).show()
                            try {
                                val bsky = BlueskyRepository()
                                val galeriaParaSubir = withContext(Dispatchers.IO) {
                                    obras.mapNotNull { obra ->
                                        val bitmap = ImageUtils.descargarImagen(context, obra.url)
                                        if (bitmap != null) Pair(bitmap, obra.titulo) else null
                                    }
                                }
                                val exito = if (galeriaParaSubir.isNotEmpty()) {
                                    Toast.makeText(context, "📤 Subiendo tesoro a Bluesky...", Toast.LENGTH_SHORT).show()
                                    bsky.publicarGaleriaArte(texto, galeriaParaSubir)
                                } else {
                                    Toast.makeText(context, "⚠️ Error en imágenes.", Toast.LENGTH_SHORT).show()
                                    false
                                }
                                if (exito) {
                                    Toast.makeText(context, "✅ ¡Tesoro publicado!", Toast.LENGTH_LONG).show()
                                    navController.popBackStack()
                                } else {
                                    Toast.makeText(context, "❌ Error al publicar.", Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "🔥 Error crítico: ${e.message}", Toast.LENGTH_LONG).show()
                                e.printStackTrace()
                            }
                        }
                    }
                )

            }
        }
    }
}

sealed class Screen(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Notificaciones : Screen("notifs", "Buzón", Icons.Default.Notifications)
    object Timeline : Screen("timeline", "Caza", Icons.Default.Home)
    object Arte : Screen("arte", "Galería", Icons.Default.Edit)
    // Nueva ruta registrada oficialmente sin causar errores
    object ArteMaestro : Screen("arte_maestro", "Obras Maestras", Icons.Default.Search)
}