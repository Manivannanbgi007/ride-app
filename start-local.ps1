<#
.SYNOPSIS
    Runs the platform on this machine for manual testing: the infrastructure in Docker, the Spring Boot services
    as local processes, and the Front-App web application (Vite dev server), each in its own tab of one Windows
    Terminal window (its log is in its tab; Ctrl+C or closing the tab stops it). A last tab shows every service's
    health.

    Next to another project: -PortOffset moves every port the platform opens on this machine (services, web app and
    infrastructure), e.g. 10000: the gateway on 18080, Kafka on 19092. Nothing starts while one of the platform's
    ports is held by another program: the script names the port and the program instead.

.EXAMPLE
    .\start-local.ps1                     # start everything (builds a service's jar first if it has none)
    .\start-local.ps1 -Build              # rebuild every service's jar (and reinstall Front-App's packages) first
    .\start-local.ps1 -PortOffset 10000   # every port + 10000 (or FD_PORT_OFFSET=10000 in .env)
    .\start-local.ps1 -NoAi               # without the AI assistant (Ollama, ai-mcp-server, ai-mcp-client)
    .\start-local.ps1 -Only user-service,api-gateway   # some services only (less memory)
    .\start-local.ps1 -Detached           # without windows: the services one after the other until UP, then
                                          # Front-App; their logs in logs\<name>.log (also for the e2e tests)
    .\start-local.ps1 -Stop               # stop what -Detached started
    .\start-local.ps1 -Status             # only show the services' health, refreshed every 10 s
#>
param(
    [switch]$Build, [switch]$Status, [switch]$Detached, [switch]$Stop, [switch]$NoAi,
    # Start these services only (and the infrastructure they need)
    [string[]]$Only,
    # How far every port moves; default: FD_PORT_OFFSET in .env, else 0
    [int]$PortOffset = -1,
    # Enough for local testing, and all the services fit in a laptop's memory
    [string]$JvmOptions = "-Xms128m -Xmx512m"
)
# Native commands (docker, mvnw) write progress to stderr: exit codes are checked instead
$ErrorActionPreference = "Continue"
$root = $PSScriptRoot
$logs = "$root\logs"
$pidFile = "$logs\detached-services.txt"

if ($Stop) {
    if (-not (Test-Path $pidFile)) {
        Write-Host "No service started with -Detached is running."
        return
    }
    foreach ($line in Get-Content $pidFile) {
        $service, $processId = $line -split " "
        # The service's cmd and its java process (/T): only the processes this script started
        taskkill /PID $processId /T /F 2>$null | Out-Null
        Write-Host "   stopped $service"
    }
    Remove-Item $pidFile
    return
}

# A setting of .env (a number), or $null: only the port settings are read
function Get-DotEnvNumber([string]$name) {
    if (-not (Test-Path "$root\.env")) { return $null }
    $match = Select-String -Path "$root\.env" -Pattern "^\s*$name\s*=\s*(\d+)\s*$" | Select-Object -First 1
    if ($match) { [int]$match.Matches[0].Groups[1].Value } else { $null }
}

if ($PortOffset -lt 0) {
    $fromEnv = Get-DotEnvNumber "FD_PORT_OFFSET"
    $PortOffset = if ($null -ne $fromEnv) { $fromEnv } else { 0 }
}

# In start order; the API Gateway last
$services = [ordered]@{
    "user-service" = 8081; "restaurant-service" = 8082; "catalog-service" = 8083; "promotion-service" = 8089; "review-service" = 8094; "chat-service" = 8096
    "order-service" = 8084; "payment-service" = 8085; "notification-service" = 8086
    "delivery-partner-service" = 8099; "delivery-service" = 8087
    "search-service" = 8088; "admin-service" = 8092; "ai-mcp-server" = 8098; "ai-mcp-client" = 8097; "api-gateway" = 8080
}
foreach ($service in @($services.Keys)) { $services[$service] += $PortOffset }
# "-Only a,b" arrives as one text through powershell -File (the status tab)
$Only = @($Only | ForEach-Object { $_ -split "," } | Where-Object { $_ })
# Every service's address, also of the ones not started: no call may go to another program on a default port
$ports = [ordered]@{}
foreach ($service in $services.Keys) { $ports[$service] = $services[$service] }
if ($Only) {
    $unknown = @($Only | Where-Object { -not $services.Contains($_) })
    if ($unknown) { throw "Unknown service: $($unknown -join ', ')" }
    foreach ($service in @($services.Keys)) { if ($Only -notcontains $service) { $services.Remove($service) } }
}
if ($NoAi) {
    $services.Remove("ai-mcp-server")
    $services.Remove("ai-mcp-client")
}
$withAi = $services.Contains("ai-mcp-server") -or $services.Contains("ai-mcp-client")
$withSearch = $services.Contains("search-service")
$frontAppPort = 5180 + $PortOffset

# The infrastructure's ports on this machine; docker-compose.yml reads the same variables. A port set in .env is used
# as it is, the others move by the offset
$infrastructure = [ordered]@{
    MYSQL_PORT = 3330; KAFKA_HOST_PORT = 9092; SCHEMA_REGISTRY_HOST_PORT = 8091; REDIS_HOST_PORT = 6379
    ELASTICSEARCH_HOST_PORT = 9200; MAILPIT_HOST_PORT = 8025; MAILPIT_SMTP_HOST_PORT = 1025; PHPMYADMIN_HOST_PORT = 8095
}
if ($withAi) { $infrastructure["OLLAMA_HOST_PORT"] = 11434 }
if (-not $withSearch) { $infrastructure.Remove("ELASTICSEARCH_HOST_PORT") }
foreach ($name in @($infrastructure.Keys)) {
    $fromEnv = Get-DotEnvNumber $name
    $infrastructure[$name] = if ($null -ne $fromEnv) { $fromEnv } else { $infrastructure[$name] + $PortOffset }
}
$gatewayUrl = "http://localhost:$($ports['api-gateway'])"
$mailpitUrl = "http://localhost:$($infrastructure['MAILPIT_HOST_PORT'])"

function Show-Health {
    while ($true) {
        $lines = foreach ($service in $services.Keys) {
            $port = $services[$service]
            $state = try { (Invoke-RestMethod "http://localhost:$port/actuator/health" -TimeoutSec 2).status } catch { "starting or down" }
            "  {0,-24} http://localhost:{1,-5} {2}" -f $service, $port, $state
        }
        Clear-Host
        "Food Delivery - services on this machine ($(Get-Date -Format HH:mm:ss), refreshed every 10 s)"
        ""
        $lines
        ""
        "  API Gateway   $gatewayUrl      Mailpit    $mailpitUrl      Redis    localhost:$($infrastructure['REDIS_HOST_PORT'])"
        "  phpMyAdmin    http://localhost:$($infrastructure['PHPMYADMIN_HOST_PORT'])" + $(if ($withSearch) { "     Elasticsearch http://localhost:$($infrastructure['ELASTICSEARCH_HOST_PORT'])" } else { "" })
        "  Front-App     http://localhost:$frontAppPort/admin/dashboard"
        Start-Sleep -Seconds 10
    }
}

if ($Status) {
    Show-Health
    return
}

# The program listening on a port of this machine, or $null
function Get-PortOwner([int]$port) {
    $listener = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $listener) { return $null }
    $name = (Get-Process -Id $listener.OwningProcess -ErrorAction SilentlyContinue).ProcessName
    if ($name -in "wslrelay", "com.docker.backend") {
        # Docker's port relay: name the container that publishes the port
        $container = docker ps --format "{{.Names}} {{.Ports}}" | Where-Object { $_ -match ":$port->" } | Select-Object -First 1
        if ($container) { return "the container $(($container -split ' ')[0])" }
    }
    if ($name) { "$name (process $($listener.OwningProcess))" } else { "process $($listener.OwningProcess)" }
}

Push-Location $root
try {
    Write-Host "1. The platform's ports must be free (offset $PortOffset)"
    # The services run as local processes: a leftover container of the time they ran in Docker must not hold a port.
    # The app services are commented out of docker-compose.yml, so compose no longer knows them: stop any leftover
    # container by its name (only the ones that are running)
    $running = @(docker ps --format "{{.Names}}")
    $leftovers = @($services.Keys | ForEach-Object { "fd-$_" } | Where-Object { $running -contains $_ })
    if ($leftovers.Count -gt 0) { docker stop @leftovers | Out-Null }
    # Ports this project's own containers publish are fine (compose keeps or replaces them)
    $ownPorts = @(docker ps --filter "label=com.docker.compose.project=food-delivery" --format "{{.Ports}}" |
        ForEach-Object { [regex]::Matches($_, ':(\d+)->') | ForEach-Object { [int]$_.Groups[1].Value } })
    $wanted = [ordered]@{}
    foreach ($name in $infrastructure.Keys) { $wanted["$name"] = $infrastructure[$name] }
    foreach ($service in $services.Keys) { $wanted[$service] = $services[$service] }
    $wanted["front-app"] = $frontAppPort
    $conflicts = foreach ($name in $wanted.Keys) {
        $port = $wanted[$name]
        if ($ownPorts -contains $port -and $infrastructure.Contains($name)) { continue }
        $owner = Get-PortOwner $port
        if ($owner) { "   port {0,-6} ({1}) is used by {2}" -f $port, $name, $owner }
    }
    if ($conflicts) {
        $conflicts
        $next = $PortOffset + 10000
        throw "Another program holds these ports, so nothing was started. Run the platform on other ports: .\start-local.ps1 -PortOffset $next (or FD_PORT_OFFSET=$next in .env)"
    }

    Write-Host "2. Infrastructure in Docker (MySQL, Kafka, Schema Registry, Redis, Mailpit$(if ($withSearch) { ', Elasticsearch' })$(if ($withAi) { ', Ollama' }))"
    # docker compose reads the ports from these variables (they come before the values of .env)
    foreach ($name in $infrastructure.Keys) { Set-Item "env:$name" $infrastructure[$name] }
    $containers = @("mysql", "kafka", "schema-registry", "redis", "mailpit", "phpmyadmin")
    if ($withSearch) { $containers += "elasticsearch" }
    if ($withAi) { $containers += "ollama", "ollama-pull" }
    docker compose up -d @containers
    if ($LASTEXITCODE -ne 0) { throw "docker compose up failed" }
    $healthChecked = @("fd-mysql", "fd-kafka", "fd-schema-registry", "fd-redis")
    if ($withSearch) { $healthChecked += "fd-elasticsearch" }
    foreach ($container in $healthChecked) {
        $deadline = (Get-Date).AddMinutes(3)
        while ((docker inspect -f "{{.State.Health.Status}}" $container) -ne "healthy") {
            if ((Get-Date) -gt $deadline) { throw "$container is not healthy: docker compose logs $($container -replace '^fd-', '')" }
            Start-Sleep -Seconds 2
        }
        Write-Host "   $container healthy"
    }

    Write-Host "3. The shared event library in the local Maven repository"
    # The version the services use (event-contracts/pom.xml): a newer one is installed when it is not there yet
    $contractsVersion = ([xml](Get-Content "$root\event-contracts\pom.xml")).project.version
    if ($Build -or -not (Test-Path "$env:USERPROFILE\.m2\repository\com\fooddelivery\event-contracts\$contractsVersion")) {
        Push-Location "$root\event-contracts"
        try { & .\mvnw.cmd -B -q install -Dcheckstyle.skip -Dspotbugs.skip } finally { Pop-Location }
        if ($LASTEXITCODE -ne 0) { throw "The build of event-contracts failed" }
    }

    Write-Host "4. The services' jars"
    $jars = @{}
    foreach ($service in $services.Keys) {
        $jar = Get-ChildItem "$root\$service\target\$service-*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($Build -or -not $jar) {
            Write-Host "   building $service"
            Push-Location "$root\$service"
            try { & .\mvnw.cmd -B -q package -DskipTests } finally { Pop-Location }
            if ($LASTEXITCODE -ne 0) { throw "The build of $service failed" }
            $jar = Get-ChildItem "$root\$service\target\$service-*.jar" | Select-Object -First 1
        }
        $jars[$service] = $jar.Name
    }

    # Where every service finds the others and the infrastructure (environment variables come before the values of
    # .env, which each service also reads); one file that every service's command loads
    $urlVariables = @{ "ai-mcp-server" = "AGENT_SERVER_URL"; "ai-mcp-client" = "AI_MCP_CLIENT_URL"; "api-gateway" = "GATEWAY_URL" }
    $settings = [ordered]@{
        MYSQL_PORT = $infrastructure["MYSQL_PORT"]
        KAFKA_BOOTSTRAP_SERVERS = "localhost:$($infrastructure['KAFKA_HOST_PORT'])"
        SCHEMA_REGISTRY_URL = "http://localhost:$($infrastructure['SCHEMA_REGISTRY_HOST_PORT'])"
        REDIS_PORT = $infrastructure["REDIS_HOST_PORT"]
        MAIL_PORT = $infrastructure["MAILPIT_SMTP_HOST_PORT"]
        CORS_ALLOWED_ORIGINS = "http://localhost:$frontAppPort"
        REALTIME_ALLOWED_ORIGINS = "http://localhost:$frontAppPort,http://127.0.0.1:$frontAppPort"
        # The gateway allows as many logins and registrations per minute as in docker-compose (the test scripts and
        # E2E tests register many users from this machine); production keeps its default of 10
        GATEWAY_AUTH_RATE_LIMIT_PER_MINUTE = 120
        # Observability is off (docker-compose.yml): no trace is sent, so none reaches another program that happens
        # to listen on the default address (localhost:4318)
        TRACING_SAMPLING_PROBABILITY = 0
        VITE_API_BASE_URL = $gatewayUrl
    }
    if ($withSearch) { $settings["ELASTICSEARCH_URIS"] = "http://localhost:$($infrastructure['ELASTICSEARCH_HOST_PORT'])" }
    if ($withAi) { $settings["OLLAMA_BASE_URL"] = "http://localhost:$($infrastructure['OLLAMA_HOST_PORT'])" }
    foreach ($service in $ports.Keys) {
        $variable = if ($urlVariables.Contains($service)) { $urlVariables[$service] } else { ($service -replace "-", "_").ToUpper() + "_URL" }
        $settings[$variable] = "http://localhost:$($ports[$service])"
    }
    New-Item -ItemType Directory -Force $logs | Out-Null
    $settingsFile = "$logs\local-settings.cmd"
    $lines = @("@echo off") + ($settings.Keys | ForEach-Object { "set `"$_=$($settings[$_])`"" })
    Set-Content -Path $settingsFile -Value $lines -Encoding ascii

    Write-Host "5. Front-App's packages"
    if ($Build -or -not (Test-Path "$root\front-app\node_modules")) {
        Push-Location "$root\front-app"
        try { & npm.cmd ci --no-fund --no-audit } finally { Pop-Location }
        if ($LASTEXITCODE -ne 0) { throw "npm ci in front-app failed" }
    }

    if ($Detached) {
        Write-Host "6. The services, one after the other, then Front-App (logs in $logs)"
        if (Test-Path $pidFile) { throw "Services started with -Detached are still running: .\start-local.ps1 -Stop first" }
        foreach ($service in $services.Keys) {
            $port = $services[$service]
            $command = "call `"$settingsFile`"&& set SERVER_PORT=$port&& java $JvmOptions -jar target\$($jars[$service]) > `"$logs\$service.log`" 2>&1"
            $process = Start-Process cmd.exe -ArgumentList "/s /c `"$command`"" -WorkingDirectory "$root\$service" -WindowStyle Hidden -PassThru
            Add-Content $pidFile "$service $($process.Id)"
            $deadline = (Get-Date).AddMinutes(3)
            do {
                Start-Sleep -Seconds 3
                # Any answer means started (DOWN comes as 503); no answer yet: still starting
                $state = try { (Invoke-RestMethod "http://localhost:$port/actuator/health" -TimeoutSec 2).status } catch { if ($_.Exception.Response) { "DOWN" } else { $null } }
                if ($process.HasExited) { throw "$service stopped while starting: see $logs\$service.log" }
                if ((Get-Date) -gt $deadline) { throw "$service did not start within 3 minutes: see $logs\$service.log" }
            } until ($state)
            Write-Host ("   {0,-24} http://localhost:{1,-5} {2}" -f $service, $port, $state)
        }
        # The web application as well (-Stop stops it with the services)
        $command = "call `"$settingsFile`"&& npm run dev -- --port $frontAppPort > `"$logs\front-app.log`" 2>&1"
        $process = Start-Process cmd.exe -ArgumentList "/s /c `"$command`"" -WorkingDirectory "$root\front-app" -WindowStyle Hidden -PassThru
        Add-Content $pidFile "front-app $($process.Id)"
        $deadline = (Get-Date).AddMinutes(2)
        while (-not (Get-PortOwner $frontAppPort)) {
            if ($process.HasExited) { throw "Front-App stopped while starting: see $logs\front-app.log" }
            if ((Get-Date) -gt $deadline) { throw "Front-App did not start within 2 minutes: see $logs\front-app.log" }
            Start-Sleep -Seconds 2
        }
        Write-Host ("   {0,-24} http://localhost:{1}" -f "front-app", $frontAppPort)
        Write-Host "Started. Front-App: http://localhost:$frontAppPort   API Gateway: $gatewayUrl   Stop all: .\start-local.ps1 -Stop"
        Write-Host "End-to-end tests: `$env:E2E_BASE_URL='$gatewayUrl'; `$env:E2E_MAILPIT_URL='$mailpitUrl'; cd e2e-tests; .\mvnw.cmd verify"
        return
    }

    Write-Host "6. One Windows Terminal window, a tab per service"
    # Each service runs in its own folder, so it reads the passwords from ..\.env
    $tabs = foreach ($service in $services.Keys) {
        "new-tab --title $service --suppressApplicationTitle -d `"$root\$service`" cmd /k call `"$settingsFile`"&& set SERVER_PORT=$($services[$service])&& java $JvmOptions -jar target\$($jars[$service])"
    }
    $tabs += "new-tab --title front-app --suppressApplicationTitle -d `"$root\front-app`" cmd /k call `"$settingsFile`"&& npm run dev -- --port $frontAppPort"
    $statusArguments = "-Status -PortOffset $PortOffset" + $(if ($NoAi) { " -NoAi" } else { "" }) + $(if ($Only) { " -Only " + ($Only -join ",") } else { "" })
    $tabs += "new-tab --title status --suppressApplicationTitle -d `"$root`" powershell -NoExit -ExecutionPolicy Bypass -File `"$root\start-local.ps1`" $statusArguments"
    Start-Process wt.exe -ArgumentList ("-w new " + ($tabs -join " ; "))
    Write-Host "Started. The services need about a minute; the 'status' tab shows when all are UP."
    Write-Host "Front-App: http://localhost:$frontAppPort/admin/dashboard   API Gateway: $gatewayUrl"
} finally {
    Pop-Location
}
