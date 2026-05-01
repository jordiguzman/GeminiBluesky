package mentat.music.com.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Definimos la paleta OSCURA (La que queremos usar siempre)
private val MentatDarkScheme = darkColorScheme(
    primary = AzulMentat,
    secondary = PurpleGrey80,
    tertiary = Pink80,

    // Aquí está la magia del fondo negro
    background = NegroAbsoluto,
    surface = GrisOscuro, // Las tarjetas tendrán este tono sutil

    onPrimary = BlancoPuro,
    onSecondary = BlancoPuro,
    onTertiary = BlancoPuro,

    onBackground = BlancoPuro, // Texto sobre fondo
    onSurface = BlancoPuro     // Texto sobre tarjetas
)

@Composable
fun MentatMusicComTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color puesto a FALSE para que no coja los colores de tu fondo de pantalla de Android
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // FORZAMOS SIEMPRE EL MODO OSCURO (MentatDarkScheme)
        // Ignoramos el modo claro por completo
        else -> MentatDarkScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Pintamos la barra de estado (donde la hora y batería) de negro
            window.statusBarColor = colorScheme.background.toArgb()
            // Le decimos al sistema que los iconos de la barra sean claros
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography, // Asegúrate de que tienes Typography.kt, si no, borra esta línea
        content = content
    )
}