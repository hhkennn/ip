$dataFile = Join-Path $PSScriptRoot '..\data\herta.txt'
$dataDirectory = Split-Path -Parent $dataFile
$savedTasks = @(
    'T | 1 | loaded todo',
    'D | 0 | loaded deadline | Friday',
    'E | 0 | loaded event | Monday | Tuesday'
)

New-Item -ItemType Directory -Path $dataDirectory -Force | Out-Null
$utf8WithoutBom = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllLines($dataFile, $savedTasks, $utf8WithoutBom)

& java -cp out Herta
exit $LASTEXITCODE
