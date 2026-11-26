# Empaquetado de CAA para Windows

## Resultado

- `dist/CAA-<version>-portable-win-x64.zip`: contiene `CAA\CAA.exe` + runtime + JARs. Se descomprime y se ejecuta; no requiere Java instalado.
- Opcional: `dist/CAA-<version>.exe`, instalador por usuario (sin permisos de administrador), con acceso en menú Inicio y escritorio.

El `.exe` es una aplicación gráfica: no abre consola. Los datos **no** se incluyen en el paquete; la app los crea en `%LOCALAPPDATA%\CAA\datos` (para migrar los datos actuales, copiá la carpeta `datos` ahí antes de abrir el programa).

Por defecto, al terminar, el script copia el ZIP (y el instalador si se generó) a `%USERPROFILE%\Downloads`. Para evitarlo, agregá `-NoCopyToDownloads`.

Cuando descargues un artefacto desde GitHub, el navegador guarda un ZIP "contenedor": descomprimilo una vez y adentro vas a encontrar el ZIP portable (o el EXE instalador).

## Cómo generarlo

En una PC con Windows solo necesitás **JDK 17+** (Maven viene incluido vía Maven Wrapper; no hay que instalarlo):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\build-windows.ps1
```

Instalador EXE adicional (requiere WiX 3.x en el PATH, p. ej. `winget install --id WiXToolset.WiX3`):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\build-windows.ps1 -Installer
```

No se puede generar un `.exe` de Windows desde Linux/macOS: `jpackage` solo empaqueta para el sistema donde se ejecuta. Si no tenés Windows, podés usar el workflow `.github/workflows/build-windows.yml` en GitHub Actions (subí el repo y ejecutá *Run workflow*). El runner `windows-latest` ya incluye WiX 3.x, así que genera **el ZIP portable y el instalador EXE** en la misma ejecución; se descargan como artefactos `CAA-windows-portable` y `CAA-windows-installer`.

## Qué se optimiza

- `jlink` crea un runtime privado con solo los módulos JDK necesarios (`java.base`, `java.desktop`, `java.xml`, `java.logging`, `java.scripting`, `jdk.unsupported`) en vez del JDK completo.
- `--strip-debug --no-header-files --no-man-pages --strip-native-commands --compress=2` reduce el runtime.
- Se copian solo dependencias de ejecución; se excluyen JUnit/Mockito/Hamcrest que hereda el POM padre.
- No se empaquetan los 16 MB de `datos/expedientes` ni código fuente.
- `com.caa.app.Lanzador` es el `main` del ejecutable: evita el chequeo de modularidad de JavaFX 19 cuando las librerías van en el classpath.

Tamaño orientativo medido con este mismo método: imagen de aplicación ~81 MB sin comprimir (runtime recortado ~49 MB + JARs ~32 MB). El ZIP final queda en el orden de 50–70 MB. El instalador con WiX suele ser algo menor porque comprime el contenido.

## Por qué no un "único archivo EXE"

JavaFX incluye librerías nativas (Direct3D, glass, media) que no pueden ir dentro de un solo JAR. El EXE portable siempre necesita su carpeta `runtime` y `lib/app`. Si querés un instalador de un solo clic, usá la opción `-Installer`.

GraalVM `native-image` podría reducir el tamaño, pero CalendarFX y FXML dependen mucho de reflexión y recursos; requeriría mantener configuración de reflexión y es más frágil ante actualizaciones. Para una app de escritorio, `jlink + jpackage` es la opción equilibrada.
