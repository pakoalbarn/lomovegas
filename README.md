# Lomo Vegas (editor de vídeo/audio offline para Android)

## Generar el .apk
1. Abre esta carpeta con Android Studio (Koala o superior) y deja que sincronice Gradle (necesita internet una sola vez).
2. Build > Build APK(s). Resultado: app/build/outputs/apk/debug/app-debug.apk
3. Pásalo al Redmi 7 e instálalo (permitir "instalar apps desconocidas").

## Uso rápido
- Importar: va a la pista seleccionada (vídeo con audio -> pista V, solo audio -> pista A).
- Mantén pulsado un clip o subtítulo y arrastra para moverlo. Toca la regla o una pista vacía para mover el cursor.
- Dividir corta el clip seleccionado en el cursor. Ini/Fin recortan.
- Fund. ent / Fund. sal: ciclan 0 - 0.5 - 1 - 2 s.
- + Subtítulo abre el editor (texto, tiempos, fuente, color, tamaño, posición). Toca un subtítulo ya seleccionado para editarlo.
- Pistas superiores (V2, V3...) se dibujan encima de las inferiores.

## Estado
Código sin compilar ni probar en dispositivo. Si Gradle marca error, lo más probable es en Exporter.kt, Fades.kt
o Subtitles.kt (API experimental de Media3 1.5.1).
Exportaciones en Android/data/com.example.lomovegas/files/Movies/
