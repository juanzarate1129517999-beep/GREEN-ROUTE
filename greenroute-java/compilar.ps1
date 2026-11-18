# Compila el motor en greenroute-java\out (necesita el JDK 17 o superior).
$ErrorActionPreference = "Stop"
$aqui = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $aqui "buscar-java.ps1")

$javac = Find-Java "javac.exe"
if (-not $javac) { Write-SinJava "el JDK (javac)"; exit 1 }

Set-Location $aqui
$fuentes = Get-ChildItem -Recurse -Filter *.java src | ForEach-Object { $_.FullName }
# Se compila en una carpeta temporal: si algo falla, la versión anterior de out\ no se pierde.
if (Test-Path out_nuevo) { Remove-Item -Recurse -Force out_nuevo }
& $javac --release 17 -encoding UTF-8 -d out_nuevo $fuentes
if ($LASTEXITCODE -ne 0) {
    if (Test-Path out_nuevo) { Remove-Item -Recurse -Force out_nuevo }
    throw "Error de compilación"
}
if (Test-Path out) { Remove-Item -Recurse -Force out }
Rename-Item out_nuevo out
Write-Host ("Compilado: {0} clases en greenroute-java\out" -f $fuentes.Count)
