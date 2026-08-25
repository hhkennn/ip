$dataFile = Join-Path $PSScriptRoot '..\data\herta.txt'
$dataDirectory = Split-Path -Parent $dataFile

New-Item -ItemType Directory -Path $dataDirectory -Force | Out-Null
Remove-Item -LiteralPath $dataFile -Force -ErrorAction SilentlyContinue

& java -cp out Herta
exit $LASTEXITCODE
