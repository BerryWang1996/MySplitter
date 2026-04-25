Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$mavenWrapper = Join-Path $repoRoot "mvnw.cmd"

$commands = @(
    @{
        Description = "Validate core module"
        Arguments = @("-q", "-pl", "mysplitter", "verify")
    },
    @{
        Description = "Validate starter module"
        Arguments = @("-q", "-pl", "mysplitter-spring-boot-starter", "-am", "test")
    },
    @{
        Description = "Run regression suite"
        Arguments = @("-q", "-pl", "mysplitter-tests", "-am", "test")
    },
    @{
        Description = "Compile demo consumer"
        Arguments = @("-q", "-pl", "demo", "-am", "-Dmaven.test.skip=true", "clean", "compile")
    },
    @{
        Description = "Package release artifacts"
        Arguments = @("-q", "-pl", "mysplitter,mysplitter-spring-boot-starter", "-am", "-Dmaven.test.skip=true", "package")
    }
)

foreach ($command in $commands) {
    Write-Host ("==> " + $command.Description)
    & $mavenWrapper @($command.Arguments)
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

