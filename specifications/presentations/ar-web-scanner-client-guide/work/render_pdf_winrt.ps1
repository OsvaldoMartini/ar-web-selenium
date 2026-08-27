param(
  [Parameter(Mandatory = $true)][string]$InputPdf,
  [Parameter(Mandatory = $true)][string]$OutputDirectory,
  [int]$TargetWidth = 1500
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.WindowsRuntime

function Await-WinRtResult {
  param([object]$Operation, [type]$ResultType)
  $method = [System.WindowsRuntimeSystemExtensions].GetMethods() |
    Where-Object {
      $_.Name -eq 'AsTask' -and $_.IsGenericMethodDefinition -and
      $_.GetParameters().Count -eq 1 -and
      $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    } | Select-Object -First 1
  $task = $method.MakeGenericMethod($ResultType).Invoke($null, @($Operation))
  $task.Wait()
  return $task.Result
}

function Await-WinRtAction {
  param([object]$Operation)
  $method = [System.WindowsRuntimeSystemExtensions].GetMethods() |
    Where-Object {
      $_.Name -eq 'AsTask' -and -not $_.IsGenericMethod -and
      $_.GetParameters().Count -eq 1 -and
      $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncAction'
    } | Select-Object -First 1
  $task = $method.Invoke($null, @($Operation))
  $task.Wait()
}

$pdfPath = [System.IO.Path]::GetFullPath($InputPdf)
$outputPath = [System.IO.Path]::GetFullPath($OutputDirectory)
[System.IO.Directory]::CreateDirectory($outputPath) | Out-Null

$null = [Windows.Storage.StorageFile, Windows.Storage, ContentType = WindowsRuntime]
$null = [Windows.Data.Pdf.PdfDocument, Windows.Data.Pdf, ContentType = WindowsRuntime]
$null = [Windows.Data.Pdf.PdfPageRenderOptions, Windows.Data.Pdf, ContentType = WindowsRuntime]
$null = [Windows.Storage.Streams.InMemoryRandomAccessStream, Windows.Storage.Streams, ContentType = WindowsRuntime]

$file = Await-WinRtResult ([Windows.Storage.StorageFile]::GetFileFromPathAsync($pdfPath)) ([Windows.Storage.StorageFile])
$document = Await-WinRtResult ([Windows.Data.Pdf.PdfDocument]::LoadFromFileAsync($file)) ([Windows.Data.Pdf.PdfDocument])
$files = @()
for ($index = 0; $index -lt $document.PageCount; $index++) {
  $page = $document.GetPage($index)
  $stream = New-Object Windows.Storage.Streams.InMemoryRandomAccessStream
  $options = New-Object Windows.Data.Pdf.PdfPageRenderOptions
  $options.DestinationWidth = [uint32]$TargetWidth
  Await-WinRtAction ($page.RenderToStreamAsync($stream, $options))
  $stream.Seek(0)
  $dotNetStream = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($stream)
  $name = 'page-{0:D3}.png' -f ($index + 1)
  $target = Join-Path $outputPath $name
  $fileStream = [System.IO.File]::Create($target)
  try { $dotNetStream.CopyTo($fileStream) } finally { $fileStream.Dispose(); $dotNetStream.Dispose() }
  $page.Dispose()
  $stream.Dispose()
  $files += $target
}

[pscustomobject]@{
  input = $pdfPath
  pages = $document.PageCount
  width = $TargetWidth
  files = $files
} | ConvertTo-Json -Depth 4
