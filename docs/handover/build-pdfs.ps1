<#
.SYNOPSIS
    Builds the two handover PDFs from their HTML sources in this folder, with headless Microsoft Edge (or Chrome)
    driven over the DevTools protocol: A4, background colours, and a footer with the document name and page numbers.

.EXAMPLE
    .\build-pdfs.ps1
#>
$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot

$documents = @(
    @{ Source = 'project-proposal.html';       Pdf = 'Food-Delivery-Project-Proposal.pdf';       Name = 'Project Proposal' },
    @{ Source = 'development-completion.html'; Pdf = 'Food-Delivery-Development-Completion.pdf'; Name = 'Development Completion' }
)

$browser = @(
    'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe',
    'C:\Program Files\Microsoft\Edge\Application\msedge.exe',
    'C:\Program Files\Google\Chrome\Application\chrome.exe'
) | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $browser) { throw 'Microsoft Edge or Google Chrome is needed to print the PDFs.' }

$port = 9334
$profileDir = Join-Path $env:TEMP ('handover-pdf-' + [guid]::NewGuid())
$proc = Start-Process $browser -ArgumentList '--headless=new', '--disable-gpu', "--remote-debugging-port=$port",
    "--user-data-dir=$profileDir", '--allow-file-access-from-files', 'about:blank' -PassThru

try {
    $target = $null
    for ($i = 0; $i -lt 60 -and -not $target; $i++) {
        Start-Sleep -Milliseconds 250
        try { $target = (Invoke-RestMethod "http://127.0.0.1:$port/json/list") | Where-Object { $_.type -eq 'page' } | Select-Object -First 1 } catch { }
    }
    if (-not $target) { throw 'The browser did not open its DevTools port.' }

    $ws = New-Object System.Net.WebSockets.ClientWebSocket
    $ws.ConnectAsync([Uri]$target.webSocketDebuggerUrl, [Threading.CancellationToken]::None).Wait()
    $script:id = 0

    function Invoke-Cdp([string]$method, [hashtable]$params = @{}) {
        $script:id++
        $want = $script:id
        $message = @{ id = $want; method = $method; params = $params } | ConvertTo-Json -Depth 10 -Compress
        $bytes = [Text.Encoding]::UTF8.GetBytes($message)
        $ws.SendAsync([ArraySegment[byte]]$bytes, 'Text', $true, [Threading.CancellationToken]::None).Wait()
        $buffer = New-Object byte[] 1048576
        while ($true) {
            $stream = New-Object IO.MemoryStream
            do {
                $result = $ws.ReceiveAsync([ArraySegment[byte]]$buffer, [Threading.CancellationToken]::None).Result
                $stream.Write($buffer, 0, $result.Count)
            } while (-not $result.EndOfMessage)
            $reply = [Text.Encoding]::UTF8.GetString($stream.ToArray()) | ConvertFrom-Json
            if ($reply.id -eq $want) {
                if ($reply.error) { throw "$method failed: $($reply.error.message)" }
                return $reply.result
            }
        }
    }

    Invoke-Cdp 'Page.enable' | Out-Null
    foreach ($doc in $documents) {
        $source = Join-Path $here $doc.Source
        $url = ([Uri]$source).AbsoluteUri
        Invoke-Cdp 'Page.navigate' @{ url = $url } | Out-Null
        # Wait until the page, its stylesheet, images and fonts are ready
        for ($i = 0; $i -lt 40; $i++) {
            Start-Sleep -Milliseconds 250
            $state = Invoke-Cdp 'Runtime.evaluate' @{ expression = "document.readyState === 'complete' && [...document.images].every(i => i.complete)"; returnByValue = $true }
            if ($state.result.value -eq $true) { break }
        }
        Invoke-Cdp 'Runtime.evaluate' @{ expression = 'document.fonts.ready.then(() => true)'; awaitPromise = $true } | Out-Null

        $footer = "<div style=""width:100%; font-family:'Segoe UI',Arial,sans-serif; font-size:7.5pt; color:#55675a; padding:0 16mm; display:flex; justify-content:space-between;"">" +
            "<span>Food Delivery Platform &middot; $($doc.Name)</span>" +
            "<span>Page <span class=""pageNumber""></span> of <span class=""totalPages""></span></span></div>"
        $pdf = Invoke-Cdp 'Page.printToPDF' @{
            printBackground     = $true
            preferCSSPageSize   = $true
            displayHeaderFooter = $true
            headerTemplate      = '<span></span>'
            footerTemplate      = $footer
            generateDocumentOutline = $true
            generateTaggedPDF   = $true
        }
        $out = Join-Path $here $doc.Pdf
        [IO.File]::WriteAllBytes($out, [Convert]::FromBase64String($pdf.data))
        $pages = ([regex]::Matches([Text.Encoding]::ASCII.GetString([IO.File]::ReadAllBytes($out)), '/Type\s*/Page[^s]')).Count
        '{0}  ({1} pages, {2:N0} KB)' -f $out, $pages, ((Get-Item $out).Length / 1KB)
    }
} finally {
    Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
    Get-CimInstance Win32_Process -Filter "Name = 'msedge.exe' OR Name = 'chrome.exe'" |
        Where-Object { $_.CommandLine -like "*$profileDir*" } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
}
