param([string]$JavaPath='java')
$ErrorActionPreference='Stop'
foreach($aceosRequired in @('ACEOS_DB_URL','ACEOS_DB_USER','ACEOS_DB_PASSWORD')) {
    if([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($aceosRequired))){
        throw "Configure $aceosRequired privately before launching. See START-HERE.md."
    }
}
$aceosJar=Join-Path $PSScriptRoot 'app\aceos-2.0.0.jar'
$aceosLibraries=Join-Path $PSScriptRoot 'lib\*'
if(!(Test-Path -LiteralPath $aceosJar)){throw 'Application JAR missing. Extract the complete college package first.'}
& $JavaPath '--enable-native-access=ALL-UNNAMED' '-cp' ($aceosJar+';'+$aceosLibraries) 'com.aceos.Main'
exit $LASTEXITCODE
