$ErrorActionPreference = 'Stop'

$tpRepo = 'https://github.com/zeldaret/tp.git'
$tpPin = 'c8fa8c9e2aab72cf4e5db0e5d1c84a9ea6ee6eb0'
$tpTarget = 'GZ2E01'
$workspace = Join-Path $env:LOCALAPPDATA 'Matrix3\TPDecomp'
$discTarget = Join-Path $workspace "orig\$tpTarget"

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

function Resolve-Python {
    $command = Get-Command python.exe -ErrorAction SilentlyContinue
    if ($command) {
        return @{ Exe = $command.Source; Prefix = @() }
    }

    $command = Get-Command py.exe -ErrorAction SilentlyContinue
    if ($command) {
        return @{ Exe = $command.Source; Prefix = @('-3') }
    }

    throw 'Python 3 was not found. Install Python 3 and make it available to Windows, then run this button again.'
}

$python = Resolve-Python

function Invoke-Python {
    param(
        [string[]]$Arguments = @(),
        [string]$WorkingDirectory = $null
    )

    $allArgs = @($python.Prefix) + @($Arguments)
    Invoke-External -FilePath $python.Exe -Arguments $allArgs -WorkingDirectory $WorkingDirectory
}

function Resolve-Ninja {
    $command = Get-Command ninja.exe -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    Write-Host 'Ninja was not found. Installing the documented user-local Ninja package...' -ForegroundColor Yellow
    Invoke-Python -Arguments @('-m', 'pip', 'install', '--user', 'ninja')

    $command = Get-Command ninja.exe -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    $userBaseLines = & $python.Exe @($python.Prefix) -m site --user-base
    if ($LASTEXITCODE -eq 0) {
        $userBase = [string]($userBaseLines | Select-Object -Last 1)
        if ($userBase -and (Test-Path $userBase)) {
            $candidate = Get-ChildItem -Path $userBase -Filter 'ninja.exe' -File -Recurse -ErrorAction SilentlyContinue |
                Select-Object -First 1
            if ($candidate) {
                return $candidate.FullName
            }
        }
    }

    throw 'Ninja could not be located after installation. Send this build window log; do not start manually extracting the disc.'
}

function Select-TpDisc {
    Add-Type -AssemblyName System.Windows.Forms

    $dialog = New-Object System.Windows.Forms.OpenFileDialog
    $dialog.Title = 'Select Twilight Princess GameCube USA (GZ2E01)'
    $dialog.Filter = 'Supported disc images (*.iso;*.gcm;*.rvz;*.wia;*.wbfs;*.ciso;*.nfs;*.gcz;*.tgc)|*.iso;*.gcm;*.rvz;*.wia;*.wbfs;*.ciso;*.nfs;*.gcz;*.tgc|All files (*.*)|*.*'
    $dialog.Multiselect = $false
    $dialog.CheckFileExists = $true

    if ($dialog.ShowDialog() -ne [System.Windows.Forms.DialogResult]::OK) {
        throw 'No Twilight Princess disc image was selected.'
    }

    return $dialog.FileName
}

function Assert-RawDiscId {
    param([Parameter(Mandatory = $true)][string]$Path)

    $extension = [System.IO.Path]::GetExtension($Path).ToLowerInvariant()
    if ($extension -ne '.iso' -and $extension -ne '.gcm') {
        Write-Host "Selected $extension image. The TP decomp will validate the supported compressed format." -ForegroundColor DarkGray
        return
    }

    $bytes = New-Object byte[] 6
    $stream = [System.IO.File]::OpenRead($Path)
    try {
        if ($stream.Read($bytes, 0, 6) -ne 6) {
            throw 'The selected disc image is too small to contain a GameCube game ID.'
        }
    }
    finally {
        $stream.Dispose()
    }

    $gameId = [System.Text.Encoding]::ASCII.GetString($bytes)
    if ($gameId -ne $tpTarget) {
        throw "Wrong Twilight Princess image. Expected GameCube USA $tpTarget, but the file reports '$gameId'."
    }

    Write-Host "Disc identity VERIFIED: $gameId (GameCube North America)." -ForegroundColor Green
}

$git = Get-Command git.exe -ErrorAction SilentlyContinue
if (-not $git) {
    throw 'Git was not found. Install Git for Windows, then run this button again.'
}

$ninjaExe = Resolve-Ninja

Write-Host "TP source pin: $tpPin"
Write-Host "Local donor workspace: $workspace"
Write-Host ''

$workspaceParent = Split-Path $workspace -Parent
if (-not (Test-Path $workspaceParent)) {
    New-Item -ItemType Directory -Path $workspaceParent -Force | Out-Null
}

if (-not (Test-Path (Join-Path $workspace '.git'))) {
    if ((Test-Path $workspace) -and (Get-ChildItem -LiteralPath $workspace -Force -ErrorAction SilentlyContinue | Select-Object -First 1)) {
        throw "The TP workspace exists but is not a Git checkout: $workspace. Rename/delete that folder or send this log."
    }

    Write-Host 'Cloning zeldaret/tp. This is a one-time setup...' -ForegroundColor Cyan
    Invoke-External -FilePath $git.Source -Arguments @('clone', '--filter=blob:none', $tpRepo, $workspace)
}

& $git.Source -C $workspace cat-file -e "$tpPin^{commit}" 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Host 'Fetching the pinned TP source commit...' -ForegroundColor Cyan
    Invoke-External -FilePath $git.Source -Arguments @('-C', $workspace, 'fetch', '--depth', '1', 'origin', $tpPin)
}

Invoke-External -FilePath $git.Source -Arguments @('-C', $workspace, 'checkout', '--detach', $tpPin)

if (-not (Test-Path $discTarget)) {
    $discPath = Select-TpDisc
    Assert-RawDiscId -Path $discPath

    $origDir = Split-Path $discTarget -Parent
    if (-not (Test-Path $origDir)) {
        New-Item -ItemType Directory -Path $origDir -Force | Out-Null
    }

    Write-Host 'Preparing the local GZ2E01 image for the decomp...' -ForegroundColor Cyan
    try {
        New-Item -ItemType HardLink -Path $discTarget -Target $discPath -ErrorAction Stop | Out-Null
        Write-Host 'Used an NTFS hard link, so no second 1.35 GB copy was needed.' -ForegroundColor Green
    }
    catch {
        Write-Host 'Hard link was unavailable; copying the image once into the local TP workspace...' -ForegroundColor Yellow
        Copy-Item -LiteralPath $discPath -Destination $discTarget -Force
    }
}
else {
    Write-Host 'Reusing the already prepared local GZ2E01 image.' -ForegroundColor Green
}

Write-Host ''
Write-Host 'Configuring the pinned Twilight Princess decomp...' -ForegroundColor Cyan
Invoke-Python -Arguments @('configure.py') -WorkingDirectory $workspace

Write-Host ''
Write-Host 'Building GZ2E01...' -ForegroundColor Cyan
Invoke-External -FilePath $ninjaExe -WorkingDirectory $workspace

Write-Host ''
Write-Host 'TP donor bootstrap complete.' -ForegroundColor Green
Write-Host "Pinned source: $tpPin"
Write-Host "Workspace: $workspace"
Write-Host 'Matrix3 integration is intentionally the next slice; this step only proves the donor source/build foundation.'
