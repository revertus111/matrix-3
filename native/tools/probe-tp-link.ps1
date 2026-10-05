$ErrorActionPreference = 'Stop'

$tpTarget = 'GZ2E01'
$workspace = Join-Path $env:LOCALAPPDATA 'Matrix3\TPDecomp'
$proofDir = Join-Path $env:LOCALAPPDATA 'Matrix3\TPLinkProof'
$rawDir = Join-Path $proofDir 'raw'
$selectedDir = Join-Path $proofDir 'selected'
$visualDir = Join-Path $proofDir 'visual'
$kmdlDir = Join-Path $rawDir 'Kmdl'
$alanmDir = Join-Path $rawDir 'AlAnm'
$dtk = Join-Path $workspace 'build\tools\dtk.exe'
$probeScript = Join-Path $PSScriptRoot 'tp-link-probe.py'
$visualScript = Join-Path $PSScriptRoot 'tp-link-visual-proof.py'
$discDir = Join-Path $workspace "orig\$tpTarget"
$supportedDiscExtensions = @('.iso', '.gcm', '.rvz', '.wia', '.wbfs', '.ciso', '.nfs', '.gcz', '.tgc')
$msys2Python = 'C:\msys64\ucrt64\bin\python.exe'

$toolRoot = Join-Path $env:LOCALAPPDATA 'Matrix3\TPLinkTools'
$demakeRoot = Join-Path $toolRoot 'demake-engine'
$demakeRepo = 'https://github.com/snuri00/demake-engine.git'
$demakePin = 'a134ff49cc74585c6b11f881293796e45c973c75'
$venvDir = Join-Path $toolRoot 'venv'
$venvPython = Join-Path $venvDir 'Scripts\python.exe'

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
    param(
        [Parameter(Mandatory = $true)][hashtable]$Spec,
        [switch]$Require312
    )

    try {
        if ($Require312) {
            & $Spec.Exe @($Spec.Prefix) -c 'import sys; raise SystemExit(0 if sys.version_info >= (3, 12) else 1)' *> $null
        }
        else {
            & $Spec.Exe @($Spec.Prefix) --version *> $null
        }
        return ($LASTEXITCODE -eq 0)
    }
    catch {
        return $false
    }
}

function Get-PythonCandidates {
    $result = @()

    $py = Get-Command py.exe -ErrorAction SilentlyContinue
    if ($py) {
        $result += @{ Exe = $py.Source; Prefix = @('-3.13') }
        $result += @{ Exe = $py.Source; Prefix = @('-3.12') }
        $result += @{ Exe = $py.Source; Prefix = @('-3') }
    }

    $python = Get-Command python.exe -ErrorAction SilentlyContinue
    if ($python -and $python.Source -notmatch '\\WindowsApps\\python\.exe$') {
        $result += @{ Exe = $python.Source; Prefix = @() }
    }

    if (Test-Path $msys2Python) {
        $result += @{ Exe = $msys2Python; Prefix = @() }
    }

    return $result
}

function Find-Python {
    foreach ($spec in (Get-PythonCandidates)) {
        if (Test-PythonSpec -Spec $spec) {
            return $spec
        }
    }
    return $null
}

function Find-Python312 {
    foreach ($spec in (Get-PythonCandidates)) {
        if (Test-PythonSpec -Spec $spec -Require312) {
            return $spec
        }
    }
    return $null
}

function Ensure-VisualToolchain {
    $git = Get-Command git.exe -ErrorAction SilentlyContinue
    if (-not $git) {
        throw 'Git for Windows is required for the pinned TP Link visual proof tool.'
    }

    if (-not (Test-Path $toolRoot)) {
        New-Item -ItemType Directory -Path $toolRoot -Force | Out-Null
    }

    if (-not (Test-Path (Join-Path $demakeRoot '.git'))) {
        if (Test-Path $demakeRoot) {
            Remove-Item -LiteralPath $demakeRoot -Recurse -Force
        }
        Write-Host 'Cloning pinned local-only J3D visual proof tool...' -ForegroundColor Cyan
        Invoke-External -FilePath $git.Source -Arguments @('clone', $demakeRepo, $demakeRoot) | Out-Host
    }

    Write-Host 'Pinning J3D visual proof tool...' -ForegroundColor Cyan
    Invoke-External -FilePath $git.Source -Arguments @('-C', $demakeRoot, 'fetch', 'origin') | Out-Host
    Invoke-External -FilePath $git.Source -Arguments @('-C', $demakeRoot, 'checkout', '--detach', $demakePin) | Out-Host
    $head = (& $git.Source -C $demakeRoot rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or $head -ne $demakePin) {
        throw "Visual proof tool pin mismatch. Expected $demakePin, got $head"
    }

    if (-not (Test-Path $venvPython)) {
        $python312 = Find-Python312
        if (-not $python312) {
            throw 'Python 3.12+ is required for the visual TP Link proof. The donor build may use an older Python, but this pinned J3D converter requires 3.12+.'
        }
        Write-Host 'Creating isolated TP Link visual-proof Python environment...' -ForegroundColor Cyan
        $venvArgs = @($python312.Prefix) + @('-m', 'venv', $venvDir)
        Invoke-External -FilePath $python312.Exe -Arguments $venvArgs | Out-Host
    }

    & $venvPython -c 'import numpy; from PIL import Image' *> $null
    if ($LASTEXITCODE -ne 0) {
        Write-Host 'Installing isolated visual-proof dependencies (numpy + Pillow)...' -ForegroundColor Cyan
        Invoke-External -FilePath $venvPython -Arguments @(
            '-m', 'pip', 'install', '--disable-pip-version-check',
            'numpy>=1.26,<3', 'Pillow>=10,<13'
        ) | Out-Host
    }

    return @{ Root = $demakeRoot; Python = $venvPython }
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
    throw "TP Link asset probe script is missing: $probeScript"
}

if (-not (Test-Path $visualScript)) {
    throw "TP Link visual probe script is missing: $visualScript"
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
Write-Host 'Matrix3 - Twilight Princess Link visual proof' -ForegroundColor Cyan
Write-Host '================================================' -ForegroundColor DarkGray
Write-Host "Donor: $($disc.FullName)"
Write-Host "Proof output: $proofDir"
Write-Host ''

foreach ($path in @($rawDir, $selectedDir, $visualDir)) {
    if (Test-Path $path) {
        Remove-Item -LiteralPath $path -Recurse -Force
    }
}
foreach ($path in @((Join-Path $proofDir 'manifest.json'), (Join-Path $proofDir 'summary.txt'))) {
    if (Test-Path $path) {
        Remove-Item -LiteralPath $path -Force
    }
}
New-Item -ItemType Directory -Path $kmdlDir -Force | Out-Null
New-Item -ItemType Directory -Path $alanmDir -Force | Out-Null
New-Item -ItemType Directory -Path $proofDir -Force | Out-Null

Write-Host 'Extracting Link Kmdl resources only...' -ForegroundColor Cyan
Invoke-External -FilePath $dtk -Arguments @('vfs', 'cp', $kmdlSource, $kmdlDir) -WorkingDirectory $workspace

Write-Host 'Extracting Link AlAnm animation archive only...' -ForegroundColor Cyan
Invoke-External -FilePath $dtk -Arguments @('vfs', 'cp', $alanmSource, $alanmDir) -WorkingDirectory $workspace

Write-Host ''
Write-Host 'Validating authentic TP Link model, idle/walk/sword clips and weapon socket...' -ForegroundColor Cyan
$probeArgs = @($python.Prefix) + @(
    $probeScript,
    '--kmdl', $kmdlDir,
    '--alanm', $alanmDir,
    '--out', $proofDir
)
Invoke-External -FilePath $python.Exe -Arguments $probeArgs

$summary = Join-Path $proofDir 'summary.txt'
$manifest = Join-Path $proofDir 'manifest.json'
if (-not (Test-Path $summary) -or -not (Test-Path $manifest)) {
    throw 'Asset probe returned successfully but did not create summary.txt + manifest.json.'
}

Write-Host ''
Write-Host 'Building visible TP Link animation + 0xF socket proof...' -ForegroundColor Cyan
$visualTools = Ensure-VisualToolchain
Invoke-External -FilePath $visualTools.Python -Arguments @(
    $visualScript,
    '--demake-root', $visualTools.Root,
    '--manifest', $manifest,
    '--out', $proofDir
)

$visualSummary = Join-Path $visualDir 'visual-summary.txt'
$visualGif = Join-Path $visualDir 'tp-link-proof.gif'
if (-not (Test-Path $visualSummary) -or -not (Test-Path $visualGif)) {
    throw 'Visual proof returned successfully but did not create the expected summary/GIF.'
}

Write-Host ''
Get-Content -LiteralPath $summary | ForEach-Object { Write-Host $_ }
Write-Host ''
Get-Content -LiteralPath $visualSummary | ForEach-Object { Write-Host $_ }
Write-Host ''
Write-Host 'TP Link visible asset/animation/socket proof complete.' -ForegroundColor Green
Write-Host 'No Nintendo assets or third-party converter source were copied into the Matrix3 Git repository.' -ForegroundColor DarkGray

try {
    Start-Process -FilePath $visualGif | Out-Null
}
catch {
    try {
        Start-Process explorer.exe -ArgumentList @($visualDir) | Out-Null
    }
    catch {
        Write-Host "Could not auto-open the proof. Open: $visualGif" -ForegroundColor Yellow
    }
}
