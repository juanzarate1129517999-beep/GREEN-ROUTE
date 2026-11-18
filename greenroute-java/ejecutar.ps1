# Inicia el motor y el sitio web en http://localhost:8080 (opciones: --puerto=8081, --demo).
$aqui = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $aqui "buscar-java.ps1")

$java = Find-Java "java.exe"
if (-not $java) { Write-SinJava "Java"; exit 1 }

Set-Location $aqui
if (-not (Test-Path out\co\edu\greenroute\App.class)) {
    & (Join-Path $aqui "compilar.ps1")
    if (-not (Test-Path out\co\edu\greenroute\App.class)) { exit 1 }
}
& $java "-Dfile.encoding=UTF-8" "-Dstdout.encoding=UTF-8" -Xmx1g -cp out co.edu.greenroute.App @args
