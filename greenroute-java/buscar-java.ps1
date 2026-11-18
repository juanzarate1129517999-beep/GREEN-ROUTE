# Busca Java 17 o superior en: la carpeta .tools del proyecto, JAVA_HOME, el PATH y las carpetas de instalación habituales.
# Lo usan compilar.ps1, ejecutar.ps1 y escritorio.ps1.

function Get-VersionJava([string]$exe) {
    try { return (Get-Item $exe).VersionInfo.ProductMajorPart } catch { return 0 }
}

function Find-Java([string]$programa) {
    $candidatos = @()
    $tools = Join-Path $PSScriptRoot "..\.tools"
    if (Test-Path $tools) {
        $candidatos += Get-ChildItem -Directory $tools -Filter "jdk-*" | ForEach-Object { Join-Path $_.FullName "bin\$programa" }
    }
    if ($env:JAVA_HOME) { $candidatos += Join-Path $env:JAVA_HOME "bin\$programa" }
    $candidatos += Get-Command $programa -All -ErrorAction SilentlyContinue | ForEach-Object { $_.Source }
    $raices = @("$env:ProgramFiles\Java", "$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Microsoft",
        "$env:ProgramFiles\Zulu", "$env:ProgramFiles\Amazon Corretto", "$env:ProgramFiles\BellSoft", "$env:USERPROFILE\.jdks")
    foreach ($raiz in $raices) {
        if (Test-Path $raiz) {
            $candidatos += Get-ChildItem -Directory $raiz | Sort-Object Name -Descending | ForEach-Object { Join-Path $_.FullName "bin\$programa" }
        }
    }
    foreach ($c in $candidatos) {
        if ($c -and (Test-Path $c) -and (Get-VersionJava $c) -ge 17) { return $c }
    }
    return $null
}

function Write-SinJava([string]$que) {
    Write-Host ""
    Write-Host "No se encontró $que 17 o superior en este equipo." -ForegroundColor Red
    $actual = Get-Command java -ErrorAction SilentlyContinue
    if ($actual) { Write-Host ("Java instalado: versión {0} ({1})" -f (Get-VersionJava $actual.Source), $actual.Source) }
    Write-Host "Instale el JDK 17 o superior (por ejemplo, Temurin en https://adoptium.net) y vuelva a ejecutar el script."
}
