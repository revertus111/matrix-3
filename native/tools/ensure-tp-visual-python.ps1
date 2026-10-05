$ErrorActionPreference = 'Stop'

$msys2Root = 'C:\msys64'
$msys2Python = Join-Path $msys2Root 'ucrt64\bin\python.exe'
$pacman = Join-Path $msys2Root 'usr\bin\pacman.exe'
$visualPackages = @(
    'mingw-w64-ucrt-x86_64-python-numpy',
    'mingw-w64-ucrt-x86_64-python-pillow'
)

function Test-VisualDependencies {
    param([Parameter(Mandatory = $true)][string]$PythonPath)

    if (-not (Test-Path $PythonPath)) {
        return $false
    }

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'SilentlyContinue'
        & $PythonPath -c 'import numpy; from PIL import Image' *> $null
        return ($LASTEXITCODE -eq 0)
    }
    catch {
        return $false
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
}

if (-not (Test-Path $msys2Python)) {
    throw "Matrix3 MSYS2/UCRT64 Python was not found at $msys2Python. Run PREP + BUILD TP LINK again if the verified MSYS2 toolchain was removed."
}

if (Test-VisualDependencies -PythonPath $msys2Python) {
    Write-Host 'TP Link MSYS2 visual Python environment is ready.' -ForegroundColor DarkGray
    exit 0
}

if (-not (Test-Path $pacman)) {
    throw "Matrix3 MSYS2 package manager was not found at $pacman. Run PREP + BUILD TP LINK again if the verified MSYS2 toolchain was removed."
}

Write-Host 'Installing TP Link visual dependencies from MSYS2 UCRT64 binary packages...' -ForegroundColor Cyan
$pacmanArgs = @('-S', '--needed', '--noconfirm') + $visualPackages
$pacmanProcess = Start-Process -FilePath $pacman -ArgumentList $pacmanArgs -Wait -PassThru -NoNewWindow
if ($pacmanProcess.ExitCode -ne 0) {
    throw "MSYS2 pacman exited with code $($pacmanProcess.ExitCode) while installing TP Link visual dependencies."
}

if (-not (Test-VisualDependencies -PythonPath $msys2Python)) {
    throw 'MSYS2 UCRT64 NumPy/Pillow packages installed, but the verified Matrix3 Python still cannot import both dependencies.'
}

Write-Host 'TP Link MSYS2 visual Python environment is ready.' -ForegroundColor Green
exit 0
