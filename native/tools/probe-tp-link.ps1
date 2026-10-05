$ErrorActionPreference = 'Stop'

$tpTarget = 'GZ2E01'
$workspace = Join-Path $env:LOCALAPPDATA 'Matrix3\TPDecomp'
$proofDir = Join-Path $env:LOCALAPPDATA 'Matrix3\TPLinkProof'
$rawDir = Join-Path $proofDir 'raw'
$kmdlDir = Join-Path $rawDir 'Kmdl'
$alanmDir = Join-Path $rawDir 'AlAnm'
$dtk = Join-Path $workspace 'build\tools\dtk.exe'
$probeScript = Join-Path $PSScriptRoot 'tp-link-probe.py'
$discDir = Join-Path $workspace "orig\$tpTarget"
$supportedDiscExtensions = @('.iso', '.gcm', '.rvz', '.wia', '.wbfs', '.ciso', '.nfs', '.gcz', '.tgc')
$msys2Python = 'C:\msys64\ucrt64\bin\python.exe'

function Invoke-External {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [string[]]$Arguments = @(),
        [string]$WorkingDirectory = $null
    )

    $pushed = $false
    try {
        if ($WorkingDirectory) {
            Push-Location $WorkingDirectory
            $pushed = $true
        }

        & $FilePath @Arguments
        $code = $LASTEXITCODE
    }
    finally {
        if ($pushed) {
            Pop-Location
        }
    }

    if ($code -ne 0) {
        throw "$FilePath exited with code $code."
    }
}

function Test-PythonSpec {
    param([Parameter(Mandatory = $true)][hashtable]$Spec)

    try {
        & $Spec.Exe @($Spec.Prefix) --version *> $null
        return ($LASTEXITCODE -eq 0)
    }
    catch {
        return $false
    }
}

function Find-Python {
    $py = Get-Command py.exe -ErrorAction SilentlyContinue
    if ($py) {
        $spec = @{ Exe = $py.Source; Prefix = @('-3') }
        if (Test-PythonSpec -Spec $spec) {
            return $spec
        }
    }

    $python = Get-Command python.exe -ErrorAction SilentlyContinue
    if ($python -and $python.Source -notmatch '\\WindowsApps\\python\.exe$') {
        $spec = @{ Exe = $python.Source; Prefix = @() }
        if (Test-PythonSpec -Spec $spec) {
            return $spec
        }
    }

    if (Test-Path $msys2Python) {
        $spec = @{ Exe = $msys2Python; Prefix = @() }
        if (Test-PythonSpec -Spec $spec) {
            return $spec
        }
    }

    return $null
}

if (-not (Test-Path $workspace)) {
    throw "TP donor workspace was not found: $workspace. Run PREP + BUILD TP LINK first."
}

if (-not (Test-Path (Join-Path $workspace 'build\GZ2E01\report.json'))) {
    throw 'The successful GZ2E01 donor build report was not found. Run PREP + BUILD TP LINK first.'
}

if (-not (Test-Path $dtk)) {
    throw "decomp-toolkit was not found at $dtk. Run PREP + BUILD TP LINK again."
}

if (-not (Test-Path $probeScript)) {
    throw "TP Link probe script is missing: $probeScript"
}

if (-not (Test-Path $discDir)) {
    throw "Prepared TP donor directory was not found: $discDir"
}

$discCandidates = @(Get-ChildItem -LiteralPath $discDir -File -ErrorAction SilentlyContinue | Where-Object {
    $supportedDiscExtensions -contains $_.Extension.ToLowerInvariant()
})

if ($discCandidates.Count -eq 0) {
    throw "No supported GZ2E01 image was found inside $discDir. Run PREP + BUILD TP LINK again."
}

if ($discCandidates.Count -gt 1) {
    $list = ($discCandidates | ForEach-Object { "  $($_.FullName)" }) -join [Environment]::NewLine
    throw "More than one TP donor image is prepared. Keep only the one used by the successful donor build:`n$list"
}

$python = Find-Python
if (-not $python) {
    throw 'Python 3 could not be found. The verified TP donor builder previously used MSYS2 Python; rerun PREP + BUILD TP LINK if that installation was removed.'
}

$disc = $discCandidates[0]
$relativeDisc = "orig/$tpTarget/$($disc.Name)"
$kmdlSource = "${relativeDisc}:files/res/Object/Kmdl.arc:"
$alanmSource = "${relativeDisc}:files/res/Object/AlAnm.arc:"

Write-Host '================================================' -ForegroundColor DarkGray
Write-Host 'Matrix3 - Twilight Princess Link asset proof' -ForegroundColor Cyan
Write-Host '================================================' -ForegroundColor DarkGray
Write-Host "Donor: $($disc.FullName)"
Write-Host "Proof output: $proofDir"
Write-Host ''

if (Test-Path $rawDir) {
    Remove-Item -LiteralPath $rawDir -Recurse -Force
}
New-Item -ItemType Directory -Path $kmdlDir -Force | Out-Null
New-Item -ItemType Directory -Path $alanmDir -Force | Out-Null
New-Item -ItemType Directory -Path $proofDir -Force | Out-Null

Write-Host 'Extracting Link Kmdl resources only...' -ForegroundColor Cyan
Invoke-External -FilePath $dtk -Arguments @('vfs', 'cp', $kmdlSource, $kmdlDir) -WorkingDirectory $workspace

Write-Host 'Extracting Link AlAnm animation archive only...' -ForegroundColor Cyan
Invoke-External -FilePath $dtk -Arguments @('vfs', 'cp', $alanmSource, $alanmDir) -WorkingDirectory $workspace

Write-Host ''
Write-Host 'Validating authentic TP Link J3D model, animation clips and weapon socket...' -ForegroundColor Cyan
$probeArgs = @($python.Prefix) + @(
    $probeScript,
    '--kmdl', $kmdlDir,
    '--alanm', $alanmDir,
    '--out', $proofDir
)
Invoke-External -FilePath $python.Exe -Arguments $probeArgs

$summary = Join-Path $proofDir 'summary.txt'
if (-not (Test-Path $summary)) {
    throw "Asset probe returned successfully but did not create $summary"
}

Write-Host ''
Get-Content -LiteralPath $summary | ForEach-Object { Write-Host $_ }
Write-Host ''
Write-Host 'TP Link asset proof complete.' -ForegroundColor Green
Write-Host 'No Nintendo assets were copied into the Matrix3 Git repository.' -ForegroundColor DarkGray

try {
    Start-Process explorer.exe -ArgumentList @($proofDir) | Out-Null
}
catch {
    Write-Host "Could not auto-open Explorer. Proof folder: $proofDir" -ForegroundColor Yellow
}
