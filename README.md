# Lomo Vegas (editor de vídeo/audio offline para Android)

## Compilar
Sube el contenido de esta carpeta a un repositorio de GitHub (la raíz debe contener settings.gradle.kts).
El flujo .github/workflows/build-apk.yml genera app-debug.apk (pestaña Actions > Artifacts).

## Uso rápido
- 📂 Importar vídeo, audio o fotos. 📁 Proyectos y exportación (720p / 1080p).
- Barra inferior: ✂ dividir, 🗑 eliminar, ↔ recortar, 🌗 fundidos, ✨ efectos, 💬 subtítulos, ☰ pistas, 🔎 zoom de la línea de tiempo.
  Los iconos con ▾ abren un desplegable al tocar o al mantener pulsado.
- Cabecera de cada pista: 👁/🚫 ver u ocultar, 🔊/🔇 silenciar.
- Mantén pulsado un clip o subtítulo y arrastra para moverlo.

## Subtitulado automático
- 💬 > "Subtitular automáticamente" crea los subtítulos en la pista S2 a partir del audio del clip seleccionado.
- 💬 > "Subtitular y recortar silencios" además elimina del clip los tramos sin voz (pausas de más de ~1 s).
- Funciona sin internet con Vosk (modelo español pequeño). El flujo de GitHub Actions descarga el modelo
  y lo incluye en la app. Si compilas en local, descarga vosk-model-small-es-0.42.zip desde
  https://alphacephei.com/vosk/models y copia la carpeta descomprimida a app/src/main/assets/model-es

## Dispositivos probados como objetivo
- Redmi Note 10 (Snapdragon 678, Android 11 / MIUI): pantalla alta 20:9 con orificio de cámara, 4-6 GB de RAM.
  La app respeta el orificio, mantiene la pantalla encendida al exportar y usa solo la arquitectura arm64.
- Redmi 7: sigue siendo compatible (Android 9 o superior).
- En MIUI: Ajustes > Aplicaciones > Permisos > Instalar apps desconocidas (para instalar el .apk) y
  Ahorro de batería > Sin restricciones para Lomo Vegas (para que no se cierre durante una exportación larga).

## Proyectos
- 📁 (arriba) > Nuevo proyecto / Guardar / Guardar como… / Abrir proyecto… / Exportar 720p / Exportar 1080p
- Los proyectos se guardan dentro de la app (no copian tus vídeos: guardan referencias a ellos y todos los cortes, efectos y subtítulos).
- Si mueves o borras un vídeo original, al abrir el proyecto la app avisa y quita ese clip.
- Autoguardado: 3 s después de cada cambio y al salir de la app. Se recupera en Abrir proyecto > «Autoguardado».
- Un punto (•) junto al nombre indica cambios sin guardar.

## Cortar y zoom
- ✂ (barra inferior): toca para activar el modo cortar (se pone morado); entonces tocar un clip lo divide justo ahí.
  Vuelve a tocar ✂ para salir. Mantén pulsado ✂ para "Dividir en el cursor".
- Zoom de la línea de tiempo: pellizca con dos dedos sobre las pistas, o usa 🔎 (acercar, alejar, ver todo el proyecto).
  Al acercar mucho, la regla muestra décimas de segundo para cortar con precisión.

## Deshacer / rehacer
- ↩ deshace y ↪ rehace, paso a paso (hasta 100 pasos), arriba junto al botón 📁.
- Un paso agrupa los cambios seguidos (por ejemplo, un arrastre completo de un clip).
- Abrir o crear un proyecto reinicia el historial.

## Presets de efectos (🎚)
Selecciona un clip y abre 🎚.
- Vídeo: blanco y negro, sepia, negativo, ondular, distorsión (abombado), espejo, colores vivos, glitch RGB.
- Audio: voz aguda, voz grave, ardilla, monstruo, distorsión, robot, eco, teléfono.
- Vista previa: se ven los efectos de color (B/N, sepia, negativo, colores vivos) y se oyen los de tono.
  Ondular, abombado, espejo, glitch y los efectos de audio de distorsión, robot, eco y teléfono se aplican al exportar.

## Pistas
- Al importar, si hay varias pistas del mismo tipo, la app pregunta en cuál añadir.
- Mantén pulsado un clip y arrastra: izquierda/derecha lo mueve en el tiempo; arriba/abajo lo pasa a otra pista del mismo tipo.
- Desplazarse por la línea de tiempo: arrastra con un dedo sobre las pistas.
