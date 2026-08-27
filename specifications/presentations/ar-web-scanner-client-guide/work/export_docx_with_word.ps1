param(
  [Parameter(Mandatory = $true)][string]$InputDocx,
  [Parameter(Mandatory = $true)][string]$OutputPdf
)

$ErrorActionPreference = 'Stop'
$inputPath = [System.IO.Path]::GetFullPath($InputDocx)
$outputPath = [System.IO.Path]::GetFullPath($OutputPdf)
$outputDir = [System.IO.Path]::GetDirectoryName($outputPath)
[System.IO.Directory]::CreateDirectory($outputDir) | Out-Null

$word = $null
$document = $null
try {
  $word = New-Object -ComObject Word.Application
  $word.Visible = $false
  $word.DisplayAlerts = 0
  $document = $word.Documents.Open($inputPath, $false, $true)
  $pageCount = $document.ComputeStatistics(2)
  $document.ExportAsFixedFormat($outputPath, 17, $false, 0, 0, 1, $pageCount, 0, $true, $true, 1, $true, $true, $false)
  [pscustomobject]@{
    input = $inputPath
    output = $outputPath
    pages = $pageCount
    bytes = (Get-Item -LiteralPath $outputPath).Length
  } | ConvertTo-Json -Depth 3
}
finally {
  if ($null -ne $document) { $document.Close($false) }
  if ($null -ne $word) { $word.Quit() }
  if ($null -ne $document) { [void][Runtime.InteropServices.Marshal]::ReleaseComObject($document) }
  if ($null -ne $word) { [void][Runtime.InteropServices.Marshal]::ReleaseComObject($word) }
  [GC]::Collect()
  [GC]::WaitForPendingFinalizers()
}
