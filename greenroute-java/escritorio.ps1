# Abre la aplicación de escritorio (Java Swing + JOptionPane).
$aqui = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $aqui "buscar-java.ps1")

$java = Find-Java "java.exe"
if (-not $java) { Write-SinJava "Java"; exit 1 }

Set-Location $aqui
if (-not (Test-Path out\co\edu\greenroute\escritorio\AppEscritorio.class)) {
    & (Join-Path $aqui "compilar.ps1")
    if (-not (Test-Path out\co\edu\greenroute\escritorio\AppEscritorio.class)) { exit 1 }
}
& $java "-Dfile.encoding=UTF-8" "-Dstdout.encoding=UTF-8" -Xmx1g -cp out co.edu.greenroute.escritorio.AppEscritorio @args
