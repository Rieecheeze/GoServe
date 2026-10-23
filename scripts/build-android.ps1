param(
    [string[]]$Tasks = @(':app:testDebugUnitTest', ':app:assembleDebug', ':app:lintDebug'),
    [string]$JavaHome,
    [string]$GradleHome
)

$ErrorActionPreference = 'Stop'
$goServeRoot = Split-Path -Parent $PSScriptRoot
$goServeTools = Join-Path $goServeRoot '.tools'

function Test-GoServeJdk([string]$Candidate) {
    if (!$Candidate -or !(Test-Path -LiteralPath (Join-Path $Candidate 'bin/java.exe'))) { return $false }
    if (!(Test-Path -LiteralPath (Join-Path $Candidate 'bin/javac.exe'))) { return $false }
    $releaseFile = Join-Path $Candidate 'release'
    if (!(Test-Path -LiteralPath $releaseFile)) { return $false }
    $versionLine = Get-Content -LiteralPath $releaseFile | Select-String '^JAVA_VERSION="(\d+)'
    if (!$versionLine) { return $false }
    $major = [int]$versionLine.Matches[0].Groups[1].Value
    return $major -ge 17 -and $major -le 22
}

if ($JavaHome -and !(Test-GoServeJdk $JavaHome)) {
    throw "JavaHome '$JavaHome' must contain an installed JDK 17-22 compatible with Gradle 8.9."
}
if (!$JavaHome) {
    $jdkCandidates = @($env:JAVA_HOME)
    foreach ($vendor in @('Eclipse Adoptium', 'Amazon Corretto', 'Microsoft', 'Java')) {
        $vendorDirectory = Join-Path $env:ProgramFiles $vendor
        if (Test-Path -LiteralPath $vendorDirectory) {
            $jdkCandidates += Get-ChildItem -LiteralPath $vendorDirectory -Directory |
                Sort-Object Name -Descending | Select-Object -ExpandProperty FullName
        }
    }
    $JavaHome = $jdkCandidates | Where-Object { Test-GoServeJdk $_ } | Select-Object -First 1
}
if (!$JavaHome) { throw 'No compatible installed JDK found. Pass -JavaHome with a JDK 17-22 directory; this script does not install tools.' }
if (!$GradleHome) {
    $GradleHome = Join-Path $goServeTools 'gradle-8.9'
    if (!(Test-Path -LiteralPath (Join-Path $GradleHome 'bin/gradle.bat'))) {
        $wrapperCache = Join-Path $goServeTools 'gradle-cache/wrapper/dists/gradle-8.9-bin'
        if (Test-Path -LiteralPath $wrapperCache) {
            $GradleHome = Get-ChildItem -LiteralPath $wrapperCache -Directory | ForEach-Object {
                $distribution = Join-Path $_.FullName 'gradle-8.9'
                if (Test-Path -LiteralPath (Join-Path $distribution 'bin/gradle.bat')) { $distribution }
            } | Select-Object -First 1
        }
    }
}
if (!$GradleHome) { throw 'Gradle 8.9 is not cached. Follow android/README.md build setup or pass -GradleHome with an existing installation.' }
$gradleLauncher = Join-Path $GradleHome 'bin/gradle.bat'
if (!(Test-Path -LiteralPath $gradleLauncher)) {
    throw "Gradle launcher missing at '$gradleLauncher'. Follow android/README.md build setup or pass -GradleHome with an installed Gradle 8.9 directory."
}
$androidProject = Join-Path $goServeRoot 'android'
if (!(Test-Path -LiteralPath (Join-Path $androidProject 'local.properties')) -and !$env:ANDROID_HOME -and !$env:ANDROID_SDK_ROOT) {
    throw 'Android SDK is not configured. Set ANDROID_HOME or android/local.properties; existing local settings are preserved.'
}
$socketDirectory = (Join-Path $goServeTools 'tmp').Replace('\', '/')
New-Item -ItemType Directory -Force -Path $socketDirectory | Out-Null
$savedEnvironment = @{}
foreach ($name in @('JAVA_HOME', 'GRADLE_USER_HOME', 'JAVA_TOOL_OPTIONS')) {
    $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
$buildExitCode = 1
try {
    $env:JAVA_HOME = $JavaHome
    $env:GRADLE_USER_HOME = Join-Path $goServeTools 'gradle-cache'
    $env:JAVA_TOOL_OPTIONS = "$($savedEnvironment['JAVA_TOOL_OPTIONS']) `"-Djdk.net.unixdomain.tmpdir=$socketDirectory`"".Trim()
    $gradleArguments = @('-p', $androidProject, '--offline', '--no-daemon', '--no-parallel', '--max-workers=1', '--console=plain', '--stacktrace')
    Write-Host "Building Android with JDK '$JavaHome' and Gradle '$GradleHome'."
    $ErrorActionPreference = 'Continue'
    & $gradleLauncher @gradleArguments @Tasks
    $buildExitCode = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($buildExitCode -ne 0) {
        Write-Warning "Android build failed (exit $buildExitCode). Review the Gradle error above and android/app/build/reports. Offline dependencies must already exist in .tools/gradle-cache."
    }
} finally {
    foreach ($name in $savedEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process')
    }
}
exit $buildExitCode
