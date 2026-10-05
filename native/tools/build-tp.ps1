$ErrorActionPreference = 'Stop'

$tpRepo = 'https://github.com/zeldaret/tp.git'
$tpPin = 'c8fa8c9e2aab72cf4e5db0e5d1c84a9ea6ee6eb0'
$tpTarget = 'GZ2E01'
$workspace = Join-Path $env:LOCALAPPDATA 'Matrix3\TPDecomp'
$discBaseDir = Join-Path $workspace "orig\$tpTarget"
$nativeDir = Split-Path $PSScriptRoot -Parent
$supportedDiscExtensions = @('.iso', '.gcm', '.rvz', '.wia', '.wbfs', '.ciso', '.nfs', '.gcz', '.tgc')
$msys2Shell = $env:MATRIX3_MSYS2_SHELL
if (-not $msys2Shell) {
    $msys2Shell = 'C:\msys64\msys2_shell.cmd'
}
$msys2Root = Split-Path $msys2Shell -Parent
$msys2UcrtBin = Join-Path $msys2Root 'ucrt64\bin'

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

    $candidates = @(
        (Join-Path $msys2UcrtBin 'python.exe'),
        (Join-Path $msys2Root 'usr\bin\python.exe')
    )

    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            $spec = @{ Exe = $candidate; Prefix = @() }
            if (Test-PythonSpec -Spec $spec) {
                return $spec
            }
        }
    }

    return $null
}

function Find-Ninja {
    $ninja = Get-Command ninja.exe -ErrorAction SilentlyContinue
    if ($ninja) {
        return $ninja.Source
    }

    $candidate = Join-Path $msys2UcrtBin 'ninja.exe'
    if (Test-Path $candidate) {
        return $candidate
    }

    return $null
}

function Ensure-Msys2BootstrapTools {
    if (-not (Test-Path $msys2Shell)) {
        throw "Python 3 and/or Ninja are missing, and the existing Matrix3 MSYS2 shell was not found at '$msys2Shell'. Send this log; do not install random tools manually."
    }

    Write-Host 'Python 3 / Ninja are missing from native Windows PATH.' -ForegroundColor Yellow
    Write-Host 'Installing the required UCRT64 Python + Ninja packages through the existing Matrix3 MSYS2 toolchain...' -ForegroundColor Cyan

    & $msys2Shell -defterm -here -no-start -ucrt64 -c "pacman -S --needed --noconfirm mingw-w64-ucrt-x86_64-python mingw-w64-ucrt-x86_64-ninja"
    if ($LASTEXITCODE -ne 0) {
        throw "MSYS2 could not install Python/Ninja (exit $LASTEXITCODE). Send this build window log."
    }
}

function Resolve-Toolchain {
    $pythonSpec = Find-Python
    $ninjaPath = Find-Ninja

    if (-not $pythonSpec -or -not $ninjaPath) {
        Ensure-Msys2BootstrapTools
        $pythonSpec = Find-Python
        $ninjaPath = Find-Ninja
    }

    if (-not $pythonSpec) {
        throw 'A real Python 3 executable still could not be found after the automatic MSYS2 setup. The Microsoft Store app-execution alias is intentionally ignored.'
    }

    if (-not $ninjaPath) {
        throw 'Ninja still could not be found after the automatic MSYS2 setup.'
    }

    return @{ Python = $pythonSpec; Ninja = $ninjaPath }
}

function Invoke-Python {
    param(
        [Parameter(Mandatory = $true)][hashtable]$PythonSpec,
        [string[]]$Arguments = @(),
        [string]$WorkingDirectory = $null
    )

    $allArgs = @($PythonSpec.Prefix) + @($Arguments)
    Invoke-External -FilePath $PythonSpec.Exe -Arguments $allArgs -WorkingDirectory $WorkingDirectory
}

function Test-IsNkitV1Name {
    param([Parameter(Mandatory = $true)][string]$Path)
    return [System.IO.Path]::GetFileName($Path).ToLowerInvariant().EndsWith('.nkit.iso')
}

function Find-SingleSupportedDisc {
    param([Parameter(Mandatory = $true)][string]$Directory)

    if (-not (Test-Path -LiteralPath $Directory -PathType Container)) {
        return $null
    }

    $files = @(Get-ChildItem -LiteralPath $Directory -File -ErrorAction SilentlyContinue | Where-Object {
        ($supportedDiscExtensions -contains $_.Extension.ToLowerInvariant()) -and
        (-not (Test-IsNkitV1Name -Path $_.FullName))
    })

    if ($files.Count -eq 1) {
        return $files[0].FullName
    }

    if ($files.Count -gt 1) {
        throw "Multiple supported TP disc images were found in '$Directory'. Keep one prepared donor image there and rerun the builder."
    }

    return $null
}

function Find-LocalTpDisc {
    if (-not (Test-Path $nativeDir)) {
        return $null
    }

    $all = @(Get-ChildItem -LiteralPath $nativeDir -File -ErrorAction SilentlyContinue | Where-Object {
        $supportedDiscExtensions -contains $_.Extension.ToLowerInvariant()
    })

    if ($all.Count -eq 0) {
        return $null
    }

    $named = @($all | Where-Object { $_.Name -match '(?i)twilight\s*princess|zelda.*twilight|GZ2E01' })
    $pool = if ($named.Count -gt 0) { $named } else { $all }
    $nonNkit = @($pool | Where-Object { -not (Test-IsNkitV1Name -Path $_.FullName) })

    if ($nonNkit.Count -eq 1) {
        return $nonNkit[0].FullName
    }

    if ($nonNkit.Count -eq 0 -and $pool.Count -eq 1) {
        return $pool[0].FullName
    }

    return $null
}

function Select-TpDisc {
    $localDisc = Find-LocalTpDisc
    if ($localDisc) {
        Write-Host 'Found the Twilight Princess image beside the Matrix3 native tools:' -ForegroundColor Green
        Write-Host "  $localDisc"
        return $localDisc
    }

    Add-Type -AssemblyName System.Windows.Forms

    $dialog = New-Object System.Windows.Forms.OpenFileDialog
    $dialog.Title = 'Select Twilight Princess GameCube USA (GZ2E01)'
    $dialog.InitialDirectory = $nativeDir
    $dialog.Filter = 'Supported disc images (*.iso;*.gcm;*.rvz;*.wia;*.wbfs;*.ciso;*.nfs;*.gcz;*.tgc)|*.iso;*.gcm;*.rvz;*.wia;*.wbfs;*.ciso;*.nfs;*.gcz;*.tgc|All files (*.*)|*.*'
    $dialog.Multiselect = $false
    $dialog.CheckFileExists = $true

    if ($dialog.ShowDialog() -ne [System.Windows.Forms.DialogResult]::OK) {
        throw 'No Twilight Princess disc image was selected.'
    }

    return $dialog.FileName
}

function Assert-SupportedTpImage {
    param([Parameter(Mandatory = $true)][string]$Path)

    if (Test-IsNkitV1Name -Path $Path) {
        throw @"
Found your Twilight Princess file automatically, but it is an NKit v1 image:
  $Path

zeldaret/tp does not document .nkit.iso as a supported donor format.
Use a normal GZ2E01 ISO/GCM, RVZ, WIA, WBFS, CISO, NFS, GCZ or TGC image instead.
You can place the replacement directly in:
  $nativeDir
Then click PREP + BUILD TP LINK again; the builder will find it automatically.
"@
    }
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

$preparedDisc = Find-SingleSupportedDisc -Directory $discBaseDir
$discPath = $null
if ($preparedDisc) {
    Write-Host 'Reusing the already prepared local GZ2E01 donor image:' -ForegroundColor Green
    Write-Host "  $preparedDisc"
}
else {
    $discPath = Select-TpDisc
    Assert-SupportedTpImage -Path $discPath
    Assert-RawDiscId -Path $discPath
}

$git = Get-Command git.exe -ErrorAction SilentlyContinue
if (-not $git) {
    throw 'Git was not found. Install Git for Windows, then run this button again.'
}

$toolchain = Resolve-Toolchain
$python = $toolchain.Python
$ninjaExe = $toolchain.Ninja

Write-Host "TP source pin: $tpPin"
Write-Host "Local donor workspace: $workspace"
Write-Host "Python: $($python.Exe)"
Write-Host "Ninja: $ninjaExe"
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

if (Test-Path -LiteralPath $discBaseDir -PathType Leaf) {
    Write-Host 'Repairing the previous TP donor layout (disc image was placed where decomp-toolkit expects a directory)...' -ForegroundColor Yellow
    Remove-Item -LiteralPath $discBaseDir -Force
}

$preparedDisc = Find-SingleSupportedDisc -Directory $discBaseDir
if (-not $preparedDisc) {
    if (-not $discPath) {
        $discPath = Select-TpDisc
        Assert-SupportedTpImage -Path $discPath
        Assert-RawDiscId -Path $discPath
    }

    if (-not (Test-Path $discBaseDir)) {
        New-Item -ItemType Directory -Path $discBaseDir -Force | Out-Null
    }

    $discTarget = Join-Path $discBaseDir ([System.IO.Path]::GetFileName($discPath))
    Write-Host 'Preparing the local GZ2E01 image for decomp-toolkit...' -ForegroundColor Cyan
    try {
        New-Item -ItemType HardLink -Path $discTarget -Target $discPath -ErrorAction Stop | Out-Null
        Write-Host 'Used an NTFS hard link, so no second disc-image copy was needed.' -ForegroundColor Green
    }
    catch {
        Write-Host 'Hard link was unavailable; copying the image once into the local TP workspace...' -ForegroundColor Yellow
        Copy-Item -LiteralPath $discPath -Destination $discTarget -Force
    }

    $preparedDisc = $discTarget
}

Write-Host 'decomp-toolkit object base:' -ForegroundColor DarkGray
Write-Host "  $discBaseDir"
Write-Host 'Prepared donor image:' -ForegroundColor DarkGray
Write-Host "  $preparedDisc"

Write-Host ''
Write-Host 'Configuring the pinned Twilight Princess decomp...' -ForegroundColor Cyan
Invoke-Python -PythonSpec $python -Arguments @('configure.py') -WorkingDirectory $workspace

Write-Host ''
Write-Host 'Building GZ2E01...' -ForegroundColor Cyan
Invoke-External -FilePath $ninjaExe -WorkingDirectory $workspace

Write-Host ''
Write-Host 'TP donor bootstrap complete.' -ForegroundColor Green
Write-Host "Pinned source: $tpPin"
Write-Host "Workspace: $workspace"
Write-Host 'Matrix3 integration is intentionally the next slice; this step only proves the donor source/build foundation.'
