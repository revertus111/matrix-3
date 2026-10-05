Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

[System.Windows.Forms.Application]::EnableVisualStyles()

$scriptRoot = $PSScriptRoot
$marioBuilder = Join-Path $scriptRoot 'build-mario.cmd'
$ootBuilder = Join-Path $scriptRoot 'build-oot.cmd'
$tpBuilder = Join-Path $scriptRoot 'build-tp.cmd'

$form = New-Object System.Windows.Forms.Form
$form.Text = 'Matrix3 Native Builder'
$form.StartPosition = 'CenterScreen'
$form.ClientSize = New-Object System.Drawing.Size(630, 255)
$form.FormBorderStyle = 'FixedDialog'
$form.MaximizeBox = $false
$form.MinimizeBox = $true

$title = New-Object System.Windows.Forms.Label
$title.Text = 'Matrix3 Native Builder'
$title.Font = New-Object System.Drawing.Font('Segoe UI', 16, [System.Drawing.FontStyle]::Bold)
$title.AutoSize = $true
$title.Location = New-Object System.Drawing.Point(22, 18)
$form.Controls.Add($title)

$subtitle = New-Object System.Windows.Forms.Label
$subtitle.Text = 'No cd commands. No guessing which bridge or donor folder you are in.'
$subtitle.AutoSize = $true
$subtitle.Location = New-Object System.Drawing.Point(25, 55)
$form.Controls.Add($subtitle)

$marioButton = New-Object System.Windows.Forms.Button
$marioButton.Text = 'BUILD + TEST MARIO'
$marioButton.Font = New-Object System.Drawing.Font('Segoe UI', 11, [System.Drawing.FontStyle]::Bold)
$marioButton.Size = New-Object System.Drawing.Size(180, 58)
$marioButton.Location = New-Object System.Drawing.Point(25, 91)
$form.Controls.Add($marioButton)

$ootButton = New-Object System.Windows.Forms.Button
$ootButton.Text = 'BUILD OOT'
$ootButton.Font = New-Object System.Drawing.Font('Segoe UI', 11, [System.Drawing.FontStyle]::Bold)
$ootButton.Size = New-Object System.Drawing.Size(180, 58)
$ootButton.Location = New-Object System.Drawing.Point(225, 91)
$form.Controls.Add($ootButton)

$tpButton = New-Object System.Windows.Forms.Button
$tpButton.Text = 'PREP + BUILD TP LINK'
$tpButton.Font = New-Object System.Drawing.Font('Segoe UI', 10, [System.Drawing.FontStyle]::Bold)
$tpButton.Size = New-Object System.Drawing.Size(180, 58)
$tpButton.Location = New-Object System.Drawing.Point(425, 91)
$form.Controls.Add($tpButton)

$status = New-Object System.Windows.Forms.Label
$status.Text = 'Pick a build. A build window will stay open with SUCCESS or FAILED.'
$status.AutoSize = $false
$status.Size = New-Object System.Drawing.Size(580, 42)
$status.Location = New-Object System.Drawing.Point(25, 165)
$form.Controls.Add($status)

$closeButton = New-Object System.Windows.Forms.Button
$closeButton.Text = 'Close'
$closeButton.Size = New-Object System.Drawing.Size(90, 30)
$closeButton.Location = New-Object System.Drawing.Point(515, 211)
$form.Controls.Add($closeButton)

function Start-Builder([string]$Path, [string]$Label) {
    if (-not (Test-Path $Path)) {
        [System.Windows.Forms.MessageBox]::Show(
            "Missing builder script:`r`n$Path",
            'Matrix3 Native Builder',
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Error
        ) | Out-Null
        return
    }

    try {
        Start-Process -FilePath $Path | Out-Null
        $status.Text = "$Label build started. Use the build window; you do not need to type anything."
    }
    catch {
        [System.Windows.Forms.MessageBox]::Show(
            $_.Exception.Message,
            'Matrix3 Native Builder',
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Error
        ) | Out-Null
    }
}

$marioButton.Add_Click({ Start-Builder $marioBuilder 'Mario' })
$ootButton.Add_Click({ Start-Builder $ootBuilder 'OoT' })
$tpButton.Add_Click({ Start-Builder $tpBuilder 'TP Link' })
$closeButton.Add_Click({ $form.Close() })

[void]$form.ShowDialog()
