<#
.SYNOPSIS
    Analyses every project with SonarQube (http://localhost:9000) and prints each one's quality gate.

.DESCRIPTION
    Needs SonarQube running:  docker compose --profile quality up -d
    On the first run it sets the admin password (SONARQUBE_ADMIN_PASSWORD from .env) and creates the
    "Food Delivery" quality gate. Each run uses a short-lived analysis token, revoked at the end.

.EXAMPLE
    .\quality\sonar-scan.ps1                          # build, test and analyse all projects
    .\quality\sonar-scan.ps1 -Projects order-service  # one project
    .\quality\sonar-scan.ps1 -SkipBuild               # analyse the last "mvnw verify" output only
#>
param(
    [string[]]$Projects = @("event-contracts", "api-gateway", "user-service", "restaurant-service", "catalog-service",
        "order-service", "payment-service", "notification-service", "delivery-service", "delivery-partner-service",
        "search-service", "promotion-service", "review-service", "chat-service", "admin-service"),
    [switch]$SkipBuild,
    [string]$HostUrl = "http://localhost:9000"
)
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent

# The quality gate every project must pass: no bugs or vulnerabilities, maintainability A, tests that cover
# most of the code, little copy-paste; new code is held to Sonar's own "clean as you code" bar.
$gateName = "Food Delivery"
$gateConditions = @(
    @{ metric = "reliability_rating"; op = "GT"; error = "1" },           # rating A: no bugs
    @{ metric = "security_rating"; op = "GT"; error = "1" },              # rating A: no vulnerabilities
    @{ metric = "sqale_rating"; op = "GT"; error = "1" },                 # maintainability rating A
    @{ metric = "coverage"; op = "LT"; error = "60" },                    # line + branch coverage of all code
    @{ metric = "duplicated_lines_density"; op = "GT"; error = "5" },
    @{ metric = "new_coverage"; op = "LT"; error = "80" },
    @{ metric = "new_duplicated_lines_density"; op = "GT"; error = "3" },
    @{ metric = "new_violations"; op = "GT"; error = "0" }                # no new issues
)

# Rules set differently from Sonar way, to agree with quality/checkstyle.xml
$profileRules = @(
    # Parameters per method and constructor (Checkstyle ParameterNumber: 10)
    @{ rule = "java:S107"; params = @{ max = "10"; constructorMax = "10" } }
)

function Get-EnvValue($name) {
    $line = Get-Content "$root\.env" | Where-Object { $_ -like "$name=*" } | Select-Object -First 1
    if (-not $line) { throw "$name is missing in .env (see .env.example)" }
    return ($line -split "=", 2)[1]
}
function Basic($user, $secret) {
    return @{ Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${user}:$secret")) }
}
function Api($method, $path, $headers, $body = $null) {
    $params = @{ Method = $method; Uri = "$HostUrl$path"; Headers = $headers; TimeoutSec = 60 }
    if ($body) { $params.Body = $body; $params.ContentType = "application/x-www-form-urlencoded" }
    return Invoke-RestMethod @params
}

# ---- SonarQube up?
Write-Host "Waiting for SonarQube at $HostUrl ..."
$deadline = (Get-Date).AddMinutes(5)
while ($true) {
    try { if ((Invoke-RestMethod "$HostUrl/api/system/status" -TimeoutSec 5).status -eq "UP") { break } } catch { }
    if ((Get-Date) -gt $deadline) { throw "SonarQube is not up. Start it with: docker compose --profile quality up -d" }
    Start-Sleep -Seconds 3
}

# ---- First run: replace the default admin password
$adminPassword = Get-EnvValue "SONARQUBE_ADMIN_PASSWORD"
$admin = Basic "admin" $adminPassword
if (-not (Api GET "/api/authentication/validate" $admin).valid) {
    if (-not (Api GET "/api/authentication/validate" (Basic "admin" "admin")).valid) {
        throw "SonarQube rejects the admin password from .env (SONARQUBE_ADMIN_PASSWORD)"
    }
    Api POST "/api/users/change_password" (Basic "admin" "admin") "login=admin&previousPassword=admin&password=$([uri]::EscapeDataString($adminPassword))" | Out-Null
    Write-Host "SonarQube admin password set from .env"
}

# ---- The quality gate (created once, then kept in line with the conditions above)
$encodedGate = [uri]::EscapeDataString($gateName)
$gates = (Api GET "/api/qualitygates/list" $admin).qualitygates
if (-not ($gates | Where-Object { $_.name -eq $gateName })) {
    Api POST "/api/qualitygates/create" $admin "name=$encodedGate" | Out-Null
    Write-Host "Quality gate '$gateName' created"
}
$existing = (Api GET "/api/qualitygates/show?name=$encodedGate" $admin).conditions
foreach ($c in $gateConditions) {
    $current = $existing | Where-Object { $_.metric -eq $c.metric }
    if ($current -and ($current.op -ne $c.op -or $current.error -ne $c.error)) {
        Api POST "/api/qualitygates/delete_condition" $admin "id=$($current.id)" | Out-Null
        $current = $null
    }
    if (-not $current) {
        Api POST "/api/qualitygates/create_condition" $admin "gateName=$encodedGate&metric=$($c.metric)&op=$($c.op)&error=$($c.error)" | Out-Null
    }
}
Api POST "/api/qualitygates/set_as_default" $admin "name=$encodedGate" | Out-Null

# ---- The Java rules: Sonar way (inherited, so it keeps getting SonarQube's updates), with this project's settings
$profileName = "Food Delivery way"
$encodedProfile = [uri]::EscapeDataString($profileName)
$profiles = (Api GET "/api/qualityprofiles/search?language=java" $admin).profiles
$qualityProfile = $profiles | Where-Object { $_.name -eq $profileName }
if (-not $qualityProfile) {
    $qualityProfile = (Api POST "/api/qualityprofiles/create" $admin "language=java&name=$encodedProfile").profile
    Api POST "/api/qualityprofiles/change_parent" $admin "language=java&qualityProfile=$encodedProfile&parentQualityProfile=$([uri]::EscapeDataString('Sonar way'))" | Out-Null
    Write-Host "Quality profile '$profileName' created"
}
foreach ($rule in $profileRules) {
    $params = ($rule.params.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join ";"
    Api POST "/api/qualityprofiles/activate_rule" $admin "key=$($qualityProfile.key)&rule=$($rule.rule)&params=$([uri]::EscapeDataString($params))" | Out-Null
}
Api POST "/api/qualityprofiles/set_default" $admin "language=java&qualityProfile=$encodedProfile" | Out-Null

# ---- Analyse, with a token that only lives for this run
$tokenName = "scan-" + (Get-Date -Format "yyyyMMdd-HHmmss")
$token = (Api POST "/api/user_tokens/generate" $admin "name=$tokenName&type=GLOBAL_ANALYSIS_TOKEN").token
$results = @()
try {
    $env:SONAR_TOKEN = $token
    foreach ($project in $Projects) {
        Write-Host ""
        Write-Host "==== $project" -ForegroundColor Cyan
        $goals = @(if ($SkipBuild) { "sonar:sonar" } else { "verify"; "sonar:sonar" })
        $log = Join-Path ([IO.Path]::GetTempPath()) "sonar-$project.log"
        Push-Location "$root\$project"
        # Windows PowerShell turns a native command's stderr lines into errors: they must not stop the script
        $ErrorActionPreference = "Continue"
        try {
            & .\mvnw.cmd -B @goals "-Dsonar.host.url=$HostUrl" "-Dsonar.qualitygate.wait=true" "-Dsonar.qualitygate.timeout=300" *> $log
            $exit = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = "Stop"
            Pop-Location
        }
        $key = "food-delivery-$project"
        $gate = try { (Api GET "/api/qualitygates/project_status?projectKey=$key" $admin).projectStatus.status } catch { "NONE" }
        $m = @{}
        try {
            $measures = (Api GET "/api/measures/component?component=$key&metricKeys=bugs,vulnerabilities,code_smells,security_hotspots,coverage,duplicated_lines_density,ncloc" $admin).component.measures
            foreach ($x in $measures) { $m[$x.metric] = $x.value }
        } catch { }
        $results += [pscustomobject]@{
            Project = $project; Gate = $gate; Build = $(if ($exit -eq 0) { "ok" } else { "FAILED (see $log)" })
            Lines = $m.ncloc; Bugs = $m.bugs; Vulnerabilities = $m.vulnerabilities; Hotspots = $m.security_hotspots
            CodeSmells = $m.code_smells; Coverage = $m.coverage; Duplication = $m.duplicated_lines_density
        }
        Write-Host "quality gate: $gate"
    }
} finally {
    Remove-Item Env:SONAR_TOKEN -ErrorAction SilentlyContinue
    Api POST "/api/user_tokens/revoke" $admin "name=$tokenName" | Out-Null
}

Write-Host ""
$results | Format-Table -AutoSize
Write-Host "Details: $HostUrl/projects"
if ($results | Where-Object { $_.Gate -ne "OK" }) { exit 1 }
