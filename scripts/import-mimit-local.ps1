#Requires -Version 5.1
[CmdletBinding()]
param(
    [ValidateSet('full', 'prices')][string]$Mode = 'full',
    [uri]$ApiBaseUrl = 'https://trova-benzina-api.onrender.com',
    [System.Management.Automation.PSCredential]$Credential,
    [string]$PricesFile,
    [string]$StationsFile,
    [switch]$CheckDownloads
)

$ErrorActionPreference = 'Stop'
if (($ApiBaseUrl.Scheme -ne 'https' -and -not ($ApiBaseUrl.Scheme -eq 'http' -and $ApiBaseUrl.IsLoopback)) -or
    $ApiBaseUrl.UserInfo -or $ApiBaseUrl.Query -or $ApiBaseUrl.Fragment -or $ApiBaseUrl.AbsolutePath -ne '/') {
    throw 'Specify the HTTPS backend origin without credentials, query or /api path.'
}

function Confirm-CsvHeader([string]$Path, [string]$Expected) {
    $reader = [System.IO.StreamReader]::new($Path, [System.Text.Encoding]::UTF8, $true)
    try {
        while ($null -ne ($line = $reader.ReadLine())) {
            if ($line.Contains('|')) {
                $actualHeader = [regex]::Replace($line.ToLowerInvariant(), '[^a-z0-9|]', '')
                $expectedHeader = [regex]::Replace($Expected.ToLowerInvariant(), '[^a-z0-9|]', '')
                if ($actualHeader -ne $expectedHeader) { throw 'Unexpected MIMIT CSV header. Import cancelled.' }
                return
            }
        }
        throw 'MIMIT CSV header not found. Import cancelled.'
    } finally { $reader.Dispose() }
}

function Save-MimitCsv([string]$Dataset, [string]$Path) {
    Write-Host "Downloading official MIMIT CSV: $Dataset"
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        try {
            Invoke-WebRequest -UseBasicParsing -Uri "https://www.mimit.gov.it/images/exportCSV/$Dataset.csv" `
                -OutFile $Path -TimeoutSec 120
            return
        } catch {
            if ($attempt -eq 3) { throw }
            Write-Warning "MIMIT download failed (attempt $attempt/3). Retrying."
            Start-Sleep -Seconds 2
        }
    }
}

$workDir = Join-Path ([System.IO.Path]::GetTempPath()) ('mimit-local-' + [guid]::NewGuid().ToString('N'))
$null = New-Item -ItemType Directory -Path $workDir
$client = $null
$form = $null
$response = $null
try {
    if (-not $PricesFile) {
        $PricesFile = Join-Path $workDir 'prices.csv'
        Save-MimitCsv 'prezzo_alle_8' $PricesFile
    }
    $PricesFile = (Resolve-Path -LiteralPath $PricesFile).ProviderPath
    Confirm-CsvHeader $PricesFile 'idImpianto|desc_carburante|prezzo|is_self|dtComu'
    if ($Mode -eq 'full') {
        if (-not $StationsFile) {
            $StationsFile = Join-Path $workDir 'stations.csv'
            Save-MimitCsv 'anagrafica_impianti_attivi' $StationsFile
        }
        $StationsFile = (Resolve-Path -LiteralPath $StationsFile).ProviderPath
        Confirm-CsvHeader $StationsFile 'idImpianto|Gestore|Bandiera|Tipo Impianto|Nome Impianto|Indirizzo|Comune|Provincia|Latitudine|Longitudine'
    }
    if ($CheckDownloads) {
        Write-Host 'MIMIT CSV headers verified. No login or database import performed.'
        return
    }

    if ($null -eq $Credential) {
        $email = Read-Host 'Backend admin email'
        $password = Read-Host 'Backend admin password' -AsSecureString
        $Credential = [System.Management.Automation.PSCredential]::new($email, $password)
    }
    Add-Type -AssemblyName System.Net.Http
    $handler = [System.Net.Http.HttpClientHandler]::new()
    $handler.AllowAutoRedirect = $false
    $client = [System.Net.Http.HttpClient]::new($handler)
    $client.Timeout = [timespan]::FromMinutes(20)
    $baseUrl = $ApiBaseUrl.AbsoluteUri.TrimEnd('/')
    $loginJson = @{ email = $Credential.UserName; password = $Credential.GetNetworkCredential().Password } | ConvertTo-Json -Compress
    $loginContent = [System.Net.Http.StringContent]::new($loginJson, [System.Text.Encoding]::UTF8, 'application/json')
    $loginTimeout = [System.Threading.CancellationTokenSource]::new([timespan]::FromSeconds(120))
    $login = $null
    try {
        $login = $client.PostAsync("$baseUrl/api/auth/login", $loginContent, $loginTimeout.Token).GetAwaiter().GetResult()
        if (-not $login.IsSuccessStatusCode) { throw "Admin login failed (HTTP $([int]$login.StatusCode))." }
        $auth = $login.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
        if (-not $auth.token) { throw 'Backend login returned no token.' }
        $client.DefaultRequestHeaders.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $auth.token)
    } finally {
        if ($null -ne $login) { $login.Dispose() }
        $loginContent.Dispose()
        $loginTimeout.Dispose()
        $loginJson = $null
        $auth = $null
    }

    $form = [System.Net.Http.MultipartFormDataContent]::new()
    $parts = @{ prices = $PricesFile }
    if ($Mode -eq 'full') { $parts.stations = $StationsFile }
    foreach ($part in $parts.GetEnumerator()) {
        $content = [System.Net.Http.StreamContent]::new([System.IO.File]::OpenRead($part.Value))
        $content.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::new('text/csv')
        $form.Add($content, $part.Key, [System.IO.Path]::GetFileName($part.Value))
    }
    $endpoint = '/api/admin/mimit/import/prices/upload'
    if ($Mode -eq 'full') { $endpoint = '/api/admin/mimit/import/upload' }
    Write-Host "Uploading CSV files to backend ($Mode). Waiting for import result."
    # Never retry POST: a lost response may follow already committed batches.
    $response = $client.PostAsync("$baseUrl$endpoint", $form).GetAwaiter().GetResult()
    $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    if (-not $response.IsSuccessStatusCode) {
        throw "Upload/import failed (HTTP $([int]$response.StatusCode)): $body. Check backend logs before retrying."
    }
    $result = $body | ConvertFrom-Json
    $result | ConvertTo-Json -Depth 5
    if ($result.pricesRead -le 0 -or ($result.pricesInserted + $result.pricesUpdated) -le 0 -or $result.errors -ne 0) {
        throw 'Import returned no applied prices or reported errors. Inspect the result above.'
    }
    Write-Host 'MIMIT import completed.'
} catch {
    throw "MIMIT local import failed: $($_.Exception.Message). For upload timeouts, check backend logs before retrying."
} finally {
    if ($null -ne $response) { $response.Dispose() }
    if ($null -ne $form) { $form.Dispose() }
    if ($null -ne $client) { $client.Dispose() }
    foreach ($name in @('prices.csv', 'stations.csv')) {
        $download = Join-Path $workDir $name
        if (Test-Path -LiteralPath $download) { Remove-Item -LiteralPath $download -Force }
    }
    Remove-Item -LiteralPath $workDir
}
