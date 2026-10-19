@echo off
setlocal
set "PRICE_WATCHER_DEPLOY_FILE=%~f0"
set "PRICE_WATCHER_DEPLOY_PS1=%TEMP%\price-watcher-launch-%RANDOM%-%RANDOM%.ps1"
rem Extract the embedded PowerShell payload so -File preserves quoted arguments.
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $code=([IO.File]::ReadAllText($env:PRICE_WATCHER_DEPLOY_FILE) -split '(?m)^# POWERSHELL_PAYLOAD\r?\n',2)[1]; if (-not $code) { throw 'Missing deployment payload.' }; $stream=[IO.File]::Open($env:PRICE_WATCHER_DEPLOY_PS1,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None); try { $bytes=[Text.Encoding]::UTF8.GetBytes($code); $stream.Write($bytes,0,$bytes.Length) } finally { $stream.Dispose() }"
if errorlevel 1 exit /b 1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%PRICE_WATCHER_DEPLOY_PS1%" %*
set "DEPLOY_EXIT=%ERRORLEVEL%"
del /q "%PRICE_WATCHER_DEPLOY_PS1%"
echo.
if not "%DEPLOY_EXIT%"=="0" echo Deployment failed. See the details above.
pause
exit /b %DEPLOY_EXIT%
# POWERSHELL_PAYLOAD
param(
    [switch]$CheckOnly,
    [string]$ConnectionFile = (Join-Path $env:USERPROFILE 'Desktop\vpsCloudcore.cmd'),
    [string]$PlinkPath = 'C:\Program Files (x86)\pink\plink.exe'
)

$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent (Split-Path -Parent $env:PRICE_WATCHER_DEPLOY_FILE)
$serverScript = @'
#!/usr/bin/env bash
set -Eeuo pipefail

archive=${1:?Archive is required}
checksum=${2:?SHA256 is required}
mode=${3:-deploy}
expected_version=${4:?Backend version is required}
service=price-watcher
parent=/home/watcher/app
target=$parent/price-watcher
upload_dir=$parent/.deploy-uploads

[[ $EUID -eq 0 ]] || { echo 'Run via SSH as root.' >&2; exit 1; }
[[ $mode == check || $mode == deploy ]] || { echo 'Invalid mode.' >&2; exit 1; }
[[ $archive =~ ^/home/watcher/app/\.deploy-uploads/[a-f0-9]{32}\.tar\.gz$ ]] || exit 1
[[ $checksum =~ ^[a-f0-9]{64}$ ]] || exit 1
[[ $expected_version =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || exit 1
for tool in tar curl sha256sum flock java realpath find rm; do command -v "$tool" >/dev/null; done
exec 9>"$parent/.price-watcher-deploy.lock"
flock -n 9 || { echo 'Another deployment is already running.' >&2; exit 1; }
printf '%s  %s\n' "$checksum" "$archive" | sha256sum --check --status
[[ -d $target && ! -L $target ]] || { echo 'Unexpected application directory.' >&2; exit 1; }
[[ $(systemctl show "$service" -p User --value) == watcher ]] || exit 1
[[ $(systemctl show "$service" -p WorkingDirectory --value) == "$target" ]] || exit 1
systemctl show "$service" -p ExecStart --value | grep -Fq "$target/bin/price-watcher" || exit 1
if find "$target" -mindepth 1 -maxdepth 1 ! -name bin ! -name lib -print -quit | grep -q .; then
    echo 'Unexpected data in the application directory. Separate persistent data before deployment.' >&2
    exit 1
fi
while IFS= read -r entry; do
    [[ $entry == price-watcher || $entry == price-watcher/ || $entry == price-watcher/* ]] || exit 1
    [[ $entry != /* && /$entry/ != */../* ]] || exit 1
done < <(tar -tzf "$archive")

upload_id=${archive##*/}
upload_id=${upload_id%.tar.gz}
find "$upload_dir" -mindepth 1 -maxdepth 1 -type f ! -name "$upload_id.*" ! -name "price-response-${checksum:0:8}.json" -delete

echo 'Preflight OK: checksum, service, paths and archive.'
if [[ $mode == check ]]; then
    echo 'Check-only mode: the running service was not changed.'
    exit 0
fi

release_id=$(date -u +%Y%m%dT%H%M%SZ)-${checksum:0:8}
backup=$parent/price-watcher-backup-$release_id
failed=$parent/price-watcher-failed-$release_id
[[ ! -e $backup && ! -e $failed ]] || { echo 'Release directory already exists.' >&2; exit 1; }
stage=$(mktemp -d "$parent/.price-watcher-stage.XXXXXX")
stopped=0
old_moved=0
new_installed=0

rollback() {
    local result=$?
    trap - EXIT
    if (( result != 0 && stopped )); then
        echo 'Deployment failed; restoring the previous release.' >&2
        systemctl stop "$service" || true
        if (( new_installed )); then mv -- "$target" "$failed"; fi
        if (( old_moved )); then mv -- "$backup" "$target"; fi
        if systemctl start "$service"; then
            echo 'Previous release restarted.' >&2
        else
            echo 'ROLLBACK START FAILED: inspect the systemd service.' >&2
        fi
        journalctl -u "$service" -n 15 --no-pager >&2 || true
    fi
    exit "$result"
}
trap rollback EXIT

tar -xzf "$archive" --no-same-owner -C "$stage"
[[ -f $stage/price-watcher/bin/price-watcher && -d $stage/price-watcher/lib ]] || exit 1
chown -R watcher:watcher "$stage/price-watcher"
chmod +x "$stage/price-watcher/bin/price-watcher"

echo 'Stopping price-watcher and saving the previous release...'
stopped=1
systemctl stop "$service"
mv -- "$target" "$backup"
old_moved=1
mv -- "$stage/price-watcher" "$target"
new_installed=1
rmdir -- "$stage"
systemctl start "$service"

ready=0
for attempt in {1..30}; do
    if systemctl is-active --quiet "$service"; then
        code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 2 http://127.0.0.1:8080/products/price || true)
        if [[ $code == 405 ]]; then ready=1; break; fi
    fi
    sleep 1
done
(( ready )) || { echo 'HTTP readiness check failed.' >&2; exit 1; }

echo 'Verifying the running backend version...'
running_version=$(curl -fsS --max-time 5 http://127.0.0.1:8080/version)
[[ $running_version == *"\"version\":\"$expected_version\""* ]] || {
    echo "Version verification failed: expected $expected_version, received $running_version" >&2
    exit 1
}
echo "Version verified: $running_version"
echo 'Checking backend health (database and browser)...'
health=$(curl -sS --max-time 40 -w ' HTTP %{http_code}' http://127.0.0.1:8080/health || true)
[[ $health == *'"status":"ok"'*' HTTP 200' ]] || { echo "Health check failed: $health" >&2; exit 1; }
echo "Health OK: ${health% HTTP 200}"

echo 'Checking a real Ozon price request (warning only)...'
response_file=$upload_dir/price-response-${checksum:0:8}.json
price_ok=0
for attempt in 1 2 3; do
    code=$(curl -sS --max-time 150 -o "$response_file" -w '%{http_code}' \
        -H 'Content-Type: application/json' \
        --data '{"url":"https://www.ozon.ru/product/ryukzak-5677073262/"}' \
        http://127.0.0.1:8080/products/price || true)
    if [[ $code == 200 ]] && grep -Eq '"amount"[[:space:]]*:' "$response_file"; then
        price_ok=1
        break
    fi
    echo "Price check attempt $attempt failed: HTTP $code" >&2
    (( attempt < 3 )) && sleep 15
done
if (( price_ok )); then
    echo "Price check OK: $(cat "$response_file")"
else
    echo 'WARNING: the backend is healthy, but the Ozon price request failed. Keeping the new release; check Ozon via VNC.' >&2
fi
systemctl is-active --quiet "$service"
systemctl show "$service" -p ActiveState -p MainPID
# The release is committed: cleanup failures must not trigger rollback.
trap - EXIT
echo 'Deployment successful. Removing uploaded files and previous release backups...'
rm -f -- "$upload_dir/$upload_id".* "$response_file"
parent_path=$(realpath -e -- "$parent")
while IFS= read -r -d '' old_backup; do
    backup_name=${old_backup##*/}
    if [[ ! $backup_name =~ ^price-watcher-(backup|failed)-[0-9]{8}(-[0-9]{6}|T[0-9]{6}Z-[a-f0-9]{8})$ ]]; then
        echo "Skipping unrecognized backup directory: $old_backup" >&2
        continue
    fi
    backup_path=$(realpath -e -- "$old_backup") || continue
    if [[ -L $old_backup || ${backup_path%/*} != "$parent_path" || $backup_path == "$target" ]]; then
        echo "Skipping unsafe backup path: $old_backup" >&2
        continue
    fi
    if find "$backup_path" -mindepth 1 -maxdepth 1 ! -name bin ! -name lib -print -quit | grep -q .; then
        echo "Skipping backup containing unexpected data: $backup_path" >&2
        continue
    fi
    if rm -rf --one-file-system -- "$backup_path"; then
        echo "Removed backup: $backup_name"
    else
        echo "Warning: could not completely remove backup: $backup_path" >&2
    fi
done < <(find "$parent" -mindepth 1 -maxdepth 1 -type d \( -name 'price-watcher-backup-*' -o -name 'price-watcher-failed-*' \) -print0)
'@

$secretDirectory = $null
$exitCode = 1

function Read-ConnectionLiteral([string]$text, [string]$name) {
    $pattern = '(?m)^\$' + [regex]::Escape($name) + '\s*=\s*''((?:[^'']|'''')*)'''
    $match = [regex]::Match($text, $pattern)
    if (-not $match.Success) { throw "Missing $name in the local connection file." }
    return $match.Groups[1].Value.Replace("''", "'")
}

# A UTF-8 console makes .NET prefix redirected stdin with a BOM, which breaks the remote shell script.
[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)

function Invoke-Remote([string]$command, [string]$inputFile = '') {
    $commandFile = Join-Path $secretDirectory 'remote-command.txt'
    [System.IO.File]::WriteAllText($commandFile, $command, [System.Text.UTF8Encoding]::new($false))
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $PlinkPath
    $startInfo.Arguments = '-ssh -batch -noagent -hostkey "' + $hostKey + '" -pwfile "' + $passwordFile + '" -m "' + $commandFile + '" root@' + $server
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    try {
        [void]$process.Start()
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if ($inputFile) {
            $stream = [System.IO.File]::OpenRead($inputFile)
            try {
                $copyTask = $stream.CopyToAsync($process.StandardInput.BaseStream)
                $lastReport = [DateTime]::MinValue
                while (-not $copyTask.IsCompleted) {
                    if (([DateTime]::UtcNow - $lastReport).TotalSeconds -ge 5) {
                        $percent = if ($stream.Length) { [Math]::Min(99, [Math]::Floor(100 * $stream.Position / $stream.Length)) } else { 0 }
                        Write-Host ('  Upload: {0}% ({1:N1}/{2:N1} MiB)' -f $percent, ($stream.Position / 1MB), ($stream.Length / 1MB))
                        $lastReport = [DateTime]::UtcNow
                    }
                    Start-Sleep -Milliseconds 250
                }
                $copyTask.GetAwaiter().GetResult()
                Write-Host ('  Upload: 100% ({0:N1} MiB)' -f ($stream.Length / 1MB))
            } finally { $stream.Dispose() }
        }
        $process.StandardInput.Close()
        while (-not $process.WaitForExit(10000)) {
            Write-Host '  Waiting for the server; the SSH command is still running...'
        }
        $outputText = $stdout.GetAwaiter().GetResult()
        $errorText = $stderr.GetAwaiter().GetResult()
        if ($outputText) { Write-Host $outputText.TrimEnd() }
        if ($errorText) { Write-Host $errorText.TrimEnd() }
        if ($process.ExitCode -ne 0) { throw "Remote command failed with exit code $($process.ExitCode)." }
    } finally { $process.Dispose() }
}

Push-Location $projectDirectory
try {
    if (-not (Test-Path -LiteralPath $PlinkPath)) { throw "Plink not found: $PlinkPath" }
    $signature = Get-AuthenticodeSignature -LiteralPath $PlinkPath
    if ($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notmatch 'Simon Tatham') {
        throw 'Plink signature validation failed.'
    }
    $connection = [System.IO.File]::ReadAllText($ConnectionFile)
    $server = Read-ConnectionLiteral $connection 'Server'
    $hostKey = Read-ConnectionLiteral $connection 'HostKey'
    $sshPassword = Read-ConnectionLiteral $connection 'SshPassword'
    $connection = $null
    if ($server -notmatch '^[a-zA-Z0-9][a-zA-Z0-9.-]*$') { throw 'Invalid SSH host.' }
    if ($hostKey -notmatch '^SHA256:[a-zA-Z0-9+/=]+$') { throw 'Invalid SSH host key.' }

    $deploymentId = [guid]::NewGuid().ToString('N')
    $versionMatch = [regex]::Match([System.IO.File]::ReadAllText((Join-Path $projectDirectory 'gradle.properties')), '(?m)^backendVersion=([0-9]+\.[0-9]+\.[0-9]+)\r?$')
    if (-not $versionMatch.Success) { throw 'Missing or invalid backendVersion in gradle.properties.' }
    $expectedVersion = $versionMatch.Groups[1].Value
    Write-Host "Backend version: $expectedVersion"
    Write-Host '[1/4] Building the distribution...'
    & .\gradlew.bat installDist --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed.' }
    $archiveDirectory = Join-Path $projectDirectory 'build\deploy'
    [System.IO.Directory]::CreateDirectory($archiveDirectory) | Out-Null
    $archive = Join-Path $archiveDirectory ('price-watcher-' + $deploymentId + '.tar.gz')
    & tar.exe -czf $archive -C (Join-Path $projectDirectory 'build\install') price-watcher
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create the deployment archive.' }
    $checksum = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()

    $secretDirectory = Join-Path $env:TEMP ('price-watcher-deploy-' + $deploymentId)
    [System.IO.Directory]::CreateDirectory($secretDirectory) | Out-Null
    $acl = Get-Acl -LiteralPath $secretDirectory
    $acl.SetAccessRuleProtection($true, $false)
    $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl.AddAccessRule([System.Security.AccessControl.FileSystemAccessRule]::new($identity, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow'))
    Set-Acl -LiteralPath $secretDirectory -AclObject $acl
    $passwordFile = Join-Path $secretDirectory 'ssh-password.txt'
    [System.IO.File]::WriteAllText($passwordFile, $sshPassword, [System.Text.UTF8Encoding]::new($false))
    $sshPassword = $null

    Write-Host "[2/4] Uploading to $server..."
    $remoteDirectory = '/home/watcher/app/.deploy-uploads'
    $remoteArchive = "$remoteDirectory/$deploymentId.tar.gz"
    $remoteScript = "$remoteDirectory/$deploymentId.sh"
    Invoke-Remote "umask 077; mkdir -p '$remoteDirectory' && cat > '$remoteArchive'" $archive
    # Force LF even if Git checks out the shell script with CRLF on Windows.
    $shellFile = Join-Path $secretDirectory 'deploy-vps.sh'
    $shellText = $serverScript.Replace("`r`n", "`n").TrimEnd([char[]]"`r`n")
    [System.IO.File]::WriteAllText($shellFile, $shellText, [System.Text.UTF8Encoding]::new($false))

    Write-Host '[3/4] Checking the server and switching the release...'
    $mode = if ($CheckOnly) { 'check' } else { 'deploy' }
    $installCommand = "umask 077; cat > '$remoteScript' && bash -n '$remoteScript' && " +
        "bash '$remoteScript' '$remoteArchive' '$checksum' '$mode' '$expectedVersion'"
    Invoke-Remote $installCommand $shellFile
    Write-Host '[4/4] Finished.'
    $exitCode = 0
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
} finally {
    $sshPassword = $null
    if ($secretDirectory) {
        foreach ($name in @('ssh-password.txt', 'remote-command.txt', 'deploy-vps.sh')) {
            $file = Join-Path $secretDirectory $name
            if ([System.IO.File]::Exists($file)) { [System.IO.File]::Delete($file) }
        }
        if ([System.IO.Directory]::Exists($secretDirectory)) { [System.IO.Directory]::Delete($secretDirectory) }
    }
    Pop-Location
}
exit $exitCode