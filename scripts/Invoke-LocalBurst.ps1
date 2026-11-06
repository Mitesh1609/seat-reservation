#requires -Version 7.0

[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$AdminUsername = $env:BURST_ADMIN_USERNAME,
    [string]$AdminPassword = $env:BURST_ADMIN_PASSWORD,
    [ValidateRange(1, 20000)]
    [int]$HotRequestCount = 100,
    [ValidateRange(1, 500)]
    [int]$Concurrency = 20,
    [ValidateRange(2, 500)]
    [int]$UserCount = 10,
    [ValidateRange(1, 100)]
    [int]$PerUserLimit = 4,
    [ValidateRange(1, 300)]
    [int]$RequestTimeoutSeconds = 30,
    [switch]$AllowHighLoad
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')

if ([string]::IsNullOrWhiteSpace($AdminUsername) -or
        [string]::IsNullOrWhiteSpace($AdminPassword)) {
    throw 'Set BURST_ADMIN_USERNAME and BURST_ADMIN_PASSWORD before running the script.'
}

if (($HotRequestCount -gt 500 -or $Concurrency -gt 50 -or $UserCount -gt 50) -and
        -not $AllowHighLoad) {
    throw 'High-load values require -AllowHighLoad. Start with the safe defaults on a local machine.'
}

function Convert-ResponseContentToText {
    param([object]$Content)

    if ($null -eq $Content) {
        return ''
    }
    if ($Content -is [byte[]]) {
        return [System.Text.Encoding]::UTF8.GetString($Content)
    }
    return [string]$Content
}

function Invoke-JsonApi {
    param(
        [Parameter(Mandatory)]
        [ValidateSet('GET', 'POST')]
        [string]$Method,
        [Parameter(Mandatory)]
        [string]$Path,
        [object]$Body,
        [string]$Token,
        [Parameter(Mandatory)]
        [string]$RequestId
    )

    $headers = @{ 'X-Request-ID' = $RequestId }
    if (-not [string]::IsNullOrWhiteSpace($Token)) {
        $headers.Authorization = "Bearer $Token"
    }

    $arguments = @{
        Uri                  = "$BaseUrl$Path"
        Method               = $Method
        Headers              = $headers
        SkipHttpErrorCheck   = $true
        TimeoutSec           = $RequestTimeoutSeconds
        UseBasicParsing      = $true
    }
    if ($null -ne $Body) {
        $arguments.ContentType = 'application/json'
        $arguments.Body = $Body | ConvertTo-Json -Depth 10 -Compress
    }

    $response = Invoke-WebRequest @arguments
    return [pscustomobject]@{
        Status = [int]$response.StatusCode
        Body   = Convert-ResponseContentToText $response.Content
    }
}

function Get-ResultReason {
    param([object]$Result)

    if ($Result.Status -eq 201) {
        return 'confirmed'
    }
    if ($Result.Status -eq 0) {
        return 'network_error'
    }

    $message = ''
    if (-not [string]::IsNullOrWhiteSpace($Result.Body)) {
        try {
            $message = ($Result.Body | ConvertFrom-Json).message
        } catch {
            $message = ''
        }
    }

    $reason = switch ($message) {
        'Seats are not available' { 'seat_taken' }
        'Seat limit exceeded' { 'per_user_limit' }
        'Invalid request' { 'idempotency_conflict' }
        default {
            if ($Result.Status -ge 500) { 'server_error' }
            elseif ($Result.Status -ge 400) { 'other_decline' }
            else { "http_$($Result.Status)" }
        }
    }
    return $reason
}

function Invoke-ConcurrentRequests {
    param(
        [Parameter(Mandatory)]
        [object[]]$WorkItems,
        [Parameter(Mandatory)]
        [int]$ThrottleLimit
    )

    return @($WorkItems | ForEach-Object -Parallel {
        $item = $_
        $headers = @{
            Authorization  = "Bearer $($item.Token)"
            'X-Request-ID' = $item.RequestId
        }
        try {
            $response = Invoke-WebRequest `
                -Uri $item.Uri `
                -Method Post `
                -Headers $headers `
                -ContentType 'application/json' `
                -Body $item.Body `
                -SkipHttpErrorCheck `
                -TimeoutSec $item.TimeoutSeconds `
                -UseBasicParsing
            $content = if ($response.Content -is [byte[]]) {
                [System.Text.Encoding]::UTF8.GetString($response.Content)
            } else {
                [string]$response.Content
            }
            [pscustomobject]@{
                Index  = $item.Index
                Status = [int]$response.StatusCode
                Body   = $content
                Error  = $null
            }
        } catch {
            [pscustomobject]@{
                Index  = $item.Index
                Status = 0
                Body   = ''
                Error  = $_.Exception.Message
            }
        }
    } -ThrottleLimit $ThrottleLimit)
}

function Get-ReasonCount {
    param(
        [Parameter(Mandatory)]
        [object[]]$Results,
        [Parameter(Mandatory)]
        [string]$Reason
    )

    return @($Results | Where-Object { (Get-ResultReason $_) -eq $Reason }).Count
}

$runId = "burst-$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())-$([Guid]::NewGuid().ToString('N').Substring(0, 6))"
$hotSeat = 'HOT-1'
$limitAttemptCount = $PerUserLimit + 2
$limitSeats = 1..$limitAttemptCount | ForEach-Object { "LIMIT-$_" }
$showSeats = @($hotSeat) + @($limitSeats)

Write-Output "Run: $runId"
Write-Output "Target: $BaseUrl"
Write-Output "Hot-seat requests: $HotRequestCount; concurrency: $Concurrency; users: $UserCount"

$adminLogin = Invoke-JsonApi `
    -Method POST `
    -Path '/web/v1/auth/login' `
    -Body @{ username = $AdminUsername; password = $AdminPassword } `
    -RequestId "$runId-admin-login"
if ($adminLogin.Status -ne 200) {
    throw "Administrator login failed with HTTP $($adminLogin.Status)."
}
$adminToken = ($adminLogin.Body | ConvertFrom-Json).access_token

$showResponse = Invoke-JsonApi `
    -Method POST `
    -Path '/web/v1/show/create' `
    -Token $adminToken `
    -Body @{
        name             = $runId
        seats            = $showSeats
        price_paise      = 1000
        per_user_limit   = $PerUserLimit
    } `
    -RequestId "$runId-create-show"
if ($showResponse.Status -ne 201) {
    throw "Show creation failed with HTTP $($showResponse.Status): $($showResponse.Body)"
}
$showId = ($showResponse.Body | ConvertFrom-Json).id
Write-Output "Created show: $showId"

$buyers = [System.Collections.Generic.List[object]]::new()
for ($index = 0; $index -lt ($UserCount + 1); $index++) {
    $username = "$runId-user-$index"
    $password = "Local-burst-$runId-$index!"
    $createUser = Invoke-JsonApi `
        -Method POST `
        -Path '/web/v1/user/create' `
        -Body @{ username = $username; password = $password } `
        -RequestId "$runId-create-user-$index"
    if ($createUser.Status -ne 201) {
        throw "User creation failed with HTTP $($createUser.Status): $($createUser.Body)"
    }

    $login = Invoke-JsonApi `
        -Method POST `
        -Path '/web/v1/auth/login' `
        -Body @{ username = $username; password = $password } `
        -RequestId "$runId-login-user-$index"
    if ($login.Status -ne 200) {
        throw "Buyer login failed with HTTP $($login.Status)."
    }
    $buyers.Add([pscustomobject]@{
        Username = $username
        Token    = ($login.Body | ConvertFrom-Json).access_token
    })
}

$hotWork = [System.Collections.Generic.List[object]]::new()
for ($index = 0; $index -lt $HotRequestCount; $index++) {
    $buyer = $buyers[$index % $UserCount]
    $idempotencyKey = "$runId-hot-$index"
    $hotWork.Add([pscustomobject]@{
        Index           = $index
        Token           = $buyer.Token
        RequestId       = "$runId-hot-request-$index"
        IdempotencyKey  = $idempotencyKey
        Uri             = "$BaseUrl/web/v1/shows/$showId/reserve"
        Body            = (@{ seats = @($hotSeat); idempotency_key = $idempotencyKey } |
                ConvertTo-Json -Compress)
        TimeoutSeconds  = $RequestTimeoutSeconds
    })
}

Write-Output 'Running hot-seat contention...'
$hotResults = Invoke-ConcurrentRequests -WorkItems $hotWork.ToArray() -ThrottleLimit $Concurrency
$hotConfirmed = Get-ReasonCount -Results $hotResults -Reason 'confirmed'
$hotSeatTaken = Get-ReasonCount -Results $hotResults -Reason 'seat_taken'
$hotServerErrors = Get-ReasonCount -Results $hotResults -Reason 'server_error'
$hotNetworkErrors = Get-ReasonCount -Results $hotResults -Reason 'network_error'
$hotOther = $HotRequestCount - $hotConfirmed - $hotSeatTaken - $hotServerErrors - $hotNetworkErrors

Write-Output "Hot seat: confirmed=$hotConfirmed seat_taken=$hotSeatTaken other=$hotOther 5xx=$hotServerErrors network_errors=$hotNetworkErrors"

$winnerResult = $hotResults | Where-Object { $_.Status -eq 201 } | Select-Object -First 1
$replayPassed = $false
if ($null -ne $winnerResult) {
    $winnerWork = $hotWork[$winnerResult.Index]
    $originalReservation = $winnerResult.Body | ConvertFrom-Json
    $replay = Invoke-JsonApi `
        -Method POST `
        -Path "/web/v1/shows/$showId/reserve" `
        -Token $winnerWork.Token `
        -Body @{ seats = @($hotSeat); idempotency_key = $winnerWork.IdempotencyKey } `
        -RequestId "$runId-idempotent-replay"
    if ($replay.Status -eq 201) {
        $replayedReservation = $replay.Body | ConvertFrom-Json
        $replayPassed = $replayedReservation.reservation_id -eq $originalReservation.reservation_id
    }
}
Write-Output "Idempotent replay: $(if ($replayPassed) { 'PASS' } else { 'FAIL' })"

$limitBuyer = $buyers[$UserCount]
$limitWork = [System.Collections.Generic.List[object]]::new()
for ($index = 0; $index -lt $limitAttemptCount; $index++) {
    $idempotencyKey = "$runId-limit-$index"
    $limitWork.Add([pscustomobject]@{
        Index           = $index
        Token           = $limitBuyer.Token
        RequestId       = "$runId-limit-request-$index"
        Uri             = "$BaseUrl/web/v1/shows/$showId/reserve"
        Body            = (@{ seats = @($limitSeats[$index]); idempotency_key = $idempotencyKey } |
                ConvertTo-Json -Compress)
        TimeoutSeconds  = $RequestTimeoutSeconds
    })
}

Write-Output 'Running per-user-limit contention...'
$limitResults = Invoke-ConcurrentRequests `
    -WorkItems $limitWork.ToArray() `
    -ThrottleLimit ([Math]::Min($Concurrency, $limitAttemptCount))
$limitConfirmed = Get-ReasonCount -Results $limitResults -Reason 'confirmed'
$limitDeclined = Get-ReasonCount -Results $limitResults -Reason 'per_user_limit'
$limitServerErrors = Get-ReasonCount -Results $limitResults -Reason 'server_error'
$limitNetworkErrors = Get-ReasonCount -Results $limitResults -Reason 'network_error'
$limitOther = $limitAttemptCount - $limitConfirmed - $limitDeclined - $limitServerErrors - $limitNetworkErrors

Write-Output "User limit: confirmed=$limitConfirmed per_user_limit=$limitDeclined other=$limitOther 5xx=$limitServerErrors network_errors=$limitNetworkErrors"

$showState = Invoke-JsonApi `
    -Method GET `
    -Path "/web/v1/show/$showId" `
    -Token $adminToken `
    -RequestId "$runId-final-state"
if ($showState.Status -ne 200) {
    throw "Final show lookup failed with HTTP $($showState.Status): $($showState.Body)"
}
$counts = ($showState.Body | ConvertFrom-Json).counts
$reconciledTotal = [long]$counts.available + [long]$counts.held + [long]$counts.confirmed
$invariantPassed = $reconciledTotal -eq [long]$counts.total_seats

Write-Output "Final seats: available=$($counts.available) held=$($counts.held) confirmed=$($counts.confirmed) total=$($counts.total_seats)"
Write-Output "Reconciliation invariant: $(if ($invariantPassed) { 'PASS' } else { 'FAIL' })"

$overallPassed = `
    $hotConfirmed -eq 1 -and `
    $hotSeatTaken -eq ($HotRequestCount - 1) -and `
    $hotServerErrors -eq 0 -and `
    $hotNetworkErrors -eq 0 -and `
    $hotOther -eq 0 -and `
    $replayPassed -and `
    $limitConfirmed -eq $PerUserLimit -and `
    $limitDeclined -eq 2 -and `
    $limitServerErrors -eq 0 -and `
    $limitNetworkErrors -eq 0 -and `
    $limitOther -eq 0 -and `
    $invariantPassed -and `
    [long]$counts.confirmed -eq (1 + $PerUserLimit)

Write-Output "Overall: $(if ($overallPassed) { 'PASS' } else { 'FAIL' })"
if (-not $overallPassed) {
    exit 1
}
