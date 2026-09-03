$repositoryDirectory = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$temporaryDirectory = Join-Path ([System.IO.Path]::GetTempPath()) (
    'herta-ui-test-' + [System.Guid]::NewGuid().ToString('N'))
$temporaryDataDirectory = Join-Path $temporaryDirectory 'data'
$outputDirectory = Join-Path $repositoryDirectory 'out'

New-Item -ItemType Directory -Path $temporaryDataDirectory -Force | Out-Null

$exitCode = 1
Push-Location -LiteralPath $temporaryDirectory
try {
    & java -cp $outputDirectory herta.Herta
    $exitCode = $LASTEXITCODE
} finally {
    Pop-Location
    Remove-Item -LiteralPath $temporaryDirectory -Recurse -Force -ErrorAction SilentlyContinue
}

exit $exitCode
