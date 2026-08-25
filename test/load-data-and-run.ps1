$dataFile = Join-Path $PSScriptRoot '..\data\herta.txt'
$dataDirectory = Split-Path -Parent $dataFile
$savedTasks = @(
    'T | 1 | loaded todo',
    'D | 0 | loaded deadline | 2019-10-15T00:00',
    'E | 0 | loaded event | 2019-10-15T00:00 | 2019-10-16T00:00'
)

New-Item -ItemType Directory -Path $dataDirectory -Force | Out-Null
$utf8WithoutBom = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllLines($dataFile, $savedTasks, $utf8WithoutBom)

& java -cp out Herta
exit $LASTEXITCODE
