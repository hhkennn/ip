$dataFile = Join-Path $PSScriptRoot '..\data\herta.txt'
$dataDirectory = Split-Path -Parent $dataFile

New-Item -ItemType Directory -Path $dataDirectory -Force | Out-Null
[System.IO.File]::WriteAllText($dataFile, [string]::Empty)

& java -cp out Herta
exit $LASTEXITCODE
