# 🎨 Documentación de Mentat: Módulo de Arte

**Fecha de actualización:** Febrero 2026
**Objetivo:** Obtener galerías de arte de alta calidad, agruparlas por autor, enriquecer los datos biográficos y publicarlas en Bluesky con accesibilidad completa.

---

## 1. La Fuente de Datos: Art Institute of Chicago (AIC)
Hemos abandonado el scraping inestable de blogs de WordPress para usar la API oficial y estructurada del museo.
- **Endpoint principal:** `https://api.artic.edu/api/v1/artworks/search`
- **Parámetros clave:** Buscamos por estilos genéricos (Cubism, Surrealism...), exigimos dominio público (`is_public_domain=true`) y pedimos campos específicos como `image_id`, `artist_title`, `style_title`, y `classification_title`.
- **Filtro Anti-Chatarra:** Solo aceptamos obras donde el `classification_title` contenga términos puramente visuales (`painting`, `print`, `drawing`, `pastel`, etc.). Rechazamos monedas, muebles o textos.

## 2. El Curador Estricto (Lógica de Agrupación)
Para evitar el "caos visual" de mezclar autores y épocas en un mismo post, Mentat ahora aplica una regla de hierro:
- Descarga una lista grande de candidatos (hasta 80).
- Agrupa las obras por nombre de artista (`groupBy { it.artista }`).
- **Regla de Oro:** Busca el artista que tenga más obras en esa tirada. **Exige un mínimo de 2 obras** para considerarlo una "galería". Si solo hay 1, descarta y vuelve a buscar.
- Toma un máximo de 3 obras de ese mismo autor.

## 3. El Detective de Wikipedia (Sistema de Fallback)
A veces, el museo no tiene registradas las fechas de nacimiento/muerte del artista, o su estilo aparece como el texto literal `"null"`. Mentat usa la API de resumen de Wikipedia para rellenar esos huecos.
- **Endpoint:** `https://en.wikipedia.org/api/rest_v1/page/summary/{Nombre_Codificado}`
- **Extracción de Fechas:** Usa la Expresión Regular `\(\s*\d{3,4}\s*[–\-\—]\s*\d{3,4}\s*\)` para buscar patrones como `(1881-1973)` tanto en la descripción corta como en el extracto del texto.
- **Limpieza de Estilo:** Si Wikipedia devuelve la fecha junto a la profesión (ej. "Spanish painter (1881-1973)"), el código usa otra Regex para "extirpar" la fecha de la descripción. Así, el autor se queda con sus fechas, y el estilo queda limpio ("Spanish painter").

## 4. Visualización con Coil
Sustituimos Glide por **Coil** (`AsyncImage`), que es nativo de Jetpack Compose y maneja mucho mejor las URLs complejas de la API del museo (formato IIIF).
- Construcción de URL de imagen: `https://www.artic.edu/iiif/2/{image_id}/full/843,/0/default.jpg`

## 5. Accesibilidad y Publicación en Bluesky
El repositorio de Bluesky se reescribió para abandonar el texto alternativo genérico.
- Antes: Se subían 3 fotos y a todas se les ponía "Imagen compartida desde Mentat".
- Ahora: La función `publicarGaleriaArte` recibe una lista de parejas `(Bitmap, Título)`. Sube el `Blob` a los servidores y crea el `ImageAspect` vinculando cada foto con el título real de ese cuadro concreto.
- **Resultado:** Una galería coherente del mismo autor, donde los lectores de pantalla leen el nombre exacto de cada obra.

## 6. Historial de Vistas
Se mantiene el uso de `HistorialStorage` para guardar en la base de datos local la URL de la página web del museo (`urlWeb`) de cada cuadro mostrado. Esto garantiza que Mentat nunca repita un cuadro en el futuro.