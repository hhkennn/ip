$dataFile = Join-Path $PSScriptRoot '..\data\herta.txt'
$dataDirectory = Split-Path -Parent $dataFile

New-Item -ItemType Directory -Path $dataDirectory -Force | Out-Null
if (Test-Path -LiteralPath $dataFile) {
    Remove-Item -LiteralPath $dataFile -Recurse -Force
}
New-Item -ItemType Directory -Path $dataFile -Force | Out-Null

& java -cp out herta.Herta
$exitCode = $LASTEXITCODE
Remove-Item -LiteralPath $dataFile -Recurse -Force
exit $exitCode
