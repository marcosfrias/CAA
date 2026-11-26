<#
.SYNOPSIS
    Empaqueta CAA para Windows: EXE portable (ZIP) y, opcionalmente, instalador EXE.

.DESCRIPTION
    Usa jlink/jpackage incluidos en el JDK. El runtime se recorta a los módulos
    que realmente usa la aplicación y se eliminan símbolos, cabeceras y native
    commands. Los datos NO se empaquetan: se guardan en %LOCALAPPDATA%\CAA\datos.

.PARAMETER Installer
    Genera además un instalador EXE (requiere WiX 3.x en el PATH).

.PARAMETER KeepAppImage
    Conserva la carpeta intermedia build\windows\CAA junto al ZIP.

.PARAMETER NoCopyToDownloads
    No copia el artefacto a %USERPROFILE%\Downloads (por defecto sí se copia).

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts\build-windows.ps1
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts\build-windows.ps1 -Installer
#>
[CmdletBinding()]
param(
    [switch]$Installer,
    [switch]$KeepAppImage,
    [switch]$NoCopyToDownloads
)

$ErrorActionPreference = 'Stop'
# En PowerShell 7+, los comandos nativos que escriben en stderr pueden
# convertirse en errores terminating. java -version escribe justo en stderr.
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
    $PSNativeCommandUseErrorActionPreference = $false
}

$raiz = Split-Path -Parent $PSScriptRoot
$pom = Join-Path $raiz 'pom.xml'
if (-not (Test-Path $pom)) {
    throw "No se encontro pom.xml en: $raiz"
}

[xml]$xml = Get-Content -Raw -Encoding UTF8 -Path $pom
$ns = New-Object System.Xml.XmlNamespaceManager($xml.NameTable)
$ns.AddNamespace('m', 'http://maven.apache.org/POM/4.0.0')
$versionNodo = $xml.SelectSingleNode('/m:project/m:version', $ns)
$artifactIdNodo = $xml.SelectSingleNode('/m:project/m:artifactId', $ns)
if (-not $versionNodo -or -not $artifactIdNodo) {
    throw 'No se pudieron leer version/artifactId del pom.xml'
}
$version = $versionNodo.InnerText
$artifactId = $artifactIdNodo.InnerText

$appName = 'CAA'
$vendor = 'marcos frias Software & Consulting'
$descripcion = 'Gestion de auditorias ambientales y expedientes'
$icono = Join-Path $raiz 'src\main\resources\com\caa\app\icon.ico'
if (-not (Test-Path $icono)) {
    throw "No se encontro el icono: $icono"
}

# ---------- JDK ----------
$java = 'java'
if ($env:JAVA_HOME) {
    $candidatoJava = Join-Path $env:JAVA_HOME 'bin\java.exe'
    if (Test-Path $candidatoJava) { $java = $candidatoJava }
}
$preferenciaAnterior = $ErrorActionPreference
try {
    $ErrorActionPreference = 'Continue'
    $textoJava = & $java '-version' 2>&1 | Out-String
}
finally {
    $ErrorActionPreference = $preferenciaAnterior
}
if ($textoJava -notmatch 'version\s+"?(\d+)(?:\.(\d+))?') {
    throw "No se pudo leer la version de Java. Salida: $textoJava"
}
$versionJavaMayor = [int]$Matches[1]
if ($versionJavaMayor -lt 17) {
    throw "Se necesita JDK 17 o superior (detectado: $versionJavaMayor). JavaFX 19 no funciona con JDK 11."
}

$jpackage = $null
if ($env:JAVA_HOME) {
    $candidato = Join-Path $env:JAVA_HOME 'bin\jpackage.exe'
    if (Test-Path $candidato) { $jpackage = $candidato }
}
if (-not $jpackage) {
    $comando = Get-Command jpackage.exe -ErrorAction SilentlyContinue
    if ($comando) { $jpackage = $comando.Source }
}
if (-not $jpackage) {
    throw 'No se encontro jpackage.exe. Usa un JDK 17+ y/o define JAVA_HOME.'
}
Write-Host "JDK detectado: $versionJavaMayor | jpackage: $jpackage"

# ---------- Maven ----------
$stage = Join-Path $raiz 'build\windows'
$appDir = Join-Path $stage 'app'
$dist = Join-Path $raiz 'dist'
if (Test-Path $stage) { Remove-Item -Recurse -Force $stage }
New-Item -ItemType Directory -Force -Path $appDir, $dist | Out-Null

$wrapper = Join-Path $raiz 'mvnw.cmd'
if (Test-Path $wrapper) {
    $maven = $wrapper
}
else {
    $mvnComando = Get-Command mvn -ErrorAction SilentlyContinue
    if (-not $mvnComando) {
        throw 'No se encontro mvnw.cmd ni mvn. Copia tambien la carpeta oculta .mvn del proyecto (o instala Maven).'
    }
    $maven = $mvnComando.Source
}

Write-Host "Compilando con Maven ($maven) y copiando dependencias de ejecucion..."
Push-Location $raiz
try {
    & $maven '-B' '-DskipTests' 'clean' 'package' 'dependency:copy-dependencies' `
        "-DoutputDirectory=$appDir" `
        '-DincludeScope=runtime' `
        '-DexcludeGroupIds=junit,org.hamcrest,org.mockito'
    if ($LASTEXITCODE -ne 0) { throw 'Falló la compilacion con Maven.' }
}
finally {
    Pop-Location
}

$jar = Join-Path $raiz "target\$artifactId-$version.jar"
if (-not (Test-Path $jar)) { throw "No se genero el JAR: $jar" }
Copy-Item -Force $jar $appDir

# ---------- jpackage ----------
# Ojo: NO se pasa --win-console. Eso hace que el .exe sea de interfaz grafica
# y no abra una ventana de consola negra al iniciar.
$comunes = @(
    '--name', $appName,
    '--app-version', $version,
    '--vendor', $vendor,
    '--description', $descripcion,
    '--main-jar', "$artifactId-$version.jar",
    '--main-class', 'com.caa.app.Lanzador',
    '--icon', $icono,
    '--add-modules', 'java.base,java.desktop,java.xml,java.logging,java.scripting,jdk.unsupported',
    '--jlink-options', '--strip-debug --no-header-files --no-man-pages --strip-native-commands --compress=2',
    '--java-options', '-Dfile.encoding=UTF-8',
    '--java-options', '-Duser.language=es',
    '--java-options', '-Duser.country=AR'
)

Write-Host 'Generando imagen de aplicacion (runtime recortado)...'
& $jpackage @comunes '--type' 'app-image' '--input' $appDir '--dest' $stage
if ($LASTEXITCODE -ne 0) { throw 'Falló jpackage al crear la imagen de aplicacion.' }

$image = Join-Path $stage $appName
if (-not (Test-Path (Join-Path $image 'CAA.exe'))) {
    throw "No se encontro CAA.exe en: $image"
}

$zip = Join-Path $dist "$appName-$version-portable-win-x64.zip"
Write-Host "Comprimiendo portable: $zip"
Compress-Archive -Path $image -DestinationPath $zip -Force

if ($Installer) {
    if (-not (Get-Command candle.exe -ErrorAction SilentlyContinue)) {
        $wixCandles = Get-ChildItem `
            'C:\Program Files (x86)\WiX Toolset v3*\bin\candle.exe', `
            'C:\Program Files\WiX Toolset v3*\bin\candle.exe' `
            -ErrorAction SilentlyContinue
        foreach ($c in $wixCandles) { $env:PATH = "$($c.DirectoryName);$env:PATH" }
    }
    $candle = Get-Command candle.exe -ErrorAction SilentlyContinue
    $light = Get-Command light.exe -ErrorAction SilentlyContinue
    if (-not ($candle -and $light)) {
        throw 'Para -Installer se necesita WiX 3.x (candle.exe y light.exe). Ejemplo: winget install --id WiXToolset.WiX3'
    }
    Write-Host 'Generando instalador EXE (por usuario, sin permisos de administrador)...'
    & $jpackage @comunes '--type' 'exe' '--input' $appDir '--dest' $dist `
        '--win-menu' '--win-menu-group' $appName '--win-shortcut' `
        '--win-per-user-install' `
        '--win-upgrade-uuid' '7c1a6b2e-9f44-4d0a-9d31-2b6f0a8c5e11'
    if ($LASTEXITCODE -ne 0) { throw 'Falló jpackage al crear el instalador EXE.' }
}

if (-not $KeepAppImage) { Remove-Item -Recurse -Force $image }

Write-Host ''
Write-Host 'Artefactos generados:'
Get-ChildItem -Path $dist -File |
    Where-Object { $_.Name -like "$appName-$version*" } |
    ForEach-Object { '{0}  ({1:N1} MB)' -f $_.FullName, ($_.Length / 1MB) }
Write-Host ''
Write-Host 'Los datos se guardan en: %LOCALAPPDATA%\CAA\datos'

if (-not $NoCopyToDownloads) {
    $descargas = Join-Path $env:USERPROFILE 'Downloads'
    if (-not (Test-Path $descargas)) {
        New-Item -ItemType Directory -Force -Path $descargas | Out-Null
    }
    $aCopiar = @($zip)
    if ($Installer) {
        $instalador = Get-ChildItem -Path $dist -Filter "$appName-$version*.exe" -File |
            Select-Object -First 1
        if ($instalador) { $aCopiar += $instalador.FullName }
    }
    foreach ($archivo in $aCopiar) {
        $destino = Join-Path $descargas (Split-Path -Leaf $archivo)
        Copy-Item -Force $archivo $destino
        Write-Host "Copiado a Descargas: $destino"
    }
}
