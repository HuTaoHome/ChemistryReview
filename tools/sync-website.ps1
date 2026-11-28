param([string]$Site = 'https://chem-review-hh57.pages.dev', [switch]$ReuseCache)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$webRoot = Join-Path $projectRoot 'app/src/main/assets/web'
$cacheRoot = Join-Path $projectRoot '.build/website/cards'
New-Item -ItemType Directory -Force $webRoot, "$webRoot/assets", "$webRoot/offline", $cacheRoot | Out-Null
$html = (Invoke-WebRequest "$Site/" -TimeoutSec 40).Content
$jsPath = [regex]::Match($html, 'src="(/assets/[^"]+\.js)"').Groups[1].Value
$cssPath = [regex]::Match($html, 'href="(/assets/[^"]+\.css)"').Groups[1].Value
if (!$jsPath -or !$cssPath) { throw 'Cannot locate the website entry assets.' }
Invoke-WebRequest "$Site$jsPath" -OutFile "$webRoot$jsPath" -TimeoutSec 60
Invoke-WebRequest "$Site$cssPath" -OutFile "$webRoot$cssPath" -TimeoutSec 60
$js = Get-Content "$webRoot$jsPath" -Raw
$original = 'function tl(){return vo||(vo=Promise.resolve(y3)),vo}'
$replacement = 'function tl(){return vo||(vo=Promise.resolve(window.ChemData.createProvider(y3,Zm))),vo}'
if (!$js.Contains($original)) { throw 'Website data adapter changed: review the provider hook before updating.' }
[IO.File]::WriteAllText("$webRoot$jsPath", $js.Replace($original, $replacement))
$css = Get-Content "$webRoot$cssPath" -Raw
$fontPaths = [regex]::Matches($css, '/assets/[^)\s"'']+\.(?:woff2|woff|ttf)') | ForEach-Object Value | Sort-Object -Unique
$fontPaths | ForEach-Object -Parallel {
    $target = "$using:webRoot$_"
    if (!(Test-Path $target)) {
        for ($attempt=1; $attempt -le 4; $attempt++) {
            try { Invoke-WebRequest "$using:Site$_" -OutFile $target -TimeoutSec 40 -ErrorAction Stop; break }
            catch { if ($attempt -eq 4) { throw }; Start-Sleep -Seconds 2 }
        }
    }
} -ThrottleLimit 4
$index = Invoke-RestMethod "$Site/api/index" -TimeoutSec 40
$index.cards | ForEach-Object -Parallel {
    $id = $_.id
    $target = Join-Path $using:cacheRoot "$id.json"
    if (!$using:ReuseCache -or !(Test-Path $target)) {
        for ($attempt=1; $attempt -le 4; $attempt++) {
            try {
                $result = Invoke-RestMethod "$using:Site/api/card" -Method Post -ContentType 'application/json' -Body (@{id=$id} | ConvertTo-Json -Compress) -TimeoutSec 40 -ErrorAction Stop
                if ($result.card.id -ne $id -or !$result.card.answers) { throw "Incomplete card: $id" }
                $result.card | ConvertTo-Json -Depth 30 | Set-Content -Encoding utf8 $target
                break
            } catch { if ($attempt -eq 4) { throw }; Start-Sleep -Seconds 2 }
        }
    }
} -ThrottleLimit 4
$fullCards = @($index.cards | ForEach-Object { Get-Content (Join-Path $cacheRoot "$($_.id).json") -Raw | ConvertFrom-Json })
$reviewCards = @()
for ($start=0; $start -lt $index.cards.Count; $start+=25) {
    $end = [Math]::Min($start+24,$index.cards.Count-1)
    $ids = @($index.cards[$start..$end] | ForEach-Object id)
    $result = Invoke-RestMethod "$Site/api/cards" -Method Post -ContentType 'application/json' -Body (@{ids=$ids} | ConvertTo-Json -Compress) -TimeoutSec 40
    $reviewCards += $result.cards
}
if ($fullCards.Count -ne $index.cards.Count -or $reviewCards.Count -ne $index.cards.Count) { throw 'Incomplete offline snapshot.' }
$snapshot = @{ source=$Site; capturedAt=(Get-Date).ToUniversalTime().ToString('o'); index=$index; cards=$fullCards; reviewCards=$reviewCards }
$snapshot | ConvertTo-Json -Depth 40 -Compress | Set-Content -Encoding utf8 "$webRoot/offline/data.json"
# Keep the original author attribution and UI. Remove analytics for the bundled app.
$html = [regex]::Replace($html, '(?s)<!-- Cloudflare Web Analytics -->.*?<!-- End Cloudflare Web Analytics -->', '')
$html = $html.Replace('<script type="module"', '<script src="/offline/provider.js"></script><script type="module"')
[IO.File]::WriteAllText("$webRoot/index.html", $html)
& node (Join-Path $PSScriptRoot 'customize-web.cjs')
if ($LASTEXITCODE -ne 0) { throw 'Website customization failed.' }
Write-Output "Offline snapshot ready: $($fullCards.Count) cards, $($fontPaths.Count) font files."
