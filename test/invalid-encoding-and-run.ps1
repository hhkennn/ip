$dataFile = Join-Path $PSScriptRoot '..\data\herta.txt'
$dataDirectory = Split-Path -Parent $dataFile

New-Item -ItemType Directory -Path $dataDirectory -Force | Out-Null
$invalidUtf8 = [byte[]] (0x54, 0x20, 0x7C, 0x20, 0x30, 0x20, 0x7C, 0x20, 0x62, 0x61, 0x64, 0xC3, 0x28)
[System.IO.File]::WriteAllBytes($dataFile, $invalidUtf8)

& java -cp out herta.Herta
$exitCode = $LASTEXITCODE
Remove-Item -LiteralPath $dataFile -Force -ErrorAction SilentlyContinue
exit $exitCode
