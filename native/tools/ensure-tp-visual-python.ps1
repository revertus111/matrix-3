$ErrorActionPreference = 'Stop'

$toolRoot = Join-Path $env:LOCALAPPDATA 'Matrix3\TPLinkTools'
$pythonVersion = '3.12.10'
$pythonRoot = Join-Path $toolRoot 'python312'
$pythonExe = Join-Path $pythonRoot 'python.exe'
$venvDir = Join-Path $toolRoot 'venv'
$venvPython = Join-Path $venvDir 'Scripts\python.exe'
$downloadDir = Join-Path $toolRoot 'downloads'
$installer = Join-Path $downloadDir "python-$pythonVersion-amd64.exe"
$pythonUrl = "https://www.python.org/ftp/python/$pythonVersion/python-$pythonVersion-amd64.exe"

function Test-NativePython {
    param([Parameter(Mandatory = $true)][string]$PythonPath)

    if (-not (Test-Path $PythonPath)) {
        return $false
    }

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'SilentlyContinue'
        & $PythonPath -c 'import sys, sysconfig; ok = (3, 12) <= sys.version_info[:2] < (3, 14) and sysconfig.get_platform().lower().startswith("win-"); raise SystemExit(0 if ok else 1)' *> $null
        return ($LASTEXITCODE -eq 0)
    }
    catch {
        return $false
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
}

function Test-VisualDependencies {
    param([Parameter(Mandatory = $true)][string]$PythonPath)

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

New-Item -ItemType Directory -Path $toolRoot -Force | Out-Null

if ((Test-NativePython -PythonPath $venvPython) -and (Test-VisualDependencies -PythonPath $venvPython)) {
    Write-Host 'TP Link private visual Python environment is ready.' -ForegroundColor DarkGray
    exit 0
}

if (Test-Path $venvDir) {
    Write-Host 'Replacing incomplete/incompatible TP Link visual-proof venv...' -ForegroundColor Yellow
    Remove-Item -LiteralPath $venvDir -Recurse -Force
}

if (-not (Test-NativePython -PythonPath $pythonExe)) {
    if (Test-Path $pythonRoot) {
        Remove-Item -LiteralPath $pythonRoot -Recurse -Force
    }

    New-Item -ItemType Directory -Path $downloadDir -Force | Out-Null

    if (-not (Test-Path $installer)) {
        Write-Host "Downloading private CPython $pythonVersion for TP Link visual proof..." -ForegroundColor Cyan
        Invoke-WebRequest -UseBasicParsing -Uri $pythonUrl -OutFile $installer
    }

    $signature = Get-AuthenticodeSignature -FilePath $installer
    if ($signature.Status -ne 'Valid') {
        throw "Downloaded Python installer signature is not valid: $($signature.Status)"
    }

    Write-Host "Installing private CPython $pythonVersion under TPLinkTools..." -ForegroundColor Cyan
    $installerArgs = @(
        '/quiet',
        'InstallAllUsers=0',
        "TargetDir=`"$pythonRoot`"",
        'Include_launcher=0',
        'Include_pip=1',
        'Include_test=0',
        'Include_doc=0',
        'Include_tcltk=0',
        'PrependPath=0',
        'Shortcuts=0'
    )
    $installerProcess = Start-Process -FilePath $installer -ArgumentList $installerArgs -Wait -PassThru
    $installerCode = $installerProcess.ExitCode
    if ($installerCode -ne 0 -and $installerCode -ne 3010) {
        throw "Private CPython installer exited with code $installerCode."
    }
    if ($installerCode -eq 3010) {
        Write-Host 'Private CPython installer requested a restart, but setup will continue only if the interpreter is immediately usable.' -ForegroundColor Yellow
    }
}

if (-not (Test-NativePython -PythonPath $pythonExe)) {
    throw "Private Windows CPython was not usable after setup: $pythonExe"
}

Write-Host 'Creating isolated TP Link visual-proof Python environment...' -ForegroundColor Cyan
& $pythonExe -m venv $venvDir
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $venvPython)) {
    throw 'Private CPython could not create the TP Link visual-proof venv.'
}

Write-Host 'Installing isolated visual-proof dependencies (numpy + Pillow)...' -ForegroundColor Cyan
& $venvPython -m pip install --disable-pip-version-check 'numpy>=1.26,<3' 'Pillow>=10,<13'
if ($LASTEXITCODE -ne 0) {
    throw "Visual-proof dependency installation exited with code $LASTEXITCODE."
}

if (-not (Test-VisualDependencies -PythonPath $venvPython)) {
    throw 'TP Link private visual-proof venv exists, but NumPy/Pillow still cannot be imported.'
}

Write-Host 'TP Link private visual Python environment is ready.' -ForegroundColor Green
exit 0
