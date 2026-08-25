$dataFile = Join-Path $PSScriptRoot '..\data\herta.txt'
$dataDirectory = Split-Path -Parent $dataFile

New-Item -ItemType Directory -Path $dataDirectory -Force | Out-Null
$utf8WithoutBom = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllText($dataFile, 'T | 2 | invalid status', $utf8WithoutBom)

& java -cp out Herta
exit $LASTEXITCODE
