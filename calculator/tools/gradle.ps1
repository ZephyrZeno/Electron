# Local Windows workaround: some JDK/Windows combinations advertise Unix sockets
# but reject connections. An unavailable socket directory makes JDK selectors
# fall back to TCP. Only this invocation and its child processes are affected.
param([Parameter(ValueFromRemainingArguments = $true)][string[]]$GradleArguments = @('build'))
$ErrorActionPreference = 'Stop'
$electronPreviousJavaOptions = $env:JAVA_TOOL_OPTIONS
$electronUnavailableSocketPath = Join-Path $env:TEMP ('electron-socket-fallback-' + [guid]::NewGuid().ToString('N'))
try {
    $env:JAVA_TOOL_OPTIONS = ($electronPreviousJavaOptions + ' "-Djdk.net.unixdomain.tmpdir=' + $electronUnavailableSocketPath + '"').Trim()
    Push-Location (Split-Path -Parent $PSScriptRoot)
    try {
        & .\gradlew.bat @GradleArguments
        $electronExitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
} finally {
    $env:JAVA_TOOL_OPTIONS = $electronPreviousJavaOptions
}
exit $electronExitCode
