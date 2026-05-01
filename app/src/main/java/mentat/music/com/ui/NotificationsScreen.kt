package mentat.music.com.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ai.client.generativeai.GenerativeModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import mentat.music.com.ai.MentatBrain
import mentat.music.com.ui.theme.AzulMentat
import mentat.music.com.ui.theme.Purple40
import mentat.music.com.viewmodel.NotificationsViewModel

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    db: FirebaseFirestore,
    model: GenerativeModel,
    onOpenArte: () -> Unit,
    onOpenArteMaestro: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // --- INSTANCIAMOS VIEWMODEL ---
    val viewModel = remember {
        NotificationsViewModel(
            db = db,
            brain = MentatBrain(context, model),
            context = context
        )
    }

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.mensajeInfo, uiState.mensajeError) {
        uiState.mensajeInfo?.let {
            snackbarHostState.showSnackbar(it, withDismissAction = true)
            viewModel.mensajeMostrado()
        }
        uiState.mensajeError?.let {
            snackbarHostState.showSnackbar("Error: $it", withDismissAction = true)
            viewModel.mensajeMostrado()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.buscarNovedades(manual = false, context = context)
    }

    // --- CONFIGURACIÓN DEL SWIPE (PAGER) ---
    // ⚡ ELIMINADO "Conversación"
    val tabs = listOf("Reacciones", "Audiencia")
    // El PagerState controla en qué página estamos (0 o 1)
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val pullState = rememberPullToRefreshState()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Buzón Mentat", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )

                // --- PESTAÑAS SINCRONIZADAS ---
                TabRow(
                    selectedTabIndex = pagerState.currentPage, // Se mueve con el dedo
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = Purple40,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                            color = Purple40
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        // ⚡ ÍNDICES ACTUALIZADOS
                        val badgeCount = when (index) {
                            0 -> uiState.countReacciones
                            1 -> uiState.countAudiencia
                            else -> 0
                        }

                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                // Al hacer clic, movemos el Pager animado
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(title, fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal)
                                    if (badgeCount > 0) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Surface(
                                            color = Purple40, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.size(18.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(badgeCount.toString(), style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 80.dp)
            ) {
                // Nuevo botón flotante (Izquierda)
                FloatingActionButton(
                    onClick = { onOpenArteMaestro() },
                    containerColor = AzulMentat,
                    contentColor = Color.White,
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    Icon(Icons.Filled.Search, contentDescription = "Nueva Galería JSONL")
                }

                // Tu botón original (Derecha)
                FloatingActionButton(
                    onClick = onOpenArte,
                    containerColor = AzulMentat,
                    contentColor = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Galería Arte Original")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->

        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {

            // --- EL CONTENEDOR DESLIZABLE (SWIPE) ---
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { pageIndex ->

                // ⚡ LÓGICA DE FILTRADO ACTUALIZADA
                val listaFiltrada = remember(uiState.borradores, pageIndex) {
                    when (pageIndex) {
                        0 -> uiState.borradores.filter { it.tipo in listOf("LIKE", "REPOST") }
                        1 -> uiState.borradores.filter { it.tipo == "FOLLOW" }
                        else -> emptyList()
                    }
                }

                PullToRefreshBox(
                    isRefreshing = uiState.isLoading,
                    onRefresh = { viewModel.buscarNovedades(manual = true, context = context) },
                    state = pullState,
                    indicator = {
                        PullToRefreshDefaults.Indicator(
                            state = pullState, isRefreshing = uiState.isLoading,
                            containerColor = Color.White, color = Purple40, modifier = Modifier.align(Alignment.TopCenter)
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (listaFiltrada.isEmpty() && !uiState.isLoading) {
                        // ⚡ MENSAJES ACTUALIZADOS
                        val mensaje = when (pageIndex) {
                            0 -> "Sin reacciones recientes."
                            1 -> "Sin nuevos seguidores."
                            else -> "Nada por aquí."
                        }
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(mensaje, color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 100.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // ⚡ Botón de Limpieza Masiva ajustado al índice 0 (Reacciones)
                            if (pageIndex == 0 && listaFiltrada.isNotEmpty()) {
                                item {
                                    Button(
                                        onClick = { listaFiltrada.forEach { viewModel.archivar(it) } },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                        shape = MaterialTheme.shapes.medium
                                    ) {
                                        Icon(Icons.Default.Done, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Marcar todo como Visto", color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    }
                                }
                            }

                            items(listaFiltrada) { borrador ->
                                TarjetaBorrador(
                                    borrador = borrador,
                                    onArchivar = { viewModel.archivar(borrador) },
                                    onPublicar = { texto -> viewModel.enviarRespuesta(borrador, texto) },
                                    onCitar = { texto -> viewModel.citar(borrador, texto) },
                                    onRepost = { viewModel.repostear(borrador) },
                                    onIgnorar = { viewModel.archivar(borrador) },
                                    onMover = { viewModel.moverACaza(borrador) },
                                    onRegenerar = {},
                                    onLike = {}
                                )
                            }
                        }
                    }
                }
            }

            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp))
        }
    }
}