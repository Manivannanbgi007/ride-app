<#
.SYNOPSIS
    Fills the running platform with demo data, through its HTTP APIs only (the API Gateway), so that every admin
    page has plenty to show: dashboard, categories, restaurants, foods, customers, orders, payments, refunds,
    deliveries, coupons, calendar and analytics. Nothing is written to MySQL directly and no code is changed.

    What it creates (every new account has the password Demo@1234; e-mails end in @demo.fooddelivery.local):
      - 10 food categories, with pictures that Front-App serves (/images/...)
      - 25 restaurants, each with its own owner (owner.<name>@...): 18 ACTIVE and open all day in Bengaluru,
        2 SUSPENDED (after a few orders), 3 PENDING (waiting for review), 2 REJECTED
      - 90 dishes on the menus of the 20 restaurants that went live (VEG, NON_VEG and EGG, new categories too)
      - 25 customers (<first>.<last>@...) with a phone number and a Bengaluru address
      - 21 delivery partners (rider.<name>@...), online in Bengaluru
      - 12 coupons: active, paused, expired and upcoming (this month and next), platform-wide and per restaurant
      - the test accounts of app-credential.txt get data too: QA Test Kitchen (test.owner) 6 more dishes and 16 orders
        at every step of its kitchen, manual.customer 8 orders; rider.bengaluru answers offers (see $OtherRiders)
      - 91 orders: 56 delivered, 11 cancelled or rejected, 20 under way (placed, accepted, preparing, ready, out for
        delivery) and 4 waiting for payment; paid by UPI, card, wallet or cash on delivery; 20 with a coupon
      - from them: the payments (two cards declined first), 18 refunds by the admin (full, partial, two that fail)
        and the automatic refunds of the cancelled paid orders, 65 deliveries, 48 reviews with dish ratings

    Re-runnable: an account whose login works is reused; a category, restaurant, dish, coupon or address that
    exists is skipped; every order has a fixed Idempotency-Key, so a second run finds its orders instead of placing
    them again and only does the steps a first run did not finish. -Batch 2 places a new set of 91 orders.

    Online payments: Razorpay Checkout (PAYMENT_PROVIDER=RAZORPAY in .env) needs a browser. When it is on, the script
    restarts only payment-service (started by .\start-local.ps1 -Detached) with PAYMENT_PROVIDER=SIMULATED for the
    payments, and restarts it with the settings of .env again at the end; logs\detached-services.txt gets the new
    process id both times, so .\start-local.ps1 -Stop still stops it. -NoPaymentRestart: never restart it (online
    orders then stay waiting for payment).

    Needs the platform running (.\start-local.ps1 -Detached) and the bootstrap admin of .env. The 4 orders waiting
    for payment are cancelled by order-service 15 minutes later (ORDER_PAYMENT_TIMEOUT), like any unpaid order.
    Dates cannot be set through the APIs: orders, payments and deliveries are dated today (the coupons' validity
    dates spread over last month, this month and next month).

.EXAMPLE
    .\seed-dev-data.ps1                     # create what is missing (about 25 minutes the first time)
    .\seed-dev-data.ps1 -Batch 2            # 91 more orders, with the same accounts, restaurants and dishes
    .\seed-dev-data.ps1 -NoPaymentRestart   # never restart payment-service
    .\seed-dev-data.ps1 -ApiUrl http://localhost:18080 -FrontAppUrl http://localhost:15180   # -PortOffset 10000
#>
param(
    [string]$ApiUrl = "http://localhost:8080",
    # Dish and category pictures: Front-App's public/images (the APIs only take full URLs)
    [string]$FrontAppUrl = "http://localhost:5180",
    # Which set of 91 orders (each batch has its own Idempotency-Keys)
    [int]$Batch = 1,
    [switch]$NoPaymentRestart,
    [string]$AdminEmail = "admin@fooddelivery.local",
    [string]$AdminPassword = "ChangeMe123",
    # Partners that existed before (email:password): the script answers the delivery offers they get
    [string[]]$OtherRiders = @("rider.bengaluru@example.com:Rider1234"),
    # The test accounts of app-credential.txt (email:password): they get orders too (Manual in the data below)
    [string]$ManualCustomer = "manual.customer@example.com:Customer@123",
    [string]$ManualOwner = "test.owner@fooddelivery.local:Test@12345",
    # The same as start-local.ps1, for the restart of payment-service
    [string]$JvmOptions = "-Xms128m -Xmx512m"
)
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$Password = "Demo@1234"
$Domain = "demo.fooddelivery.local"
$City = "Bengaluru"
$started = Get-Date
$created = [ordered]@{ categories = 0; restaurants = 0; dishes = 0; customers = 0; riders = 0; owners = 0; coupons = 0
    orders = 0; payments = 0; refunds = 0; deliveries = 0; reviews = 0 }
$warnings = New-Object System.Collections.Generic.List[string]

function Write-Step([string]$text) { Write-Host $text -ForegroundColor Cyan }
function Write-Info([string]$text) { Write-Host "   $text" }
function Write-Warn([string]$text) { Write-Host "   WARNING: $text" -ForegroundColor Yellow; $warnings.Add($text) }

# ------------------------------------------------------------------------------------------------------------------
# HTTP: one call through the gateway, signed in as an account. 429 (rate limit) and gateway errors are retried;
# statuses in -Allow return $null instead of failing ($script:LastStatus / $script:LastError say what happened).
# ------------------------------------------------------------------------------------------------------------------
$script:Tokens = @{}
$script:LastStatus = 0
$script:LastError = $null

function New-Account([string]$Email, [string]$Pass, [string]$Role, [string]$Name = "", [string]$Phone = "") {
    [pscustomobject]@{ Email = $Email; Password = $Pass; Role = $Role; Name = $Name; Phone = $Phone; UserId = $null
        AddressId = $null; Existing = $false }
}

function Get-AccessToken($Account) {
    $key = "$($Account.Role)|$($Account.Email)"
    $cached = $script:Tokens[$key]
    # Tokens live 15 minutes: sign in again after 12
    if ($cached -and ((Get-Date) - $cached.At).TotalMinutes -lt 12) { return $cached.Token }
    $login = Invoke-Api POST "/api/v1/auth/login" @{ email = $Account.Email; password = $Account.Password; role = $Account.Role }
    $Account.UserId = $login.userId
    $script:Tokens[$key] = @{ Token = $login.accessToken; At = Get-Date }
    return $login.accessToken
}

function Invoke-Api {
    param([string]$Method, [string]$Path, $Body = $null, $As = $null, [hashtable]$Headers = @{}, [int[]]$Allow = @())
    for ($attempt = 1; ; $attempt++) {
        $sent = @{}
        if ($As) { $sent["Authorization"] = "Bearer " + (Get-AccessToken $As) }
        foreach ($name in $Headers.Keys) { $sent[$name] = $Headers[$name] }
        $request = @{ Method = $Method; Uri = "$ApiUrl$Path"; Headers = $sent; ContentType = "application/json; charset=utf-8"; TimeoutSec = 60 }
        if ($null -ne $Body) { $request.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 8 -Compress)) }
        try {
            $result = Invoke-RestMethod @request
            $script:LastStatus = 200
            $script:LastError = $null
            return $result
        } catch {
            $status = 0
            if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
            $message = if ($_.ErrorDetails -and $_.ErrorDetails.Message) { $_.ErrorDetails.Message } else { $_.Exception.Message }
            if ($_.Exception.Response -and -not ($_.ErrorDetails -and $_.ErrorDetails.Message)) {
                # Windows PowerShell leaves application/problem+json bodies in the response stream
                try {
                    $stream = $_.Exception.Response.GetResponseStream()
                    if ($stream.CanSeek) { $stream.Position = 0 }
                    $text = (New-Object IO.StreamReader($stream)).ReadToEnd()
                    if ($text) { $message = $text }
                } catch { }
            }
            $script:LastStatus = $status
            $script:LastError = $message
            if ($status -eq 429 -and $attempt -le 12) { Start-Sleep -Milliseconds (800 * $attempt); continue }
            if ($status -eq 401 -and $As -and $attempt -le 2) { $script:Tokens.Remove("$($As.Role)|$($As.Email)"); continue }
            if (($status -eq 0 -or $status -eq 502 -or $status -eq 503 -or $status -eq 504) -and $attempt -le 4) {
                Start-Sleep -Seconds (3 * $attempt); continue
            }
            if ($Allow -contains $status) { return $null }
            throw "$Method $Path -> HTTP $status $message"
        }
    }
}

# A page ({content: [...]}) or a plain list, as an array
function Get-Items($response) {
    if ($null -eq $response) { return @() }
    if ($response.PSObject.Properties.Name -contains "content") { return @($response.content) }
    return @($response)
}

# Runs $Probe until it returns something, at most $Seconds
function Wait-For([scriptblock]$Probe, [string]$What, [int]$Seconds = 45, [int]$EveryMs = 1500) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    while ($true) {
        $result = & $Probe
        if ($result) { return $result }
        if ((Get-Date) -gt $deadline) { throw "Timed out after $Seconds s waiting for $What (last answer: HTTP $($script:LastStatus) $($script:LastError))" }
        Start-Sleep -Milliseconds $EveryMs
    }
}

# Signs in, or registers the account first when its login does not work. Returns $true when it was created.
function Confirm-Account($Account) {
    $credentials = @{ email = $Account.Email; password = $Account.Password; role = $Account.Role }
    $login = Invoke-Api POST "/api/v1/auth/login" $credentials -Allow 400, 401, 403, 404, 423
    $new = $false
    if (-not $login) {
        $registration = @{ email = $Account.Email; password = $Account.Password; fullName = $Account.Name; role = $Account.Role }
        if ($Account.Phone) { $registration.phone = $Account.Phone }
        Invoke-Api POST "/api/v1/auth/register" $registration -Allow 409 | Out-Null
        if ($script:LastStatus -eq 409) {
            throw "$($Account.Email) exists but its login as $($Account.Role) with the demo password fails: $($script:LastError)"
        }
        $login = Invoke-Api POST "/api/v1/auth/login" $credentials
        $new = $true
    }
    $Account.UserId = $login.userId
    $script:Tokens["$($Account.Role)|$($Account.Email)"] = @{ Token = $login.accessToken; At = Get-Date }
    return $new
}

# ------------------------------------------------------------------------------------------------------------------
# payment-service: SIMULATED while the script pays (Razorpay Checkout needs a browser), then back to .env
# ------------------------------------------------------------------------------------------------------------------
$pidFile = "$root\logs\detached-services.txt"
$settingsFile = "$root\logs\local-settings.cmd"
$paymentPort = 8085
if (Test-Path $settingsFile) {
    $match = Select-String -Path $settingsFile -Pattern 'PAYMENT_SERVICE_URL=http://localhost:(\d+)' | Select-Object -First 1
    if ($match) { $paymentPort = [int]$match.Matches[0].Groups[1].Value }
}
$script:PaymentsReady = $null      # $true: online payments work; $false: they cannot be made in this run
$script:PaymentSwitched = $false   # payment-service runs with the SIMULATED override and must be restarted

function Test-RazorpayOn($Customer) {
    [bool](Invoke-Api GET "/api/v1/payments/razorpay/config" -As $Customer).enabled
}

function Test-PortListening([int]$port) {
    try { [bool](Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction Stop) } catch { $false }
}

function Restart-PaymentService([switch]$Simulated) {
    if (-not (Test-Path $pidFile)) {
        throw "No $pidFile`: payment-service was not started by .\start-local.ps1 -Detached. Restart it yourself with PAYMENT_PROVIDER=SIMULATED, or run with -NoPaymentRestart"
    }
    $lines = @(Get-Content $pidFile)
    $line = $lines | Where-Object { $_ -match '^payment-service \d+$' } | Select-Object -First 1
    if (-not $line) { throw "payment-service is not listed in $pidFile" }
    $oldPid = ($line -split " ")[1]
    # Its cmd and the java process under it (/T), like .\start-local.ps1 -Stop
    & cmd.exe /c "taskkill /PID $oldPid /T /F >nul 2>&1"
    Wait-For { -not (Test-PortListening $paymentPort) } "payment-service to stop" 60 1000 | Out-Null
    $jar = Get-ChildItem "$root\payment-service\target\payment-service-*.jar" | Select-Object -First 1
    if (-not $jar) { throw "No payment-service jar in payment-service\target" }
    $override = if ($Simulated) { "&& set PAYMENT_PROVIDER=SIMULATED" } else { "" }
    $command = "call `"$settingsFile`"&& set SERVER_PORT=$paymentPort$override&& java $JvmOptions -jar target\$($jar.Name) > `"$root\logs\payment-service.log`" 2>&1"
    $process = Start-Process cmd.exe -ArgumentList "/s /c `"$command`"" -WorkingDirectory "$root\payment-service" -WindowStyle Hidden -PassThru
    $lines = $lines | ForEach-Object { if ($_ -eq $line) { "payment-service $($process.Id)" } else { $_ } }
    Set-Content -Path $pidFile -Value $lines -Encoding ascii
    $mode = if ($Simulated) { "PAYMENT_PROVIDER=SIMULATED" } else { "the settings of .env" }
    Write-Info "payment-service restarted with $mode (process $($process.Id), log logs\payment-service.log)"
    Wait-For {
        if ($process.HasExited) { throw "payment-service stopped while starting: see logs\payment-service.log" }
        try { (Invoke-RestMethod "http://localhost:$paymentPort/actuator/health" -TimeoutSec 3).status -eq "UP" } catch { $false }
    } "payment-service to be UP" 180 3000 | Out-Null
    Write-Info "payment-service is UP"
}

# $true when online payments can be made now (switches payment-service to SIMULATED the first time if needed)
function Confirm-OnlinePayments($Customer) {
    if ($null -ne $script:PaymentsReady) { return $script:PaymentsReady }
    if (-not (Test-RazorpayOn $Customer)) { $script:PaymentsReady = $true; return $true }
    if ($NoPaymentRestart) {
        Write-Warn "payment-service uses Razorpay Checkout and -NoPaymentRestart is set: online orders stay waiting for payment"
        $script:PaymentsReady = $false
        return $false
    }
    Write-Info "payment-service uses Razorpay Checkout (needs a browser): switching it to the simulated gateway for the seeding"
    $script:PaymentSwitched = $true
    Restart-PaymentService -Simulated
    Wait-For { try { -not (Test-RazorpayOn $Customer) } catch { $false } } "the gateway to reach the restarted payment-service" 90 3000 | Out-Null
    $script:PaymentsReady = $true
    return $true
}

# ------------------------------------------------------------------------------------------------------------------
# The demo data
# ------------------------------------------------------------------------------------------------------------------
$Categories = @(
    @{ Name = "Starters"; Description = "Tikkas, breadsticks and small plates to begin with"; Image = "foods/paneer-tikka" },
    @{ Name = "Kebabs & Tandoori"; Description = "Seekh, tikka and tandoori from the clay oven"; Image = "foods/seekh-kebab" },
    @{ Name = "Thalis & Meals"; Description = "Complete meals on one plate"; Image = "foods/veg-thali" },
    @{ Name = "Breakfast & Tiffin"; Description = "Idli, vada, upma and other morning favourites"; Image = "foods/idli" },
    @{ Name = "Dosa Specials"; Description = "Masala, ghee roast, rava and set dosas"; Image = "foods/masala-dosa" },
    @{ Name = "Rice & Pulao"; Description = "Pulao, jeera rice and flavoured rice"; Image = "foods/veg-pulao" },
    @{ Name = "Indian Sweets"; Description = "Gulab jamun, kesari, halwa and mithai"; Image = "foods/gulab-jamun" },
    @{ Name = "Kulfi & Ice Cream"; Description = "Kulfi, falooda and ice cream scoops"; Image = "foods/malai-kulfi" },
    @{ Name = "Mutton Specials"; Description = "Mutton biryani, curries and kebabs"; Image = "foods/mutton-biryani" },
    @{ Name = "Rolls & Wraps"; Description = "Kathi rolls, frankies and wraps"; Image = "categories/north-indian" }
)

# Target: ACTIVE, SUSPENDED (live first, suspended after the orders), PENDING or REJECTED.
# Dishes: N name, P price, C category, T food type, I picture (public/images/<I>.jpg), D description
$Restaurants = @(
    @{ Key = "BD"; Name = "Biryani Darbar"; Target = "ACTIVE"; Cuisines = @("Biryani", "Mughlai", "Hyderabadi")
        Description = "Hyderabadi dum biryani slow-cooked in sealed handis, since 2009"
        Owner = "Farhan Qureshi"; OwnerPhone = "+919845020101"; Phone = "+918041100101"
        Line1 = "80 Feet Road, 6th Block, Koramangala"; Pin = "560095"; Lat = 12.9352; Lon = 77.6245; Gst = "29AAKFB1201M1Z5"
        Dishes = @(
            @{ N = "Hyderabadi Chicken Dum Biryani"; P = 329; C = "Biryani"; T = "NON_VEG"; I = "foods/hyderabadi-chicken-biryani"; D = "Long-grain basmati and marinated chicken cooked dum style, with mirchi ka salan and raita" },
            @{ N = "Mutton Dum Biryani"; P = 399; C = "Mutton Specials"; T = "NON_VEG"; I = "foods/mutton-biryani"; D = "Tender goat meat layered with saffron rice and fried onions" },
            @{ N = "Paneer Dum Biryani"; P = 279; C = "Biryani"; T = "VEG"; I = "foods/paneer-biryani"; D = "Cottage cheese cubes in spiced basmati rice, sealed and slow-cooked" },
            @{ N = "Egg Biryani"; P = 229; C = "Biryani"; T = "EGG"; I = "foods/egg-biryani"; D = "Two boiled eggs roasted in masala over fragrant biryani rice" },
            @{ N = "Double Ka Meetha"; P = 119; C = "Indian Sweets"; T = "VEG"; I = "categories/desserts"; D = "Hyderabadi bread pudding with saffron milk and dry fruits" }) },
    @{ Key = "US"; Name = "Udupi Sagar Tiffins"; Target = "ACTIVE"; Cuisines = @("South Indian", "Udupi")
        Description = "Crisp dosas, soft idlis and filter coffee, the Udupi way"
        Owner = "Raghavendra Bhat"; OwnerPhone = "+919845020102"; Phone = "+918041100102"
        Line1 = "11th Main Road, 4th Block, Jayanagar"; Pin = "560011"; Lat = 12.9250; Lon = 77.5838; Gst = "29AAKFU1202M1Z4"
        Dishes = @(
            @{ N = "Masala Dosa"; P = 99; C = "Dosa Specials"; T = "VEG"; I = "foods/masala-dosa"; D = "Golden crisp dosa with potato palya, coconut chutney and sambar" },
            @{ N = "Idli Vada Combo"; P = 79; C = "Breakfast & Tiffin"; T = "VEG"; I = "foods/idli"; D = "Two steamed idlis and one medu vada with chutney and sambar" },
            @{ N = "South Indian Mini Meals"; P = 149; C = "Thalis & Meals"; T = "VEG"; I = "foods/veg-thali"; D = "Rice, sambar, rasam, two palyas, curd and papad" },
            @{ N = "Filter Coffee"; P = 40; C = "Beverages"; T = "VEG"; I = "categories/beverages"; D = "Strong decoction with frothy hot milk, served in a davara" },
            @{ N = "Rava Kesari"; P = 59; C = "Indian Sweets"; T = "VEG"; I = "categories/desserts"; D = "Semolina pudding with ghee, saffron and cashews" }) },
    @{ Key = "PD"; Name = "Punjab Da Dhaba"; Target = "ACTIVE"; Cuisines = @("North Indian", "Punjabi")
        Description = "Tandoor, makhani gravies and lassi from the highway dhabas of Punjab"
        Owner = "Harpreet Singh"; OwnerPhone = "+919845020103"; Phone = "+918041100103"
        Line1 = "12th Main Road, HAL 2nd Stage, Indiranagar"; Pin = "560038"; Lat = 12.9719; Lon = 77.6412; Gst = "29AAKFP1203M1Z3"
        Dishes = @(
            @{ N = "Paneer Tikka"; P = 249; C = "Starters"; T = "VEG"; I = "foods/paneer-tikka"; D = "Chargrilled paneer, peppers and onion in a smoky tandoori marinade" },
            @{ N = "Butter Chicken"; P = 349; C = "North Indian"; T = "NON_VEG"; I = "categories/north-indian"; D = "Tandoori chicken simmered in a buttery tomato and cashew gravy" },
            @{ N = "Dal Makhani"; P = 229; C = "North Indian"; T = "VEG"; I = "categories/north-indian"; D = "Black lentils slow-cooked overnight with butter and cream" },
            @{ N = "Amritsari Kulcha"; P = 99; C = "North Indian"; T = "VEG"; I = "categories/north-indian"; D = "Stuffed tandoor bread with spiced potato, served with chole" },
            @{ N = "Sweet Lassi"; P = 79; C = "Beverages"; T = "VEG"; I = "categories/beverages"; D = "Thick churned curd with a layer of malai" }) },
    @{ Key = "DW"; Name = "Dragon Wok Express"; Target = "ACTIVE"; Cuisines = @("Chinese", "Asian")
        Description = "Indo-Chinese favourites tossed in a fiery wok"
        Owner = "Tenzin Dorjee"; OwnerPhone = "+919845020104"; Phone = "+918041100104"
        Line1 = "27th Main Road, Sector 1, HSR Layout"; Pin = "560102"; Lat = 12.9116; Lon = 77.6474; Gst = "29AAKFD1204M1Z2"
        Dishes = @(
            @{ N = "Veg Hakka Noodles"; P = 179; C = "Chinese"; T = "VEG"; I = "categories/chinese"; D = "Wok-tossed noodles with cabbage, carrot, capsicum and soy" },
            @{ N = "Chicken Manchurian Dry"; P = 229; C = "Chinese"; T = "NON_VEG"; I = "categories/chinese"; D = "Crispy chicken balls tossed in garlic, ginger and soy sauce" },
            @{ N = "Chilli Paneer"; P = 219; C = "Starters"; T = "VEG"; I = "categories/chinese"; D = "Paneer with onions and capsicum in a spicy chilli sauce" },
            @{ N = "Egg Fried Rice"; P = 189; C = "Chinese"; T = "EGG"; I = "categories/chinese"; D = "Fried rice with scrambled egg, spring onion and pepper" }) },
    @{ Key = "NS"; Name = "Napoli Slice"; Target = "ACTIVE"; Cuisines = @("Pizza", "Italian")
        Description = "Hand-stretched pizzas baked hot, made for Whitefield"
        Owner = "Rahul DSouza"; OwnerPhone = "+919845020105"; Phone = "+918041100105"
        Line1 = "ITPL Main Road, Whitefield"; Pin = "560066"; Lat = 12.9698; Lon = 77.7500; Gst = "29AAKFN1205M1Z1"
        Dishes = @(
            @{ N = "Margherita Pizza"; P = 249; C = "Pizza"; T = "VEG"; I = "categories/pizza"; D = "San Marzano tomato, mozzarella and fresh basil" },
            @{ N = "Farmhouse Veg Pizza"; P = 349; C = "Pizza"; T = "VEG"; I = "categories/pizza"; D = "Onion, capsicum, mushroom, tomato and sweet corn" },
            @{ N = "Chicken Tikka Pizza"; P = 399; C = "Pizza"; T = "NON_VEG"; I = "categories/pizza"; D = "Tandoori chicken tikka, onion and green chilli on a cheesy base" },
            @{ N = "Garlic Breadsticks"; P = 129; C = "Starters"; T = "VEG"; I = "categories/pizza"; D = "Oven-baked breadsticks with garlic butter and a cheese dip" }) },
    @{ Key = "BJ"; Name = "Burger Junction"; Target = "ACTIVE"; Cuisines = @("Burgers", "Fast Food", "Rolls")
        Description = "Loaded burgers, kathi rolls and thick shakes"
        Owner = "Nikhil Jain"; OwnerPhone = "+919845020106"; Phone = "+918041100106"
        Line1 = "16th Main Road, BTM Layout 2nd Stage"; Pin = "560076"; Lat = 12.9166; Lon = 77.6101; Gst = "29AAKFB1206M1Z9"
        Dishes = @(
            @{ N = "Classic Aloo Tikki Burger"; P = 119; C = "Burgers"; T = "VEG"; I = "categories/burgers"; D = "Crisp aloo tikki, onion, tomato and mint mayo in a toasted bun" },
            @{ N = "Crispy Chicken Burger"; P = 179; C = "Burgers"; T = "NON_VEG"; I = "categories/burgers"; D = "Buttermilk fried chicken with lettuce and peri-peri sauce" },
            @{ N = "Paneer Tikka Wrap"; P = 159; C = "Rolls & Wraps"; T = "VEG"; I = "foods/paneer-tikka"; D = "Paneer tikka, pickled onion and mint chutney in a rumali wrap" },
            @{ N = "Chicken Kathi Roll"; P = 169; C = "Rolls & Wraps"; T = "NON_VEG"; I = "categories/north-indian"; D = "Kolkata-style egg paratha roll with chicken tikka" },
            @{ N = "Cold Coffee"; P = 99; C = "Beverages"; T = "VEG"; I = "categories/beverages"; D = "Blended coffee with milk and a scoop of vanilla" }) },
    @{ Key = "AT"; Name = "Annapurna Thali House"; Target = "ACTIVE"; Cuisines = @("North Indian", "Thali", "Rajasthani")
        Description = "Unlimited-style vegetarian thalis, sweets and kulfi"
        Owner = "Sunita Agarwal"; OwnerPhone = "+919845020107"; Phone = "+918041100107"
        Line1 = "Outer Ring Road, Marathahalli"; Pin = "560037"; Lat = 12.9569; Lon = 77.7011; Gst = "29AAKFA1207M1Z8"
        Dishes = @(
            @{ N = "Rajasthani Veg Thali"; P = 299; C = "Thalis & Meals"; T = "VEG"; I = "foods/veg-thali"; D = "Dal baati, gatte ki sabzi, kadhi, rice, two rotis and churma" },
            @{ N = "Veg Pulao"; P = 169; C = "Rice & Pulao"; T = "VEG"; I = "foods/veg-pulao"; D = "Basmati rice with vegetables and whole spices, served with raita" },
            @{ N = "Gulab Jamun (2 pcs)"; P = 69; C = "Indian Sweets"; T = "VEG"; I = "foods/gulab-jamun"; D = "Soft khoya dumplings in rose and cardamom syrup" },
            @{ N = "Malai Kulfi"; P = 89; C = "Kulfi & Ice Cream"; T = "VEG"; I = "foods/malai-kulfi"; D = "Slow-reduced milk kulfi with pistachio" }) },
    @{ Key = "TN"; Name = "Tandoor Nights"; Target = "SUSPENDED"; Cuisines = @("North Indian", "Kebab")
        Description = "Late-night kebabs and tandoori platters"
        SuspendReason = "Repeated hygiene complaints: suspended until the food safety inspection"
        Owner = "Imtiaz Ahmed"; OwnerPhone = "+919845020108"; Phone = "+918041100108"
        Line1 = "Hosur Road, Electronic City Phase 1"; Pin = "560100"; Lat = 12.8452; Lon = 77.6602; Gst = "29AAKFT1208M1Z7"
        Dishes = @(
            @{ N = "Mutton Seekh Kebab"; P = 299; C = "Kebabs & Tandoori"; T = "NON_VEG"; I = "foods/seekh-kebab"; D = "Minced mutton with herbs, grilled on skewers in the tandoor" },
            @{ N = "Tandoori Chicken (Half)"; P = 279; C = "Kebabs & Tandoori"; T = "NON_VEG"; I = "foods/seekh-kebab"; D = "Half chicken marinated in yoghurt and Kashmiri chilli, charred in the tandoor" },
            @{ N = "Hara Bhara Kebab"; P = 199; C = "Kebabs & Tandoori"; T = "VEG"; I = "foods/paneer-tikka"; D = "Spinach, peas and potato patties with mint chutney" },
            @{ N = "Butter Naan"; P = 49; C = "North Indian"; T = "VEG"; I = "categories/north-indian"; D = "Leavened tandoor bread brushed with butter" }) },
    @{ Key = "CK"; Name = "Coastal Karavali Kitchen"; Target = "PENDING"; Cuisines = @("Mangalorean", "Seafood")
        Description = "Ghee roast, neer dosa and fresh catch from the Karavali coast"
        Owner = "Sudhakar Shetty"; OwnerPhone = "+919845020109"; Phone = "+918041100109"
        Line1 = "Dr Rajkumar Road, Rajajinagar"; Pin = "560010"; Lat = 12.9915; Lon = 77.5525; Gst = "29AAKFC1209M1Z6"
        Dishes = @() },
    @{ Key = "FK"; Name = "Filter Kaapi Corner"; Target = "REJECTED"; Cuisines = @("Cafe", "South Indian")
        Description = "Kaapi, bun maska and quick bites near Bull Temple"
        RejectReason = "The FSSAI license document has expired: please upload a valid license and resubmit"
        Owner = "Lakshmi Narayan"; OwnerPhone = "+919845020110"; Phone = "+918041100110"
        Line1 = "Bull Temple Road, Basavanagudi"; Pin = "560004"; Lat = 12.9421; Lon = 77.5714; Gst = "29AAKFF1210M1Z5"
        Dishes = @() },
    @{ Key = "AB"; Name = "Andhra Biryani Bhavan"; Target = "ACTIVE"; Cuisines = @("Andhra", "Biryani", "South Indian")
        Description = "Fiery Andhra meals, biryanis and gongura specials"
        Owner = "Srinivas Reddy"; OwnerPhone = "+919845020111"; Phone = "+918041100111"
        Line1 = "24th Main Road, Banashankari 2nd Stage"; Pin = "560070"; Lat = 12.9255; Lon = 77.5468; Gst = "29AAKFA1211M1Z4"
        Dishes = @(
            @{ N = "Andhra Chicken Biryani"; P = 289; C = "Biryani"; T = "NON_VEG"; I = "foods/chicken-biryani"; D = "Spicy Andhra-style biryani with Guntur chillies and a boiled egg" },
            @{ N = "Gongura Mutton"; P = 349; C = "Mutton Specials"; T = "NON_VEG"; I = "categories/north-indian"; D = "Mutton cooked with tangy gongura leaves and red chillies" },
            @{ N = "Andhra Veg Meals"; P = 179; C = "Thalis & Meals"; T = "VEG"; I = "foods/veg-thali"; D = "Rice, pappu, rasam, sambar, two curries, pickle, curd and papad" },
            @{ N = "Chicken 65"; P = 219; C = "Starters"; T = "NON_VEG"; I = "foods/seekh-kebab"; D = "Deep-fried chicken bites tossed with curry leaves and green chillies" },
            @{ N = "Bobbatlu (2 pcs)"; P = 79; C = "Indian Sweets"; T = "VEG"; I = "categories/desserts"; D = "Sweet flatbread stuffed with chana dal and jaggery" }) },
    @{ Key = "MM"; Name = "Mysuru Mess & Tiffins"; Target = "ACTIVE"; Cuisines = @("South Indian", "Karnataka")
        Description = "Thatte idli, Mysore masala dosa and bisi bele bath, made fresh every morning"
        Owner = "Manjula Hegde"; OwnerPhone = "+919845020112"; Phone = "+918041100112"
        Line1 = "Service Road, Vijayanagar"; Pin = "560040"; Lat = 12.9710; Lon = 77.5370; Gst = "29AAKFM1212M1Z3"
        Dishes = @(
            @{ N = "Mysore Masala Dosa"; P = 119; C = "Dosa Specials"; T = "VEG"; I = "foods/masala-dosa"; D = "Dosa smeared with red garlic chutney, filled with potato palya" },
            @{ N = "Thatte Idli"; P = 69; C = "Breakfast & Tiffin"; T = "VEG"; I = "foods/idli"; D = "One plate-sized idli with butter, chutney and sambar" },
            @{ N = "Bisi Bele Bath"; P = 109; C = "Rice & Pulao"; T = "VEG"; I = "categories/south-indian"; D = "Rice, lentils and vegetables with spice powder and ghee, served with boondi" },
            @{ N = "Mysore Pak"; P = 59; C = "Indian Sweets"; T = "VEG"; I = "categories/desserts"; D = "Ghee-rich gram flour fudge, the Mysuru classic" },
            @{ N = "Badam Milk"; P = 69; C = "Beverages"; T = "VEG"; I = "categories/beverages"; D = "Warm milk with almonds, saffron and cardamom" }) },
    @{ Key = "KR"; Name = "Kolkata Kathi Rolls"; Target = "ACTIVE"; Cuisines = @("Rolls", "Bengali", "Street Food")
        Description = "Flaky paratha rolls the Park Street way"
        Owner = "Subhash Banerjee"; OwnerPhone = "+919845020113"; Phone = "+918041100113"
        Line1 = "Mosque Road, Frazer Town"; Pin = "560005"; Lat = 12.9988; Lon = 77.6143; Gst = "29AAKFK1213M1Z2"
        Dishes = @(
            @{ N = "Double Egg Roll"; P = 99; C = "Rolls & Wraps"; T = "EGG"; I = "categories/north-indian"; D = "Paratha layered with two eggs, onion, chilli and lime" },
            @{ N = "Chicken Tikka Kathi Roll"; P = 159; C = "Rolls & Wraps"; T = "NON_VEG"; I = "categories/north-indian"; D = "Chargrilled chicken tikka with onions and green chutney" },
            @{ N = "Paneer Kathi Roll"; P = 139; C = "Rolls & Wraps"; T = "VEG"; I = "foods/paneer-tikka"; D = "Paneer tikka, capsicum and onion in a flaky paratha" },
            @{ N = "Mishti Doi"; P = 59; C = "Indian Sweets"; T = "VEG"; I = "categories/desserts"; D = "Sweet set curd in a clay pot" }) },
    @{ Key = "ML"; Name = "Momo Lane"; Target = "ACTIVE"; Cuisines = @("Tibetan", "Chinese")
        Description = "Steamed momos, thukpa and Himalayan comfort food"
        Owner = "Pema Lhamo"; OwnerPhone = "+919845020114"; Phone = "+918041100114"
        Line1 = "Kammanahalli Main Road, Kammanahalli"; Pin = "560084"; Lat = 13.0159; Lon = 77.6378; Gst = "29AAKFM1214M1Z1"
        Dishes = @(
            @{ N = "Veg Steamed Momos"; P = 119; C = "Chinese"; T = "VEG"; I = "categories/chinese"; D = "Eight cabbage and carrot momos with fiery tomato chutney" },
            @{ N = "Chicken Steamed Momos"; P = 149; C = "Chinese"; T = "NON_VEG"; I = "categories/chinese"; D = "Eight juicy chicken momos with a sesame-chilli dip" },
            @{ N = "Chicken Thukpa"; P = 179; C = "Chinese"; T = "NON_VEG"; I = "categories/chinese"; D = "Tibetan noodle soup with chicken and vegetables" },
            @{ N = "Chilli Garlic Noodles"; P = 159; C = "Chinese"; T = "VEG"; I = "categories/chinese"; D = "Hakka noodles tossed with burnt garlic and red chilli" }) },
    @{ Key = "LP"; Name = "La Piazza Woodfired"; Target = "ACTIVE"; Cuisines = @("Pizza", "Italian")
        Description = "Neapolitan-style pizzas from a wood-fired oven"
        Owner = "Marco Fernandes"; OwnerPhone = "+919845020115"; Phone = "+918041100115"
        Line1 = "15th Cross, JP Nagar 6th Phase"; Pin = "560078"; Lat = 12.9063; Lon = 77.5857; Gst = "29AAKFL1215M1Z9"
        Dishes = @(
            @{ N = "Four Cheese Pizza"; P = 399; C = "Pizza"; T = "VEG"; I = "categories/pizza"; D = "Mozzarella, cheddar, parmesan and gorgonzola on a thin crust" },
            @{ N = "Chicken Pepperoni Pizza"; P = 449; C = "Pizza"; T = "NON_VEG"; I = "categories/pizza"; D = "Chicken pepperoni, mozzarella and oregano" },
            @{ N = "Paneer Makhani Pizza"; P = 379; C = "Pizza"; T = "VEG"; I = "categories/pizza"; D = "Makhani sauce, paneer tikka, onion and coriander" },
            @{ N = "Tiramisu Cup"; P = 179; C = "Desserts"; T = "VEG"; I = "categories/desserts"; D = "Eggless coffee-soaked sponge with mascarpone cream" }) },
    @{ Key = "SB"; Name = "Smash Burger Bros"; Target = "ACTIVE"; Cuisines = @("Burgers", "American", "Fast Food")
        Description = "Smashed patties, crispy fries and thick shakes"
        Owner = "Aditya Malhotra"; OwnerPhone = "+919845020116"; Phone = "+918041100116"
        Line1 = "Sarjapur Outer Ring Road, Bellandur"; Pin = "560103"; Lat = 12.9260; Lon = 77.6762; Gst = "29AAKFS1216M1Z8"
        Dishes = @(
            @{ N = "Double Smash Chicken Burger"; P = 249; C = "Burgers"; T = "NON_VEG"; I = "categories/burgers"; D = "Two smashed chicken patties, cheese, pickles and house sauce" },
            @{ N = "Veggie Smash Burger"; P = 199; C = "Burgers"; T = "VEG"; I = "categories/burgers"; D = "Crisp veg patty, cheddar, caramelised onion and mustard mayo" },
            @{ N = "Peri Peri Fries"; P = 129; C = "Starters"; T = "VEG"; I = "categories/burgers"; D = "Skin-on fries tossed in peri-peri seasoning" },
            @{ N = "Oreo Thick Shake"; P = 159; C = "Beverages"; T = "VEG"; I = "categories/beverages"; D = "Vanilla ice cream blended with Oreo cookies" }) },
    @{ Key = "MC"; Name = "Malabar Coast Kitchen"; Target = "ACTIVE"; Cuisines = @("Kerala", "Seafood")
        Description = "Malabar biryani, appam and coastal curries from Kerala"
        Owner = "Thomas Kurian"; OwnerPhone = "+919845020117"; Phone = "+918041100117"
        Line1 = "HRBR Layout 1st Block, Kalyan Nagar"; Pin = "560043"; Lat = 13.0221; Lon = 77.6403; Gst = "29AAKFM1217M1Z7"
        Dishes = @(
            @{ N = "Malabar Chicken Biryani"; P = 289; C = "Biryani"; T = "NON_VEG"; I = "foods/chicken-biryani"; D = "Short-grain kaima rice with chicken, ghee and fried onions" },
            @{ N = "Appam with Veg Stew"; P = 149; C = "South Indian"; T = "VEG"; I = "categories/south-indian"; D = "Two lacy appams with coconut milk vegetable stew" },
            @{ N = "Kerala Fish Curry Meals"; P = 249; C = "Thalis & Meals"; T = "NON_VEG"; I = "categories/south-indian"; D = "Red rice, Kerala fish curry, thoran, pickle and papadum" },
            @{ N = "Kerala Parotta (2 pcs)"; P = 59; C = "South Indian"; T = "VEG"; I = "categories/south-indian"; D = "Flaky layered parottas" },
            @{ N = "Ada Pradhaman"; P = 89; C = "Indian Sweets"; T = "VEG"; I = "categories/desserts"; D = "Rice ada payasam with jaggery and coconut milk" }) },
    @{ Key = "RS"; Name = "Royal Shahi Darbar"; Target = "ACTIVE"; Cuisines = @("Mughlai", "North Indian", "Kebab")
        Description = "Kebabs, rogan josh and biryani from the old Mughlai kitchens"
        Owner = "Saleem Khan"; OwnerPhone = "+919845020118"; Phone = "+918041100118"
        Line1 = "Broadway Road, Shivajinagar"; Pin = "560051"; Lat = 12.9857; Lon = 77.6057; Gst = "29AAKFR1218M1Z6"
        Dishes = @(
            @{ N = "Chicken Seekh Kebab"; P = 269; C = "Kebabs & Tandoori"; T = "NON_VEG"; I = "foods/seekh-kebab"; D = "Minced chicken skewers with mint and spices, from the tandoor" },
            @{ N = "Mutton Rogan Josh"; P = 379; C = "Mutton Specials"; T = "NON_VEG"; I = "categories/north-indian"; D = "Kashmiri-style mutton curry with ratan jot and fennel" },
            @{ N = "Chicken Dum Biryani"; P = 299; C = "Biryani"; T = "NON_VEG"; I = "foods/chicken-biryani"; D = "Chicken and basmati sealed in a handi with saffron and mint" },
            @{ N = "Rumali Roti"; P = 35; C = "North Indian"; T = "VEG"; I = "categories/north-indian"; D = "Paper-thin handkerchief bread" },
            @{ N = "Shahi Tukda"; P = 119; C = "Indian Sweets"; T = "VEG"; I = "categories/desserts"; D = "Fried bread soaked in saffron rabri with pistachio" }) },
    @{ Key = "GS"; Name = "Gujarati Swad Thali"; Target = "ACTIVE"; Cuisines = @("Gujarati", "Thali")
        Description = "Sweet, sour and spicy Gujarati thalis with farsan"
        Owner = "Hetal Shah"; OwnerPhone = "+919845020119"; Phone = "+918041100119"
        Line1 = "8th Main Road, Basaveshwaranagar"; Pin = "560079"; Lat = 12.9886; Lon = 77.5398; Gst = "29AAKFG1219M1Z5"
        Dishes = @(
            @{ N = "Gujarati Thali"; P = 279; C = "Thalis & Meals"; T = "VEG"; I = "foods/veg-thali"; D = "Dal, kadhi, two shaaks, rotli, rice, farsan and a sweet" },
            @{ N = "Khaman Dhokla"; P = 89; C = "Starters"; T = "VEG"; I = "categories/north-indian"; D = "Steamed gram flour cakes tempered with mustard and curry leaves" },
            @{ N = "Shrikhand"; P = 89; C = "Indian Sweets"; T = "VEG"; I = "categories/desserts"; D = "Strained yoghurt with saffron and cardamom" },
            @{ N = "Masala Chaas"; P = 49; C = "Beverages"; T = "VEG"; I = "categories/beverages"; D = "Spiced buttermilk with roasted cumin and coriander" }) },
    @{ Key = "CC"; Name = "Chai & Chakna Cafe"; Target = "ACTIVE"; Cuisines = @("Cafe", "Snacks", "Street Food")
        Description = "Cutting chai, bun maska and evening snacks"
        Owner = "Kavya Murthy"; OwnerPhone = "+919845020120"; Phone = "+918041100120"
        Line1 = "Sankey Road, Sadashivanagar"; Pin = "560080"; Lat = 13.0068; Lon = 77.5813; Gst = "29AAKFC1220M1Z4"
        Dishes = @(
            @{ N = "Masala Chai"; P = 35; C = "Beverages"; T = "VEG"; I = "categories/beverages"; D = "Cutting chai brewed with ginger and cardamom" },
            @{ N = "Bun Maska"; P = 49; C = "Breakfast & Tiffin"; T = "VEG"; I = "categories/beverages"; D = "Soft bun with a thick layer of salted butter" },
            @{ N = "Vada Pav (2 pcs)"; P = 79; C = "Starters"; T = "VEG"; I = "categories/burgers"; D = "Spiced potato fritters in pav with garlic and green chutney" },
            @{ N = "Paneer Tikka Sandwich"; P = 129; C = "Starters"; T = "VEG"; I = "foods/paneer-tikka"; D = "Grilled sandwich with paneer tikka, onion and mint mayo" },
            @{ N = "Egg Bhurji Pav"; P = 99; C = "Breakfast & Tiffin"; T = "EGG"; I = "categories/north-indian"; D = "Masala scrambled eggs with buttered pav" }) },
    @{ Key = "MI"; Name = "Mithai Mahal Sweets"; Target = "ACTIVE"; Cuisines = @("Sweets", "Desserts")
        Description = "Fresh mithai, kulfi and falooda since 1987"
        Owner = "Ramesh Chaurasia"; OwnerPhone = "+919845020121"; Phone = "+918041100121"
        Line1 = "Avenue Road, Chickpet"; Pin = "560053"; Lat = 12.9700; Lon = 77.5775; Gst = "29AAKFM1221M1Z3"
        Dishes = @(
            @{ N = "Gulab Jamun Tub (6 pcs)"; P = 149; C = "Indian Sweets"; T = "VEG"; I = "foods/gulab-jamun"; D = "Six warm gulab jamuns in cardamom syrup" },
            @{ N = "Rasmalai (2 pcs)"; P = 99; C = "Indian Sweets"; T = "VEG"; I = "categories/desserts"; D = "Soft chenna discs in saffron milk" },
            @{ N = "Kesar Pista Kulfi"; P = 79; C = "Kulfi & Ice Cream"; T = "VEG"; I = "foods/malai-kulfi"; D = "Saffron and pistachio kulfi on a stick" },
            @{ N = "Royal Falooda"; P = 149; C = "Kulfi & Ice Cream"; T = "VEG"; I = "categories/desserts"; D = "Rose milk, vermicelli, basil seeds, jelly and a scoop of kulfi" },
            @{ N = "Kaju Katli (250 g)"; P = 299; C = "Indian Sweets"; T = "VEG"; I = "categories/desserts"; D = "Cashew fudge with silver varq" }) },
    @{ Key = "HW"; Name = "Hot Wok Midnight"; Target = "SUSPENDED"; Cuisines = @("Chinese", "Asian")
        Description = "Late-night Indo-Chinese for night owls"
        SuspendReason = "Customer complaints about undercooked food: suspended until the kitchen audit"
        Owner = "Kevin Chang"; OwnerPhone = "+919845020122"; Phone = "+918041100122"
        Line1 = "Sector 4, Yelahanka New Town"; Pin = "560064"; Lat = 13.1007; Lon = 77.5963; Gst = "29AAKFH1222M1Z2"
        Dishes = @(
            @{ N = "Schezwan Fried Rice"; P = 169; C = "Chinese"; T = "VEG"; I = "categories/chinese"; D = "Fried rice tossed in house Schezwan sauce" },
            @{ N = "Dragon Chicken"; P = 239; C = "Chinese"; T = "NON_VEG"; I = "categories/chinese"; D = "Crispy chicken strips in a sweet and spicy dragon sauce" },
            @{ N = "Veg Spring Rolls"; P = 139; C = "Starters"; T = "VEG"; I = "categories/chinese"; D = "Four crisp rolls stuffed with vegetables and glass noodles" },
            @{ N = "American Chopsuey"; P = 199; C = "Chinese"; T = "EGG"; I = "categories/chinese"; D = "Crispy noodles in sweet and sour sauce, topped with a fried egg" }) },
    @{ Key = "KF"; Name = "Konkan Fish Thali"; Target = "PENDING"; Cuisines = @("Konkani", "Seafood")
        Description = "Coastal fish thalis, sol kadhi and rava fry"
        Owner = "Prabhakar Kamath"; OwnerPhone = "+919845020123"; Phone = "+918041100123"
        Line1 = "Sampige Road, Malleshwaram"; Pin = "560003"; Lat = 13.0035; Lon = 77.5710; Gst = "29AAKFK1223M1Z1"
        Dishes = @() },
    @{ Key = "AP"; Name = "Amritsar Paratha House"; Target = "PENDING"; Cuisines = @("North Indian", "Punjabi")
        Description = "Stuffed parathas with white butter, chole and lassi"
        Owner = "Gurmeet Kaur"; OwnerPhone = "+919845020124"; Phone = "+918041100124"
        Line1 = "Bellary Road, Hebbal"; Pin = "560024"; Lat = 13.0358; Lon = 77.5970; Gst = "29AAKFA1224M1Z9"
        Dishes = @() },
    @{ Key = "SP"; Name = "Shawarma Point Express"; Target = "REJECTED"; Cuisines = @("Arabian", "Lebanese")
        Description = "Shawarma rolls, platters and falafel till late"
        RejectReason = "The GST number does not match the legal business name: please correct it and resubmit"
        Owner = "Abdul Rahman"; OwnerPhone = "+919845020125"; Phone = "+918041100125"
        Line1 = "CBI Road, RT Nagar"; Pin = "560032"; Lat = 13.0213; Lon = 77.5946; Gst = "29AAKFS1225M1Z8"
        Dishes = @() },
    # The test owner's restaurant ($ManualOwner): the first 4 dishes were made by hand (kept as they are, created only
    # when missing), the others are added
    @{ Key = "QA"; Name = "QA Test Kitchen"; Manual = $true; Target = "ACTIVE"; Cuisines = @("North Indian", "Biryani")
        Description = "The test kitchen of app-credential.txt"
        Owner = "Test Owner"; OwnerPhone = "+919876500003"; Phone = "+919876500001"
        Line1 = "100 Feet Road, Indiranagar"; Pin = "560038"; Lat = 12.9784; Lon = 77.6408; Gst = "29AAKFQ1226M1Z7"
        Dishes = @(
            @{ N = "Chicken Biryani"; P = 299; C = "Biryani"; T = "NON_VEG"; I = "foods/chicken-biryani"; D = "Chicken dum biryani with raita" },
            @{ N = "Masala Dosa"; P = 129; C = "South Indian"; T = "VEG"; I = "foods/masala-dosa"; D = "Crisp dosa with potato palya, chutney and sambar" },
            @{ N = "Paneer Butter Masala"; P = 249; C = "North Indian"; T = "VEG"; I = "categories/north-indian"; D = "Paneer in a buttery tomato gravy" },
            @{ N = "Gulab Jamun"; P = 89; C = "Desserts"; T = "VEG"; I = "foods/gulab-jamun"; D = "Khoya dumplings in cardamom syrup" },
            @{ N = "Mutton Biryani"; P = 379; C = "Biryani"; T = "NON_VEG"; I = "foods/mutton-biryani"; D = "Slow-cooked mutton dum biryani with mirchi ka salan" },
            @{ N = "Paneer Tikka"; P = 239; C = "Starters"; T = "VEG"; I = "foods/paneer-tikka"; D = "Chargrilled paneer, peppers and onion" },
            @{ N = "Mutton Seekh Kebab"; P = 289; C = "Kebabs & Tandoori"; T = "NON_VEG"; I = "foods/seekh-kebab"; D = "Minced mutton skewers from the tandoor, with mint chutney" },
            @{ N = "Veg Thali"; P = 219; C = "Thalis & Meals"; T = "VEG"; I = "foods/veg-thali"; D = "Dal, two sabzis, rice, rotis, salad and a sweet" },
            @{ N = "Malai Kulfi"; P = 79; C = "Kulfi & Ice Cream"; T = "VEG"; I = "foods/malai-kulfi"; D = "Slow-reduced milk kulfi with pistachio" },
            @{ N = "Sweet Lassi"; P = 69; C = "Beverages"; T = "VEG"; I = "categories/beverages"; D = "Thick churned curd with a layer of malai" }) }
)

$Customers = @(
    @{ Name = "Aarav Sharma"; Phone = "+919845010101"; Type = "HOME"; Line1 = "14, 5th Cross, 4th Block, Koramangala"; Pin = "560034"; Lat = 12.9339; Lon = 77.6295 },
    @{ Name = "Ananya Iyer"; Phone = "+919845010102"; Type = "HOME"; Line1 = "221, 30th Cross, 4th Block, Jayanagar"; Pin = "560011"; Lat = 12.9279; Lon = 77.5856 },
    @{ Name = "Rohan Gupta"; Phone = "+919845010103"; Type = "HOME"; Line1 = "Flat 302, Sai Residency, Sector 2, HSR Layout"; Pin = "560102"; Lat = 12.9105; Lon = 77.6450 },
    @{ Name = "Priya Nair"; Phone = "+919845010104"; Type = "WORK"; Line1 = "3rd Floor, 100 Feet Road, Indiranagar"; Pin = "560038"; Lat = 12.9784; Lon = 77.6408 },
    @{ Name = "Vikram Reddy"; Phone = "+919845010105"; Type = "HOME"; Line1 = "B-1104, Prestige Shantiniketan, Whitefield"; Pin = "560048"; Lat = 12.9893; Lon = 77.7280 },
    @{ Name = "Sneha Kulkarni"; Phone = "+919845010106"; Type = "HOME"; Line1 = "42, 8th Cross, Malleshwaram"; Pin = "560003"; Lat = 13.0031; Lon = 77.5643 },
    @{ Name = "Karthik Rao"; Phone = "+919845010107"; Type = "HOME"; Line1 = "17, 7th Main, BTM Layout 2nd Stage"; Pin = "560076"; Lat = 12.9152; Lon = 77.6101 },
    @{ Name = "Divya Menon"; Phone = "+919845010108"; Type = "WORK"; Line1 = "Tower C, Embassy Tech Village, Marathahalli"; Pin = "560103"; Lat = 12.9346; Lon = 77.6928 },
    @{ Name = "Arjun Patel"; Phone = "+919845010109"; Type = "HOME"; Line1 = "56, Neeladri Road, Electronic City Phase 1"; Pin = "560100"; Lat = 12.8458; Lon = 77.6692 },
    @{ Name = "Neha Joshi"; Phone = "+919845010110"; Type = "HOME"; Line1 = "9, Gandhi Bazaar Main Road, Basavanagudi"; Pin = "560004"; Lat = 12.9447; Lon = 77.5730 },
    @{ Name = "Ishaan Verma"; Phone = "+919845010111"; Type = "HOME"; Line1 = "88, 27th Cross, Banashankari 3rd Stage"; Pin = "560085"; Lat = 12.9180; Lon = 77.5530 },
    @{ Name = "Meera Krishnan"; Phone = "+919845010112"; Type = "HOME"; Line1 = "31, 4th Main, RPC Layout, Vijayanagar"; Pin = "560040"; Lat = 12.9667; Lon = 77.5330 },
    @{ Name = "Aditi Deshpande"; Phone = "+919845010113"; Type = "HOME"; Line1 = "12, Wheeler Road, Cooke Town"; Pin = "560005"; Lat = 13.0005; Lon = 77.6230 },
    @{ Name = "Siddharth Kapoor"; Phone = "+919845010114"; Type = "WORK"; Line1 = "Block G2, Manyata Tech Park, Nagavara"; Pin = "560045"; Lat = 13.0475; Lon = 77.6200 },
    @{ Name = "Pooja Shenoy"; Phone = "+919845010115"; Type = "HOME"; Line1 = "507, 24th Main, JP Nagar 7th Phase"; Pin = "560078"; Lat = 12.8990; Lon = 77.5850 },
    @{ Name = "Varun Saxena"; Phone = "+919845010116"; Type = "HOME"; Line1 = "Flat 1203, Lakeview Towers, Bellandur"; Pin = "560103"; Lat = 12.9300; Lon = 77.6780 },
    @{ Name = "Fathima Zahra"; Phone = "+919845010117"; Type = "HOME"; Line1 = "6, Coles Road, Frazer Town"; Pin = "560005"; Lat = 12.9960; Lon = 77.6120 },
    @{ Name = "Nikhita Shetty"; Phone = "+919845010118"; Type = "HOME"; Line1 = "45, 2nd Main, HRBR Layout 2nd Block, Kalyan Nagar"; Pin = "560043"; Lat = 13.0240; Lon = 77.6420 },
    @{ Name = "Abhishek Tiwari"; Phone = "+919845010119"; Type = "WORK"; Line1 = "5th Floor, Cunningham Road"; Pin = "560052"; Lat = 12.9880; Lon = 77.5940 },
    @{ Name = "Riya Chatterjee"; Phone = "+919845010120"; Type = "HOME"; Line1 = "73, West of Chord Road, Rajajinagar"; Pin = "560010"; Lat = 12.9960; Lon = 77.5520 },
    @{ Name = "Harsha Vardhan"; Phone = "+919845010121"; Type = "HOME"; Line1 = "19, Sector A, Yelahanka New Town"; Pin = "560064"; Lat = 13.1000; Lon = 77.5900 },
    @{ Name = "Tanvi Mehta"; Phone = "+919845010122"; Type = "HOME"; Line1 = "210, RMV 2nd Stage, Dollars Colony"; Pin = "560094"; Lat = 13.0350; Lon = 77.5840 },
    @{ Name = "Kiran Gowda"; Phone = "+919845010123"; Type = "HOME"; Line1 = "58, Kempapura Main Road, Hebbal"; Pin = "560024"; Lat = 13.0440; Lon = 77.5960 },
    @{ Name = "Zoya Siddiqui"; Phone = "+919845010124"; Type = "WORK"; Line1 = "2nd Floor, Residency Road, Richmond Town"; Pin = "560025"; Lat = 12.9650; Lon = 77.6040 },
    @{ Name = "Manoj Pillai"; Phone = "+919845010125"; Type = "HOME"; Line1 = "102, 7th Cross, Sahakar Nagar"; Pin = "560092"; Lat = 13.0620; Lon = 77.5880 },
    # The test customer ($ManualCustomer), with its address made by hand
    @{ Name = "Manual Customer"; Manual = $true; Phone = "+919876500002"; Type = "HOME"; Line1 = "24 MG Road"; Pin = "560001"; Lat = 12.9756; Lon = 77.6050 }
)

$Riders = @(
    @{ Name = "Ravi Kumar"; Phone = "+919900110101"; Vehicle = "MOTORCYCLE"; Number = "KA01EX4521" },
    @{ Name = "Suresh Gowda"; Phone = "+919900110102"; Vehicle = "SCOOTER"; Number = "KA05HM7783" },
    @{ Name = "Imran Pasha"; Phone = "+919900110103"; Vehicle = "MOTORCYCLE"; Number = "KA03JK1290" },
    @{ Name = "Manjunath Shetty"; Phone = "+919900110104"; Vehicle = "SCOOTER"; Number = "KA51PQ3344" },
    @{ Name = "Prakash Naik"; Phone = "+919900110105"; Vehicle = "MOTORCYCLE"; Number = "KA02LM5567" },
    @{ Name = "Lokesh Rao"; Phone = "+919900110106"; Vehicle = "SCOOTER"; Number = "KA04ZN8810" },
    @{ Name = "Naveen Kumar"; Phone = "+919900110107"; Vehicle = "MOTORCYCLE"; Number = "KA01HJ2231" },
    @{ Name = "Santosh Patil"; Phone = "+919900110108"; Vehicle = "SCOOTER"; Number = "KA02MN4410" },
    @{ Name = "Abdul Khader"; Phone = "+919900110109"; Vehicle = "MOTORCYCLE"; Number = "KA03KL7765" },
    @{ Name = "Girish Hegde"; Phone = "+919900110110"; Vehicle = "SCOOTER"; Number = "KA04MP1198" },
    @{ Name = "Mahesh Poojary"; Phone = "+919900110111"; Vehicle = "MOTORCYCLE"; Number = "KA05NB3321" },
    @{ Name = "Chandan Raj"; Phone = "+919900110112"; Vehicle = "SCOOTER"; Number = "KA41EF5509" },
    @{ Name = "Arif Hussain"; Phone = "+919900110113"; Vehicle = "MOTORCYCLE"; Number = "KA51GH6620" },
    @{ Name = "Darshan Naidu"; Phone = "+919900110114"; Vehicle = "MOTORCYCLE"; Number = "KA53JK2784" },
    @{ Name = "Vinay Acharya"; Phone = "+919900110115"; Vehicle = "SCOOTER"; Number = "KA01RS9043" },
    @{ Name = "Yusuf Sheikh"; Phone = "+919900110116"; Vehicle = "MOTORCYCLE"; Number = "KA03TU1157" },
    @{ Name = "Ganesh Pai"; Phone = "+919900110117"; Vehicle = "SCOOTER"; Number = "KA04VW3368" },
    @{ Name = "Anand Raju"; Phone = "+919900110118"; Vehicle = "MOTORCYCLE"; Number = "KA05XY4479" },
    @{ Name = "Bharath Kumar"; Phone = "+919900110119"; Vehicle = "SCOOTER"; Number = "KA02AB5580" },
    @{ Name = "Joseph Mathew"; Phone = "+919900110120"; Vehicle = "MOTORCYCLE"; Number = "KA50CD6691" },
    @{ Name = "Ramya Srinivas"; Phone = "+919900110121"; Vehicle = "SCOOTER"; Number = "KA01EF7702" }
)

# Calendar days in the business time zone (Asia/Kolkata)
$today = [System.TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTime]::UtcNow, "India Standard Time").Date
$monthStart = New-Object DateTime $today.Year, $today.Month, 1
$monthEnd = $monthStart.AddMonths(1).AddDays(-1)
$previousMonthStart = $monthStart.AddMonths(-1)
$nextMonthStart = $monthStart.AddMonths(1)
$nextMonthEnd = $monthStart.AddMonths(2).AddDays(-1)

# Restaurant: key of a restaurant above (restaurant-specific), else every restaurant. Paused: deactivated.
$Coupons = @(
    @{ Code = "WELCOME150"; Description = "Flat 150 off your first order of 499 or more"; Type = "FIXED"; Value = 150; Min = 499; From = $today.AddDays(-14); Until = $today.AddDays(75); Limit = 1000; PerCustomer = 1 },
    @{ Code = "FEAST20"; Description = "20% off up to 120 on orders above 299, all month"; Type = "PERCENTAGE"; Value = 20; Max = 120; Min = 299; From = $monthStart; Until = $monthEnd; Limit = 5000; PerCustomer = 3 },
    @{ Code = "WEEKEND50"; Description = "Flat 50 off on orders above 249"; Type = "FIXED"; Value = 50; Min = 249; From = $today; Until = $today.AddDays(27); Limit = 2000; PerCustomer = 4 },
    @{ Code = "BIRYANI25"; Restaurant = "BD"; Description = "25% off up to 100 at Biryani Darbar"; Type = "PERCENTAGE"; Value = 25; Max = 100; Min = 300; From = $today.AddDays(-7); Until = $today.AddDays(21); Limit = 500; PerCustomer = 2 },
    @{ Code = "UDUPI15"; Restaurant = "US"; Description = "15% off up to 60 on Udupi Sagar tiffins"; Type = "PERCENTAGE"; Value = 15; Max = 60; Min = 150; From = $today; Until = $today.AddDays(45); Limit = 800; PerCustomer = 5 },
    @{ Code = "PIZZA99"; Restaurant = "NS"; Description = "Flat 99 off pizzas at Napoli Slice above 399"; Type = "FIXED"; Value = 99; Min = 399; From = $today.AddDays(-2); Until = $today.AddDays(12); Limit = 300; PerCustomer = 2 },
    @{ Code = "FLASH40"; Description = "Flash sale: 40% off up to 80"; Type = "PERCENTAGE"; Value = 40; Max = 80; Min = 199; From = $today; Until = $today.AddDays(14); Limit = 200; PerCustomer = 1; Paused = "Campaign paused: the flash sale budget is used up" },
    @{ Code = "BIGSALE40"; Description = "Festival sale: flat 40 off above 199"; Type = "FIXED"; Value = 40; Min = 199; From = $today.AddDays(-20); Until = $today.AddDays(-12); Limit = 3000; PerCustomer = 2 },
    @{ Code = "MONSOON60"; Restaurant = "DW"; Description = "Monsoon special: flat 60 off at Dragon Wok above 349"; Type = "FIXED"; Value = 60; Min = 349; From = $previousMonthStart; Until = $monthStart.AddDays(-1); Limit = 400; PerCustomer = 2 },
    @{ Code = "FESTIVE100"; Description = "Festive week: flat 100 off orders above 599"; Type = "FIXED"; Value = 100; Min = 599; From = $today.AddDays(5); Until = $today.AddDays(9); Limit = 5000; PerCustomer = 1 },
    @{ Code = "NEWUSER75"; Restaurant = "BJ"; Description = "Flat 75 off at Burger Junction above 299"; Type = "FIXED"; Value = 75; Min = 299; From = $nextMonthStart; Until = $nextMonthStart.AddDays(14); Limit = 600; PerCustomer = 1 },
    @{ Code = "YEAREND25"; Description = "Year-end party: 25% off up to 200 above 499"; Type = "PERCENTAGE"; Value = 25; Max = 200; Min = 499; From = $nextMonthStart.AddDays(19); Until = $nextMonthEnd; Limit = 10000; PerCustomer = 2 }
)

# The orders. C: customer (1-26), R: restaurant key, Items: (dish, quantity), Pay: CASH_ON_DELIVERY, UPI, CARD,
# WALLET. Target: DELIVERED, OUT_FOR_DELIVERY, READY_FOR_PICKUP, PREPARING, ACCEPTED, PLACED, PAYMENT_PENDING,
# CANCELLED (by the customer, before the restaurant accepts), REJECTED (by the restaurant), ADMIN_CANCELLED (while
# preparing). Refunds: admin refunds after delivery (Amount $null = everything left). Upi "refundfail@upi" pays but
# its refunds fail (simulated gateway). DeclineFirst: a declined card, then a good one.
$Orders = @(
    @{ N = 1; C = 1; R = "BD"; Items = @(, @("Hyderabadi Chicken Dum Biryani", 2)) + @(, @("Double Ka Meetha", 1)); Pay = "UPI"; Coupon = "WELCOME150"; Target = "DELIVERED"; Notes = "Extra raita please"
        Review = @{ Rating = 5; Tags = @("DELICIOUS", "GOOD_PACKAGING"); Comment = "Best biryani in Koramangala. Rice was perfectly cooked and the chicken was juicy." } },
    @{ N = 2; C = 2; R = "US"; Items = @(, @("Masala Dosa", 2)) + @(, @("Filter Coffee", 2)); Pay = "CASH_ON_DELIVERY"; Target = "DELIVERED"
        Review = @{ Rating = 4; Tags = @("GOOD_VALUE", "FAST_DELIVERY"); Comment = "Crisp dosas and strong coffee. Chutney could have been more." } },
    @{ N = 3; C = 3; R = "DW"; Items = @(, @("Veg Hakka Noodles", 1)) + @(, @("Chicken Manchurian Dry", 1)); Pay = "CARD"; Target = "DELIVERED"
        Refunds = @(@{ Amount = 60; Reason = "Manchurian portion was smaller than usual: partial refund" })
        Review = @{ Rating = 3; Tags = @("FAST_DELIVERY"); Comment = "Noodles were good but the Manchurian portion was small." } },
    @{ N = 4; C = 4; R = "PD"; Items = @(, @("Butter Chicken", 1)) + @(, @("Amritsari Kulcha", 2)) + @(, @("Sweet Lassi", 1)); Pay = "WALLET"; Coupon = "FEAST20"; Target = "DELIVERED"; Notes = "Less spicy, please ring the bell"
        Refunds = @(@{ Amount = 100; Reason = "Kulchas arrived cold and soggy" })
        Review = @{ Rating = 4; Tags = @("DELICIOUS"); Comment = "Butter chicken was rich and creamy. Kulchas were cold on arrival." } },
    @{ N = 5; C = 5; R = "NS"; Items = @(, @("Farmhouse Veg Pizza", 1)) + @(, @("Garlic Breadsticks", 1)); Pay = "UPI"; Coupon = "PIZZA99"; Target = "DELIVERED"
        Review = @{ Rating = 5; Tags = @("EXCELLENT", "GOOD_VALUE"); Comment = "Thin crust, generous toppings and still hot when it arrived." } },
    @{ N = 6; C = 6; R = "AT"; Items = @(, @("Rajasthani Veg Thali", 2)) + @(, @("Malai Kulfi", 2)); Pay = "CARD"; Target = "DELIVERED"
        Refunds = @(@{ Amount = $null; Reason = "Food quality complaint: the dal had gone sour. Full refund approved" })
        Review = @{ Rating = 2; Tags = @("GOOD_PACKAGING"); Comment = "Packaging was neat but the dal tasted stale. Support refunded the order." } },
    @{ N = 7; C = 7; R = "BJ"; Items = @(, @("Crispy Chicken Burger", 2)) + @(, @("Cold Coffee", 1)); Pay = "UPI"; Target = "DELIVERED"
        Refunds = @(@{ Amount = 50; Reason = "Delivered 25 minutes late: goodwill credit" }) },
    @{ N = 8; C = 8; R = "BD"; Items = @(, @("Mutton Dum Biryani", 1)) + @(, @("Paneer Dum Biryani", 1)); Pay = "WALLET"; Coupon = "BIRYANI25"; Target = "DELIVERED"
        Review = @{ Rating = 5; Tags = @("DELICIOUS", "EXCELLENT", "GOOD_PACKAGING"); Comment = "Mutton was fall-off-the-bone tender. Will order again." } },
    @{ N = 9; C = 9; R = "TN"; Items = @(, @("Mutton Seekh Kebab", 1)) + @(, @("Tandoori Chicken (Half)", 1)) + @(, @("Butter Naan", 2)); Pay = "UPI"; Target = "DELIVERED"
        Refunds = @(@{ Amount = 49; Reason = "One butter naan missing from the order" }) },
    @{ N = 10; C = 10; R = "US"; Items = @(, @("South Indian Mini Meals", 2)) + @(, @("Rava Kesari", 1)); Pay = "CARD"; DeclineFirst = $true; Target = "DELIVERED"
        Review = @{ Rating = 5; Tags = @("DELICIOUS", "GOOD_VALUE"); Comment = "Tastes like home food. The rasam was excellent." } },
    @{ N = 11; C = 1; R = "DW"; Items = @(, @("Chilli Paneer", 1)) + @(, @("Egg Fried Rice", 2)); Pay = "CASH_ON_DELIVERY"; Target = "DELIVERED"; Notes = "Please bring change for 1000" },
    @{ N = 12; C = 2; R = "NS"; Items = @(, @("Margherita Pizza", 2)) + @(, @("Chicken Tikka Pizza", 1)); Pay = "UPI"; Coupon = "WELCOME150"; Target = "DELIVERED"
        Refunds = @(@{ Amount = $null; Reason = "Pizzas arrived cold and 70 minutes late: full refund" })
        Review = @{ Rating = 1; Tags = @(); Comment = "Pizzas were cold and very late. Thanks to support for the refund." } },
    @{ N = 13; C = 3; R = "PD"; Items = @(, @("Paneer Tikka", 1)) + @(, @("Dal Makhani", 1)) + @(, @("Amritsari Kulcha", 2)); Pay = "UPI"; Upi = "refundfail@upi"; Target = "DELIVERED"
        Refunds = @(@{ Amount = 120; Reason = "Paneer tikka was undercooked" })
        Review = @{ Rating = 3; Tags = @("FAST_DELIVERY"); Comment = "Quick delivery, but the paneer tikka was undercooked." } },
    @{ N = 14; C = 4; R = "BJ"; Items = @(, @("Classic Aloo Tikki Burger", 2)) + @(, @("Paneer Tikka Wrap", 1)); Pay = "WALLET"; Coupon = "WEEKEND50"; Target = "DELIVERED"
        Refunds = @(@{ Amount = 40; Reason = "Mint chutney and sauces missing" }) },
    @{ N = 15; C = 5; R = "AT"; Items = @(, @("Veg Pulao", 2)) + @(, @("Gulab Jamun (2 pcs)", 2)); Pay = "CASH_ON_DELIVERY"; Target = "DELIVERED"
        Review = @{ Rating = 5; Tags = @("DELICIOUS", "GOOD_VALUE"); Comment = "Fragrant pulao and soft gulab jamuns. Great value." } },
    @{ N = 16; C = 6; R = "BD"; Items = @(, @("Egg Biryani", 2)) + @(, @("Double Ka Meetha", 2)); Pay = "CARD"; Target = "DELIVERED"
        Refunds = @(@{ Amount = 119; Reason = "One Double Ka Meetha missing" }, @{ Amount = 80; Reason = "Biryani rice was overcooked" })
        Review = @{ Rating = 3; Tags = @(); Comment = "Biryani was mushy and one dessert was missing. Refund was quick." } },
    @{ N = 17; C = 7; R = "US"; Items = @(, @("Masala Dosa", 1)) + @(, @("Idli Vada Combo", 2)) + @(, @("Filter Coffee", 1)); Pay = "UPI"; Coupon = "UDUPI15"; Target = "DELIVERED"
        Review = @{ Rating = 5; Tags = @("FAST_DELIVERY", "DELICIOUS"); Comment = "Soft idlis and crisp vadas, arrived in 20 minutes." } },
    @{ N = 18; C = 8; R = "PD"; Items = @(, @("Butter Chicken", 2)) + @(, @("Amritsari Kulcha", 2)); Pay = "WALLET"; Target = "DELIVERED"
        Refunds = @(@{ Amount = $null; Reason = "Wrong order delivered: the customer received another order" })
        Review = @{ Rating = 2; Tags = @(); Comment = "Received someone else's order. Refund was processed by support." } },
    @{ N = 19; C = 9; R = "BJ"; Items = @(, @("Chicken Kathi Roll", 2)); Pay = "CASH_ON_DELIVERY"; Target = "CANCELLED"; CancelReason = "Ordered by mistake" },
    @{ N = 20; C = 10; R = "DW"; Items = @(, @("Veg Hakka Noodles", 2)) + @(, @("Chilli Paneer", 1)); Pay = "UPI"; Coupon = "FEAST20"; Target = "CANCELLED"; CancelReason = "Change of plans, will order later" },
    @{ N = 21; C = 1; R = "NS"; Items = @(, @("Margherita Pizza", 1)) + @(, @("Garlic Breadsticks", 2)); Pay = "CARD"; Target = "REJECTED"; CancelReason = "Pizza oven under maintenance tonight" },
    @{ N = 22; C = 2; R = "TN"; Items = @(, @("Tandoori Chicken (Half)", 2)) + @(, @("Butter Naan", 3)); Pay = "WALLET"; Target = "ADMIN_CANCELLED"; CancelReason = "Restaurant reported a gas supply failure: order cancelled by support" },
    @{ N = 23; C = 3; R = "AT"; Items = @(, @("Rajasthani Veg Thali", 1)) + @(, @("Gulab Jamun (2 pcs)", 1)); Pay = "UPI"; Target = "PLACED" },
    @{ N = 24; C = 4; R = "US"; Items = @(, @("South Indian Mini Meals", 1)) + @(, @("Filter Coffee", 2)); Pay = "CASH_ON_DELIVERY"; Target = "ACCEPTED" },
    @{ N = 25; C = 5; R = "BD"; Items = @(, @("Hyderabadi Chicken Dum Biryani", 1)) + @(, @("Egg Biryani", 1)); Pay = "CARD"; Coupon = "BIRYANI25"; Target = "PREPARING"; Notes = "No onions in the raita" },
    @{ N = 26; C = 6; R = "PD"; Items = @(, @("Paneer Tikka", 1)) + @(, @("Sweet Lassi", 2)); Pay = "UPI"; Target = "READY_FOR_PICKUP" },
    @{ N = 27; C = 7; R = "NS"; Items = @(, @("Chicken Tikka Pizza", 1)) + @(, @("Garlic Breadsticks", 1)); Pay = "WALLET"; Target = "OUT_FOR_DELIVERY" },
    @{ N = 28; C = 8; R = "BJ"; Items = @(, @("Crispy Chicken Burger", 1)) + @(, @("Chicken Kathi Roll", 1)) + @(, @("Cold Coffee", 1)); Pay = "CASH_ON_DELIVERY"; Target = "OUT_FOR_DELIVERY"; Notes = "Call when you reach the gate" },
    @{ N = 29; C = 9; R = "DW"; Items = @(, @("Chicken Manchurian Dry", 1)) + @(, @("Egg Fried Rice", 1)); Pay = "UPI"; Target = "PAYMENT_PENDING" },
    @{ N = 30; C = 10; R = "AT"; Items = @(, @("Veg Pulao", 1)) + @(, @("Malai Kulfi", 1)); Pay = "CARD"; Target = "PAYMENT_PENDING" },
    @{ N = 31; C = 11; R = "AB"; Items = @(, @("Andhra Chicken Biryani", 2)) + @(, @("Bobbatlu (2 pcs)", 1)); Pay = "UPI"; Coupon = "WELCOME150"; Target = "DELIVERED"
        Review = @{ Rating = 5; Tags = @("DELICIOUS", "GOOD_PACKAGING"); Comment = "Proper Andhra heat in the biryani. The bobbatlu was a lovely finish." } },
    @{ N = 32; C = 12; R = "MM"; Items = @(, @("Mysore Masala Dosa", 2)) + @(, @("Thatte Idli", 1)) + @(, @("Badam Milk", 2)); Pay = "CASH_ON_DELIVERY"; Target = "DELIVERED"
        Review = @{ Rating = 5; Tags = @("DELICIOUS", "GOOD_VALUE", "FAST_DELIVERY"); Comment = "Thatte idli was soft and buttery, just like in Mysuru." } },
    @{ N = 33; C = 13; R = "KR"; Items = @(, @("Double Egg Roll", 2)) + @(, @("Mishti Doi", 2)); Pay = "UPI"; Target = "DELIVERED"
        Review = @{ Rating = 4; Tags = @("GOOD_VALUE"); Comment = "Egg rolls were flaky and filling. Mishti doi could be a little sweeter." } },
    @{ N = 34; C = 14; R = "ML"; Items = @(, @("Chicken Steamed Momos", 2)) + @(, @("Chicken Thukpa", 1)); Pay = "CARD"; Coupon = "FEAST20"; Target = "DELIVERED"; Notes = "Leave it at the Block G2 reception"
        Review = @{ Rating = 5; Tags = @("EXCELLENT", "FAST_DELIVERY"); Comment = "Juicy momos and piping hot thukpa. Perfect office lunch." } },
    @{ N = 35; C = 15; R = "LP"; Items = @(, @("Four Cheese Pizza", 1)) + @(, @("Tiramisu Cup", 1)); Pay = "WALLET"; Coupon = "WELCOME150"; Target = "DELIVERED"
        Refunds = @(@{ Amount = 179; Reason = "Tiramisu cup arrived crushed" })
        Review = @{ Rating = 3; Tags = @("DELICIOUS"); Comment = "Great pizza, but the tiramisu was squashed in the box. Partial refund received." } },
    @{ N = 36; C = 16; R = "SB"; Items = @(, @("Double Smash Chicken Burger", 2)) + @(, @("Peri Peri Fries", 1)) + @(, @("Oreo Thick Shake", 1)); Pay = "UPI"; Target = "DELIVERED"
        Review = @{ Rating = 4; Tags = @("DELICIOUS", "GOOD_PACKAGING"); Comment = "Juicy burgers and the fries stayed crisp." } },
    @{ N = 37; C = 17; R = "MC"; Items = @(, @("Malabar Chicken Biryani", 2)) + @(, @("Ada Pradhaman", 2)); Pay = "CARD"; Target = "DELIVERED"
        Review = @{ Rating = 5; Tags = @("DELICIOUS", "EXCELLENT"); Comment = "Authentic Malabar biryani, tastes like home in Kozhikode." } },
    @{ N = 38; C = 18; R = "RS"; Items = @(, @("Chicken Seekh Kebab", 1)) + @(, @("Mutton Rogan Josh", 1)) + @(, @("Rumali Roti", 4)); Pay = "WALLET"; Coupon = "WEEKEND50"; Target = "DELIVERED"
        Review = @{ Rating = 4; Tags = @("DELICIOUS", "GOOD_SERVICE"); Comment = "Rogan josh was rich and the rumali rotis were soft." } },
    @{ N = 39; C = 19; R = "GS"; Items = @(, @("Gujarati Thali", 2)) + @(, @("Masala Chaas", 2)); Pay = "CASH_ON_DELIVERY"; Target = "DELIVERED"; Notes = "Office delivery, call on arrival"
        Review = @{ Rating = 5; Tags = @("GOOD_VALUE", "DELICIOUS"); Comment = "Generous thali with a perfect balance of sweet and spicy." } },
    @{ N = 40; C = 20; R = "CC"; Items = @(, @("Masala Chai", 2)) + @(, @("Vada Pav (2 pcs)", 1)) + @(, @("Paneer Tikka Sandwich", 1)); Pay = "UPI"; Target = "DELIVERED"
        Review = @{ Rating = 4; Tags = @("FAST_DELIVERY"); Comment = "Chai was still hot when it arrived. Spicy, tasty vada pav." } },
    @{ N = 41; C = 21; R = "MI"; Items = @(, @("Gulab Jamun Tub (6 pcs)", 1)) + @(, @("Kesar Pista Kulfi", 2)) + @(, @("Royal Falooda", 1)); Pay = "CARD"; Target = "DELIVERED"
        Refunds = @(@{ Amount = $null; Reason = "Kulfis and falooda melted in transit: full refund" })
        Review = @{ Rating = 2; Tags = @(); Comment = "Everything cold arrived melted. Support refunded the full order." } },
    @{ N = 42; C = 22; R = "HW"; Items = @(, @("Schezwan Fried Rice", 1)) + @(, @("Dragon Chicken", 1)); Pay = "UPI"; Target = "DELIVERED"
        Refunds = @(@{ Amount = 239; Reason = "Dragon chicken was undercooked" })
        Review = @{ Rating = 1; Tags = @(); Comment = "The chicken was undercooked and pink inside. Not ordering again." } },
    @{ N = 43; C = 23; R = "HW"; Items = @(, @("Veg Spring Rolls", 2)) + @(, @("American Chopsuey", 1)); Pay = "CASH_ON_DELIVERY"; Target = "DELIVERED"
        Review = @{ Rating = 2; Tags = @("FAST_DELIVERY"); Comment = "Quick delivery, but soggy spring rolls and a bland chopsuey." } },
    @{ N = 44; C = 24; R = "AB"; Items = @(, @("Gongura Mutton", 1)) + @(, @("Andhra Veg Meals", 1)) + @(, @("Chicken 65", 1)); Pay = "WALLET"; Coupon = "FEAST20"; Target = "DELIVERED"
        Review = @{ Rating = 5; Tags = @("DELICIOUS", "GOOD_PACKAGING"); Comment = "Gongura mutton was tangy and fiery. Leak-proof packaging too." } },
    @{ N = 45; C = 25; R = "MC"; Items = @(, @("Appam with Veg Stew", 2)) + @(, @("Kerala Parotta (2 pcs)", 2)); Pay = "UPI"; Target = "DELIVERED"
        Review = @{ Rating = 4; Tags = @("GOOD_VALUE"); Comment = "Soft appams and a mild, creamy stew." } },
    @{ N = 46; C = 11; R = "MM"; Items = @(, @("Bisi Bele Bath", 2)) + @(, @("Mysore Pak", 2)); Pay = "CARD"; DeclineFirst = $true; Target = "DELIVERED"
        Review = @{ Rating = 4; Tags = @("DELICIOUS"); Comment = "Bisi bele bath had just the right amount of ghee." } },
    @{ N = 47; C = 12; R = "LP"; Items = @(, @("Chicken Pepperoni Pizza", 1)) + @(, @("Paneer Makhani Pizza", 1)); Pay = "UPI"; Upi = "refundfail@upi"; Target = "DELIVERED"
        Refunds = @(@{ Amount = 100; Reason = "Toppings were sparse on both pizzas" })
        Review = @{ Rating = 3; Tags = @(); Comment = "Good crust, but the toppings were sparse for the price." } },
    @{ N = 48; C = 13; R = "SB"; Items = @(, @("Veggie Smash Burger", 2)) + @(, @("Oreo Thick Shake", 2)); Pay = "CASH_ON_DELIVERY"; Target = "DELIVERED"; Notes = "No onions in one burger"
        Review = @{ Rating = 5; Tags = @("DELICIOUS", "FAST_DELIVERY"); Comment = "Best veg burger I have had in a while." } },
    @{ N = 49; C = 14; R = "RS"; Items = @(, @("Chicken Dum Biryani", 2)) + @(, @("Shahi Tukda", 2)); Pay = "WALLET"; Target = "DELIVERED"
        Refunds = @(@{ Amount = 119; Reason = "One Shahi Tukda missing" })
        Review = @{ Rating = 4; Tags = @("DELICIOUS"); Comment = "Excellent biryani. One dessert was missing but support refunded it." } },
    @{ N = 50; C = 15; R = "BD"; Items = @(, @("Hyderabadi Chicken Dum Biryani", 1)) + @(, @("Double Ka Meetha", 1)); Pay = "UPI"; Coupon = "BIRYANI25"; Target = "DELIVERED"
        Review = @{ Rating = 5; Tags = @("EXCELLENT"); Comment = "Consistently great biryani." } },
    @{ N = 51; C = 16; R = "US"; Items = @(, @("Masala Dosa", 2)) + @(, @("Filter Coffee", 2)); Pay = "CASH_ON_DELIVERY"; Coupon = "UDUPI15"; Target = "DELIVERED"
        Review = @{ Rating = 4; Tags = @("FAST_DELIVERY", "GOOD_VALUE"); Comment = "Crisp dosas, delivered in 25 minutes." } },
    @{ N = 52; C = 17; R = "ML"; Items = @(, @("Veg Steamed Momos", 2)) + @(, @("Chilli Garlic Noodles", 1)); Pay = "UPI"; Target = "DELIVERED" },
    @{ N = 53; C = 18; R = "GS"; Items = @(, @("Khaman Dhokla", 2)) + @(, @("Shrikhand", 1)); Pay = "CARD"; Target = "DELIVERED"
        Review = @{ Rating = 4; Tags = @("GOOD_VALUE"); Comment = "Soft, spongy dhokla and creamy shrikhand." } },
    @{ N = 54; C = 1; R = "KR"; Items = @(, @("Chicken Tikka Kathi Roll", 2)) + @(, @("Paneer Kathi Roll", 1)); Pay = "WALLET"; Target = "DELIVERED"
        Review = @{ Rating = 5; Tags = @("DELICIOUS", "GOOD_VALUE"); Comment = "Rolls as good as the ones in Kolkata." } },
    @{ N = 55; C = 19; R = "CC"; Items = @(, @("Egg Bhurji Pav", 2)) + @(, @("Masala Chai", 2)) + @(, @("Bun Maska", 2)); Pay = "CASH_ON_DELIVERY"; Coupon = "WEEKEND50"; Target = "DELIVERED" },
    @{ N = 56; C = 20; R = "MI"; Items = @(, @("Rasmalai (2 pcs)", 2)) + @(, @("Kaju Katli (250 g)", 1)); Pay = "UPI"; Target = "DELIVERED"
        Review = @{ Rating = 5; Tags = @("EXCELLENT", "GOOD_PACKAGING"); Comment = "Fresh rasmalai and beautifully packed kaju katli." } },
    @{ N = 57; C = 21; R = "NS"; Items = @(, @("Margherita Pizza", 1)) + @(, @("Farmhouse Veg Pizza", 1)); Pay = "CARD"; Coupon = "WELCOME150"; Target = "DELIVERED"
        Review = @{ Rating = 4; Tags = @("DELICIOUS"); Comment = "Good pizzas, a little late but still hot." } },
    @{ N = 58; C = 22; R = "SB"; Items = @(, @("Double Smash Chicken Burger", 1)) + @(, @("Peri Peri Fries", 2)); Pay = "CASH_ON_DELIVERY"; Target = "CANCELLED"; CancelReason = "Wrong delivery address, will reorder" },
    @{ N = 59; C = 23; R = "AB"; Items = @(, @("Andhra Chicken Biryani", 1)); Pay = "UPI"; Target = "CANCELLED"; CancelReason = "Ordered from the wrong restaurant" },
    @{ N = 60; C = 24; R = "LP"; Items = @(, @("Four Cheese Pizza", 2)); Pay = "CARD"; Target = "REJECTED"; CancelReason = "Wood-fired oven is down for cleaning" },
    @{ N = 61; C = 25; R = "RS"; Items = @(, @("Mutton Rogan Josh", 1)) + @(, @("Rumali Roti", 3)); Pay = "WALLET"; Target = "ADMIN_CANCELLED"; CancelReason = "Restaurant ran out of mutton: order cancelled by support" },
    @{ N = 62; C = 11; R = "KR"; Items = @(, @("Paneer Kathi Roll", 2)); Pay = "UPI"; Target = "PLACED" },
    @{ N = 63; C = 12; R = "GS"; Items = @(, @("Gujarati Thali", 1)) + @(, @("Masala Chaas", 1)); Pay = "CASH_ON_DELIVERY"; Target = "ACCEPTED" },
    @{ N = 64; C = 13; R = "MC"; Items = @(, @("Kerala Fish Curry Meals", 1)) + @(, @("Kerala Parotta (2 pcs)", 1)); Pay = "CARD"; Target = "PREPARING"; Notes = "Less spicy, please" },
    @{ N = 65; C = 14; R = "CC"; Items = @(, @("Paneer Tikka Sandwich", 2)) + @(, @("Masala Chai", 2)); Pay = "WALLET"; Target = "READY_FOR_PICKUP" },
    @{ N = 66; C = 15; R = "MM"; Items = @(, @("Mysore Masala Dosa", 1)) + @(, @("Thatte Idli", 2)); Pay = "UPI"; Target = "READY_FOR_PICKUP" },
    @{ N = 67; C = 16; R = "ML"; Items = @(, @("Chicken Thukpa", 2)); Pay = "CASH_ON_DELIVERY"; Target = "OUT_FOR_DELIVERY"; Notes = "Gate 2, tower B" },
    @{ N = 68; C = 17; R = "MI"; Items = @(, @("Royal Falooda", 2)); Pay = "UPI"; Target = "OUT_FOR_DELIVERY" },
    @{ N = 69; C = 18; R = "AB"; Items = @(, @("Chicken 65", 1)) + @(, @("Andhra Veg Meals", 1)); Pay = "UPI"; Target = "PAYMENT_PENDING" },
    @{ N = 70; C = 19; R = "SB"; Items = @(, @("Veggie Smash Burger", 1)) + @(, @("Peri Peri Fries", 1)); Pay = "CARD"; Target = "PAYMENT_PENDING" },
    # The test customer (26) and the test owner's QA Test Kitchen: history, and orders at every step of the kitchen
    @{ N = 71; C = 26; R = "QA"; Items = @(, @("Chicken Biryani", 2)) + @(, @("Gulab Jamun", 1)); Pay = "UPI"; Target = "DELIVERED"
        Review = @{ Rating = 5; Tags = @("DELICIOUS", "FAST_DELIVERY"); Comment = "Biryani was fragrant and the gulab jamun was warm. Quick delivery." } },
    @{ N = 72; C = 26; R = "QA"; Items = @(, @("Chicken Biryani", 1)) + @(, @("Paneer Butter Masala", 1)); Pay = "CASH_ON_DELIVERY"; Target = "DELIVERED"
        Review = @{ Rating = 4; Tags = @("GOOD_VALUE"); Comment = "Good portions for the price. Paneer butter masala was a little sweet." } },
    @{ N = 73; C = 26; R = "US"; Items = @(, @("Masala Dosa", 2)) + @(, @("Filter Coffee", 1)); Pay = "CARD"; Target = "DELIVERED"
        Review = @{ Rating = 5; Tags = @("DELICIOUS", "GOOD_PACKAGING"); Comment = "Dosas were still crisp, and the coffee came in a sealed cup." } },
    @{ N = 74; C = 26; R = "PD"; Items = @(, @("Butter Chicken", 1)) + @(, @("Amritsari Kulcha", 2)); Pay = "WALLET"; Coupon = "FEAST20"; Target = "DELIVERED"
        Refunds = @(@{ Amount = 99; Reason = "One kulcha was burnt" })
        Review = @{ Rating = 3; Tags = @("DELICIOUS"); Comment = "Butter chicken was lovely but one kulcha was burnt. Support refunded it." } },
    @{ N = 75; C = 26; R = "MI"; Items = @(, @("Gulab Jamun Tub (6 pcs)", 1)) + @(, @("Kesar Pista Kulfi", 2)); Pay = "UPI"; Target = "DELIVERED" },
    @{ N = 76; C = 26; R = "QA"; Items = @(, @("Chicken Biryani", 1)) + @(, @("Malai Kulfi", 1)); Pay = "UPI"; Target = "OUT_FOR_DELIVERY"; Notes = "Ring the bell twice" },
    @{ N = 77; C = 26; R = "SB"; Items = @(, @("Double Smash Chicken Burger", 1)) + @(, @("Peri Peri Fries", 1)); Pay = "CASH_ON_DELIVERY"; Target = "CANCELLED"; CancelReason = "Ordered by mistake" },
    @{ N = 78; C = 26; R = "KR"; Items = @(, @("Chicken Tikka Kathi Roll", 2)); Pay = "CARD"; Target = "PREPARING" },
    @{ N = 79; C = 1; R = "QA"; Items = @(, @("Mutton Biryani", 2)) + @(, @("Sweet Lassi", 2)); Pay = "UPI"; Coupon = "FEAST20"; Target = "DELIVERED"
        Review = @{ Rating = 5; Tags = @("EXCELLENT", "DELICIOUS"); Comment = "Tender mutton and perfectly spiced rice. The lassi was thick and cold." } },
    @{ N = 80; C = 4; R = "QA"; Items = @(, @("Paneer Tikka", 1)) + @(, @("Veg Thali", 2)); Pay = "CARD"; Target = "DELIVERED"
        Review = @{ Rating = 4; Tags = @("GOOD_VALUE", "GOOD_PACKAGING"); Comment = "Filling thali and smoky paneer tikka, neatly packed." } },
    @{ N = 81; C = 12; R = "QA"; Items = @(, @("Mutton Seekh Kebab", 1)) + @(, @("Paneer Butter Masala", 1)) + @(, @("Masala Dosa", 1)); Pay = "WALLET"; Target = "DELIVERED"
        Review = @{ Rating = 3; Tags = @("FAST_DELIVERY"); Comment = "Arrived fast, but the dosa had gone soft by then." } },
    @{ N = 82; C = 17; R = "QA"; Items = @(, @("Chicken Biryani", 3)); Pay = "CASH_ON_DELIVERY"; Target = "DELIVERED"
        Review = @{ Rating = 5; Tags = @("DELICIOUS", "GOOD_VALUE"); Comment = "Ordered for the family and everyone loved it." } },
    @{ N = 83; C = 20; R = "QA"; Items = @(, @("Veg Thali", 1)) + @(, @("Malai Kulfi", 2)); Pay = "UPI"; Target = "DELIVERED" },
    @{ N = 84; C = 23; R = "QA"; Items = @(, @("Mutton Biryani", 1)) + @(, @("Gulab Jamun", 2)); Pay = "CARD"; Target = "DELIVERED"
        Refunds = @(@{ Amount = 120; Reason = "Mutton pieces were chewy and undercooked" })
        Review = @{ Rating = 2; Tags = @(); Comment = "The mutton was chewy. Support gave a partial refund." } },
    @{ N = 85; C = 25; R = "QA"; Items = @(, @("Paneer Tikka", 2)); Pay = "UPI"; Target = "REJECTED"; CancelReason = "Paneer is out of stock today" },
    @{ N = 86; C = 9; R = "QA"; Items = @(, @("Chicken Biryani", 1)); Pay = "CASH_ON_DELIVERY"; Target = "CANCELLED"; CancelReason = "The restaurant took too long to accept" },
    @{ N = 87; C = 2; R = "QA"; Items = @(, @("Veg Thali", 1)) + @(, @("Sweet Lassi", 1)); Pay = "UPI"; Target = "PLACED" },
    @{ N = 88; C = 14; R = "QA"; Items = @(, @("Mutton Biryani", 1)) + @(, @("Paneer Tikka", 1)); Pay = "CASH_ON_DELIVERY"; Target = "PLACED"; Notes = "Extra onions and lemon please" },
    @{ N = 89; C = 19; R = "QA"; Items = @(, @("Chicken Biryani", 2)); Pay = "CARD"; Target = "ACCEPTED" },
    @{ N = 90; C = 22; R = "QA"; Items = @(, @("Paneer Butter Masala", 1)) + @(, @("Masala Dosa", 2)); Pay = "WALLET"; Target = "PREPARING" },
    @{ N = 91; C = 24; R = "QA"; Items = @(, @("Mutton Seekh Kebab", 2)); Pay = "UPI"; Target = "READY_FOR_PICKUP" }
)

$Rank = @{ PAYMENT_PENDING = 0; PLACED = 1; ACCEPTED = 2; PREPARING = 3; READY_FOR_PICKUP = 4; OUT_FOR_DELIVERY = 5; DELIVERED = 6 }

function Get-EmailName([string]$fullName) { ($fullName.ToLower() -replace "[^a-z ]", "").Trim() -replace " +", "." }
function Get-ImageUrl([string]$image) { if ($image) { "$FrontAppUrl/images/$image.jpg" } else { $null } }
function Format-Day([DateTime]$day) { $day.ToString("yyyy-MM-dd") }

# ------------------------------------------------------------------------------------------------------------------
# The orders: place (or find), pay, kitchen, delivery, refunds, review
# ------------------------------------------------------------------------------------------------------------------
function Get-OrderKey($spec) { "demo-b$Batch-o{0:D2}" -f $spec.N }

function Get-CustomerOrder($customer, $orderId) { Invoke-Api GET "/api/v1/orders/$orderId" -As $customer }

function Wait-OrderStatus($customer, $orderId, [string[]]$statuses, [int]$seconds = 60) {
    Wait-For {
        $current = Get-CustomerOrder $customer $orderId
        if ($statuses -contains $current.status) { $current }
    } "order $orderId to become $($statuses -join ' or ')" $seconds
}

# The order of this spec: replayed by its Idempotency-Key, or placed now. $null when it cannot be placed.
function Find-OrPlaceOrder($spec) {
    $customer = $CustomerAccounts[$spec.C - 1]
    $key = Get-OrderKey $spec
    $body = @{ addressId = $customer.AddressId; paymentMethod = $spec.Pay; notes = $spec.Notes }
    # With an empty cart, the key either finds the order placed by an earlier run, or fails with "cart is empty"
    Invoke-Api DELETE "/api/v1/cart" -As $customer -Allow 404 | Out-Null
    $order = Invoke-Api POST "/api/v1/orders" $body -As $customer -Headers @{ "Idempotency-Key" = $key } -Allow 400, 422
    if ($order) { return $order }
    if ($script:LastError -notmatch "empty") { throw "Order $key`: $($script:LastError)" }

    $restaurant = $RestaurantByKey[$spec.R]
    if ($restaurant.Status -ne "ACTIVE") {
        Write-Warn "order $key skipped: $($restaurant.Name) is $($restaurant.Status)"
        return $null
    }
    foreach ($line in $spec.Items) {
        $itemId = $restaurant.DishIds[$line[0]]
        if (-not $itemId) { throw "Dish '$($line[0])' not found at $($restaurant.Name)" }
        Invoke-Api PUT "/api/v1/cart/items/$itemId" @{ quantity = $line[1] } -As $customer | Out-Null
    }
    if ($spec.Coupon) {
        $cart = Invoke-Api PUT "/api/v1/cart/coupon" @{ code = $spec.Coupon } -As $customer -Allow 400, 404, 409, 422
        if (-not $cart) {
            Write-Warn "coupon $($spec.Coupon) not applied to order $key`: $($script:LastError)"
            Invoke-Api DELETE "/api/v1/cart/coupon" -As $customer -Allow 404 | Out-Null
        }
    }
    $order = Invoke-Api POST "/api/v1/orders" $body -As $customer -Headers @{ "Idempotency-Key" = $key }
    $created.orders++
    return $order
}

function Invoke-OrderPayment($spec, $customer, $order) {
    $key = Get-OrderKey $spec
    $attempts = @()
    if ($spec.DeclineFirst) { $attempts += , @("$key-pay-declined", "4000000000000002") }
    $attempts += , @("$key-pay", "4111111111111111")
    $payment = $null
    foreach ($attempt in $attempts) {
        $body = @{ orderId = $order.id; paymentMethod = $spec.Pay }
        if ($spec.Pay -eq "UPI") { $body.upiId = if ($spec.Upi) { $spec.Upi } else { ($customer.Email.Split("@")[0] -replace "\.", "") + "@okhdfc" } }
        if ($spec.Pay -eq "CARD") { $body.card = @{ number = $attempt[1]; expiryMonth = 12; expiryYear = 2030 } }
        if ($spec.Pay -eq "WALLET") { $body.walletId = "wallet-" + $customer.Phone.Substring($customer.Phone.Length - 5) }
        $payment = Invoke-Api POST "/api/v1/payments" $body -As $customer -Headers @{ "Idempotency-Key" = $attempt[0] }
        $created.payments++
        if ($payment.status -ne "SUCCESS") { Write-Info "   payment $($payment.id) $($payment.status) ($($payment.failureReason))" }
    }
    return $payment
}

# Takes the order's delivery as far as the target: offer accepted, picked up, delivered
function Invoke-Delivery($spec, $customer, $order) {
    $target = $spec.Target
    $deadline = (Get-Date).AddMinutes(6)
    $waitingSince = $null
    while ($true) {
        if ((Get-Date) -gt $deadline) { throw "The delivery of order $($order.id) did not get done in 6 minutes" }
        $delivery = Get-Items (Invoke-Api GET "/api/v1/admin/deliveries?orderId=$($order.id)" -As $Admin) | Select-Object -First 1
        if (-not $delivery -or $delivery.status -eq "PENDING" -or -not $delivery.partnerUserId) { Start-Sleep -Seconds 2; continue }
        $rider = $RiderByUserId["$($delivery.partnerUserId)"]
        $path = "/api/v1/delivery/deliveries/mine/$($delivery.id)"
        if ($delivery.status -eq "ASSIGNED") {
            if (-not $rider) {
                if (-not $waitingSince) {
                    $waitingSince = Get-Date
                    Write-Info "   offered to $($delivery.partnerName), who this script cannot sign in as: waiting for the offer to expire (2 minutes)"
                }
                Start-Sleep -Seconds 5; continue
            }
            if ($rider.Existing -and $target -ne "DELIVERED") {
                # Partners that existed before only get deliveries that finish now: they stay free for manual tests
                Invoke-Api PUT "$path/decline" @{ reason = "On a break" } -As $rider -Allow 409, 422 | Out-Null
                Start-Sleep -Seconds 3; continue
            }
            Invoke-Api PUT "$path/accept" $null -As $rider | Out-Null
            $created.deliveries++
            Write-Info "   delivery $($delivery.id): $($rider.Name)"
            continue
        }
        if ($delivery.status -eq "ACCEPTED") {
            if ($target -eq "READY_FOR_PICKUP") { return }
            Invoke-Api PUT "$path/pickup" $null -As $rider | Out-Null
            Wait-OrderStatus $customer $order.id @("OUT_FOR_DELIVERY", "DELIVERED") | Out-Null
            continue
        }
        if ($delivery.status -eq "PICKED_UP") {
            if ($target -eq "OUT_FOR_DELIVERY") { return }
            $cash = if ($spec.Pay -eq "CASH_ON_DELIVERY") { $delivery.cashToCollect } else { $null }
            Invoke-Api PUT "$path/deliver" @{ cashCollected = $cash } -As $rider | Out-Null
            Wait-OrderStatus $customer $order.id @("DELIVERED") | Out-Null
            continue
        }
        if ($delivery.status -eq "DELIVERED") { return }
        throw "The delivery $($delivery.id) of order $($order.id) is $($delivery.status)"
    }
}

function Add-Refunds($spec, $order) {
    if (-not $spec.Refunds) { return }
    $payment = Get-Items (Invoke-Api GET "/api/v1/admin/payments?orderId=$($order.id)" -As $Admin) |
        Where-Object { $_.status -eq "SUCCESS" -or $_.status -eq "REFUNDED" } | Select-Object -First 1
    if (-not $payment) { Write-Warn "order $($order.id): no successful payment to refund"; return }
    $number = 0
    foreach ($refund in $spec.Refunds) {
        $number++
        $body = @{ reason = $refund.Reason }
        if ($null -ne $refund.Amount) { $body.amount = $refund.Amount }
        $result = Invoke-Api POST "/api/v1/admin/payments/$($payment.id)/refunds" $body -As $Admin `
            -Headers @{ "Idempotency-Key" = "$(Get-OrderKey $spec)-refund$number" } -Allow 409, 422
        if ($result) {
            # The same Idempotency-Key returns the refund of an earlier run
            if ($result.requestedAt -and ([DateTime]$result.requestedAt) -lt $started) { continue }
            $created.refunds++
            Write-Info "   refund $($result.id): $($result.amount) $($result.status)"
        } else {
            Write-Warn "refund of order $($order.id) refused: $($script:LastError)"
        }
    }
}

function Add-Review($spec, $customer, $order) {
    if (-not $spec.Review) { return }
    $existing = Invoke-Api GET "/api/v1/reviews/orders/$($order.id)" -As $customer -Allow 404
    if ($existing) { return }
    $number = 0
    $dishRatings = @($order.items | ForEach-Object {
        $number++
        # The second dish one star less (at least 1), so dish ratings differ a little
        @{ menuItemId = $_.menuItemId; rating = [Math]::Max(1, $spec.Review.Rating - (($number + 1) % 2)) }
    })
    $body = @{ orderId = $order.id; rating = $spec.Review.Rating; comment = $spec.Review.Comment
        tags = @($spec.Review.Tags); dishRatings = $dishRatings }
    # review-service learns about the delivery through an event: retry while it says "not delivered"
    try {
        Wait-For {
            $review = Invoke-Api POST "/api/v1/reviews" $body -As $customer -Allow 409, 422
            if ($review) { $created.reviews++ }
            $review -or $script:LastStatus -eq 409
        } "the review of order $($order.id)" 30 2000 | Out-Null
    } catch {
        Write-Warn "review of order $($order.id) not saved: $_"
    }
}

function Invoke-OrderSpec($spec) {
    $customer = $CustomerAccounts[$spec.C - 1]
    $restaurant = $RestaurantByKey[$spec.R]
    $owner = $restaurant.Owner
    $key = Get-OrderKey $spec
    $order = Find-OrPlaceOrder $spec
    if (-not $order) { return }
    $target = $spec.Target
    $status = $order.status
    $kitchen = "/api/v1/restaurants/mine/$($order.restaurantId)/orders/$($order.id)"
    Write-Info ("order {0} ({1}): {2} -> {3}, {4}, {5}" -f $order.id, $key, $restaurant.Name, $customer.Name, $spec.Pay, $target)

    if ($status -eq "PAYMENT_PENDING" -and $target -ne "PAYMENT_PENDING") {
        if (-not (Confirm-OnlinePayments $customer)) { return }
        $payment = Invoke-OrderPayment $spec $customer $order
        if ($payment.status -ne "SUCCESS") { Write-Warn "order $($order.id) not paid: $($payment.status)"; return }
        $status = (Wait-OrderStatus $customer $order.id @("PLACED", "ACCEPTED", "CANCELLED")).status
    }
    if ($status -eq "PAYMENT_PENDING" -or $status -eq "CANCELLED" -or $status -eq "REJECTED") { return }

    if ($target -eq "CANCELLED") {
        if ($status -eq "PLACED") { Invoke-Api PUT "/api/v1/orders/$($order.id)/cancel" @{ reason = $spec.CancelReason } -As $customer | Out-Null }
        return
    }
    if ($target -eq "REJECTED") {
        if ($status -eq "PLACED") { Invoke-Api PUT "$kitchen/reject" @{ reason = $spec.CancelReason } -As $owner | Out-Null }
        return
    }
    if ($target -eq "PLACED") { return }
    if ($Rank[$status] -lt 2) { Invoke-Api PUT "$kitchen/accept" $null -As $owner | Out-Null; $status = "ACCEPTED" }
    if ($target -eq "ACCEPTED") { return }
    if ($Rank[$status] -lt 3) { Invoke-Api PUT "$kitchen/preparing" $null -As $owner | Out-Null; $status = "PREPARING" }
    if ($target -eq "PREPARING") { return }
    if ($target -eq "ADMIN_CANCELLED") {
        Invoke-Api PUT "/api/v1/admin/orders/$($order.id)/cancel" @{ reason = $spec.CancelReason } -As $Admin | Out-Null
        return
    }
    if ($Rank[$status] -lt 4) { Invoke-Api PUT "$kitchen/ready" $null -As $owner | Out-Null; $status = "READY_FOR_PICKUP" }
    if ($status -ne "DELIVERED") { Invoke-Delivery $spec $customer $order }
    if ($target -eq "DELIVERED") {
        $order = Get-CustomerOrder $customer $order.id
        Add-Refunds $spec $order
        Add-Review $spec $customer $order
    }
}

# ==================================================================================================================
$paymentRestoreFailed = $false
try {
    Write-Step "1. Platform and administrator ($ApiUrl)"
    $health = Invoke-RestMethod "$ApiUrl/actuator/health" -TimeoutSec 10
    Write-Info "gateway: $($health.status)"
    $Admin = New-Account $AdminEmail $AdminPassword "PLATFORM_ADMIN" "Platform Admin"
    Get-AccessToken $Admin | Out-Null

    Write-Step "2. Food categories"
    $categoryIds = @{}
    foreach ($existing in Get-Items (Invoke-Api GET "/api/v1/admin/categories" -As $Admin)) { $categoryIds[$existing.name] = $existing.id }
    foreach ($category in $Categories) {
        if ($categoryIds.ContainsKey($category.Name)) { continue }
        $new = Invoke-Api POST "/api/v1/admin/categories" @{ name = $category.Name; description = $category.Description; imageUrl = (Get-ImageUrl $category.Image) } -As $Admin
        $categoryIds[$new.name] = $new.id
        $created.categories++
        Write-Info "created $($new.name) ($($new.id))"
    }
    Write-Info "$($categoryIds.Count) categories"

    Write-Step "3. Restaurants, their owners and menus"
    $hours = "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY" | ForEach-Object { @{ dayOfWeek = $_; opensAt = "00:00"; closesAt = "23:59" } }
    $RestaurantByKey = @{}
    foreach ($spec in $Restaurants) {
        $slug = ($spec.Name.ToLower() -replace "[^a-z0-9]", "")
        $email, $secret = if ($spec.Manual) { $ManualOwner -split ":", 2 } else { "owner.$(Get-EmailName $spec.Owner)@$Domain", $Password }
        $owner = New-Account $email $secret "RESTAURANT_ADMIN" $spec.Owner $spec.OwnerPhone
        if (Confirm-Account $owner) { $created.owners++ }
        $restaurant = Get-Items (Invoke-Api GET "/api/v1/restaurants/mine" -As $owner) | Where-Object { $_.name -eq $spec.Name } | Select-Object -First 1
        if (-not $restaurant) {
            $restaurant = Invoke-Api POST "/api/v1/restaurants/mine" @{
                name = $spec.Name; description = $spec.Description; cuisines = @($spec.Cuisines); phone = $spec.Phone
                email = "hello.$slug@$Domain"; timeZone = "Asia/Kolkata"
                address = @{ line1 = $spec.Line1; city = $City; state = "Karnataka"; postalCode = $spec.Pin; country = "India"; latitude = $spec.Lat; longitude = $spec.Lon }
                legalBusinessName = "$($spec.Name) Foods LLP"; taxRegistrationNumber = $spec.Gst
                foodLicenseNumber = "1122" + $spec.OwnerPhone.Substring($spec.OwnerPhone.Length - 10)
                documents = @(
                    @{ type = "FOOD_LICENSE"; url = "https://docs.example.com/restaurants/$slug/fssai.pdf" },
                    @{ type = "TAX_REGISTRATION"; url = "https://docs.example.com/restaurants/$slug/gst.pdf" })
                operatingHours = @($hours) } -As $owner
            $created.restaurants++
        }
        $id = $restaurant.id
        $status = $restaurant.status
        if ($spec.Target -eq "REJECTED" -and $status -eq "PENDING") {
            Invoke-Api PUT "/api/v1/admin/restaurants/$id/reject" @{ reason = $spec.RejectReason } -As $Admin | Out-Null
            $status = "REJECTED"
        }
        if ($spec.Target -eq "ACTIVE" -or $spec.Target -eq "SUSPENDED") {
            if ($status -eq "PENDING") { Invoke-Api PUT "/api/v1/admin/restaurants/$id/approve" @{ reason = "FSSAI license and GST documents verified" } -As $Admin | Out-Null; $status = "APPROVED" }
            if ($status -eq "APPROVED") { Invoke-Api PUT "/api/v1/admin/restaurants/$id/activate" @{ reason = "Menu reviewed, ready to go live" } -As $Admin | Out-Null; $status = "ACTIVE" }
        }
        $entry = @{ Id = $id; Name = $spec.Name; Status = $status; Owner = $owner; DishIds = @{}; Spec = $spec }
        $RestaurantByKey[$spec.Key] = $entry

        if ($spec.Dishes.Count -gt 0 -and ($status -eq "ACTIVE" -or $status -eq "SUSPENDED")) {
            foreach ($item in Get-Items (Invoke-Api GET "/api/v1/restaurants/mine/$id/menu-items" -As $owner -Allow 404)) { $entry.DishIds[$item.name] = $item.id }
            foreach ($dish in $spec.Dishes) {
                if ($entry.DishIds.ContainsKey($dish.N)) { continue }
                if (-not $categoryIds.ContainsKey($dish.C)) { throw "Unknown category $($dish.C)" }
                $body = @{ name = $dish.N; description = $dish.D; price = $dish.P; categoryId = $categoryIds[$dish.C]; foodType = $dish.T; imageUrl = (Get-ImageUrl $dish.I) }
                # Right after the activation catalog-service may not know the restaurant yet (Kafka): retry
                $item = Wait-For {
                    $result = Invoke-Api POST "/api/v1/restaurants/mine/$id/menu-items" $body -As $owner -Allow 403, 404, 409, 422
                    if ($script:LastStatus -eq 409) { $result = Get-Items (Invoke-Api GET "/api/v1/restaurants/mine/$id/menu-items" -As $owner) | Where-Object { $_.name -eq $dish.N } | Select-Object -First 1 }
                    $result
                } "catalog-service to accept the dishes of $($spec.Name)" 45 2000
                $entry.DishIds[$dish.N] = $item.id
                $created.dishes++
            }
        }
        Write-Info ("{0,-26} id {1,-3} {2,-9} owner {3}, {4} dishes" -f $spec.Name, $id, $status, $owner.Email, $entry.DishIds.Count)
    }

    Write-Step "4. Customers and their addresses"
    $CustomerAccounts = @()
    foreach ($spec in $Customers) {
        $email, $secret = if ($spec.Manual) { $ManualCustomer -split ":", 2 } else { "$(Get-EmailName $spec.Name)@$Domain", $Password }
        $customer = New-Account $email $secret "CUSTOMER" $spec.Name $spec.Phone
        if (Confirm-Account $customer) { $created.customers++ }
        $address = Get-Items (Invoke-Api GET "/api/v1/users/me/addresses" -As $customer) | Where-Object { $_ } | Select-Object -First 1
        if (-not $address) {
            $address = Invoke-Api POST "/api/v1/users/me/addresses" @{ type = $spec.Type; line1 = $spec.Line1; city = $City; state = "Karnataka"
                postalCode = $spec.Pin; country = "India"; latitude = $spec.Lat; longitude = $spec.Lon; makeDefault = $true } -As $customer
        }
        $customer.AddressId = $address.id
        $CustomerAccounts += $customer
        Write-Info ("{0,-16} {1,-42} address {2}" -f $spec.Name, $customer.Email, $address.id)
    }

    Write-Step "5. Delivery partners, online in $City"
    $RiderByUserId = @{}
    foreach ($spec in $Riders) {
        $rider = New-Account "rider.$(Get-EmailName $spec.Name)@$Domain" $Password "DELIVERY_PARTNER" $spec.Name $spec.Phone
        if (Confirm-Account $rider) { $created.riders++ }
        # The partner is created from the UserRegistered event (or on this first call)
        $me = Wait-For { Invoke-Api GET "/api/v1/delivery/partners/me" -As $rider -Allow 404 } "the partner profile of $($rider.Email)" 45
        if ($me.city -ne $City -or -not $me.vehicleType) {
            $me = Invoke-Api PUT "/api/v1/delivery/partners/me" @{ vehicleType = $spec.Vehicle; vehicleNumber = $spec.Number; city = $City } -As $rider
        }
        if ($me.status -eq "OFFLINE") {
            # delivery-service hears about the profile through Kafka: retry until it lets the partner go online
            Wait-For {
                Invoke-Api PUT "/api/v1/delivery/partners/me/availability" @{ status = "AVAILABLE" } -As $rider -Allow 404, 409, 422 | Out-Null
                $script:LastStatus -lt 300
            } "$($rider.Email) to go online" 45 2000 | Out-Null
        }
        $RiderByUserId["$($rider.UserId)"] = $rider
        Write-Info ("{0,-16} {1,-46} {2}" -f $spec.Name, $rider.Email, (Invoke-Api GET "/api/v1/delivery/partners/me" -As $rider).status)
    }
    foreach ($other in $OtherRiders) {
        $email, $secret = $other -split ":", 2
        $rider = New-Account $email $secret "DELIVERY_PARTNER" $email
        $rider.Existing = $true
        $login = Invoke-Api POST "/api/v1/auth/login" @{ email = $email; password = $secret; role = "DELIVERY_PARTNER" } -Allow 400, 401, 403, 404, 423
        if ($login) {
            $rider.UserId = $login.userId
            $script:Tokens["DELIVERY_PARTNER|$email"] = @{ Token = $login.accessToken; At = Get-Date }
            $RiderByUserId["$($login.userId)"] = $rider
            Write-Info "$email (existing): answers the offers it gets, keeps only deliveries that finish now"
        }
    }

    Write-Step "6. Coupons"
    foreach ($spec in $Coupons) {
        $coupon = Get-Items (Invoke-Api GET "/api/v1/admin/coupons?code=$($spec.Code)&size=50" -As $Admin) | Where-Object { $_.code -eq $spec.Code } | Select-Object -First 1
        if (-not $coupon) {
            $body = @{ code = $spec.Code; description = $spec.Description; discountType = $spec.Type; discountValue = $spec.Value
                minOrderAmount = $spec.Min; validFrom = (Format-Day $spec.From); validUntil = (Format-Day $spec.Until)
                usageLimit = $spec.Limit; perCustomerLimit = $spec.PerCustomer }
            if ($spec.Max) { $body.maxDiscount = $spec.Max }
            if ($spec.Restaurant) { $body.restaurantId = $RestaurantByKey[$spec.Restaurant].Id }
            $coupon = Invoke-Api POST "/api/v1/admin/coupons" $body -As $Admin
            $created.coupons++
        }
        if ($spec.Paused -and $coupon.status -eq "ACTIVE") {
            $coupon = Invoke-Api PUT "/api/v1/admin/coupons/$($coupon.id)/deactivate" @{ reason = $spec.Paused } -As $Admin
        }
        $scope = if ($spec.Restaurant) { $RestaurantByKey[$spec.Restaurant].Name } else { "all restaurants" }
        Write-Info ("{0,-11} {1,-8} {2} to {3}, {4}" -f $spec.Code, $coupon.status, (Format-Day $spec.From), (Format-Day $spec.Until), $scope)
    }

    Write-Step "7. Orders (batch $Batch): checkout, payment, kitchen, delivery, refunds, reviews"
    # Delivered ones first, then cancelled, under way, and the unpaid ones last (they expire 15 minutes later)
    $sequence = @("DELIVERED", "CANCELLED", "REJECTED", "ADMIN_CANCELLED", "PLACED", "ACCEPTED", "PREPARING", "READY_FOR_PICKUP", "OUT_FOR_DELIVERY", "PAYMENT_PENDING")
    foreach ($target in $sequence) {
        foreach ($spec in ($Orders | Where-Object { $_.Target -eq $target })) { Invoke-OrderSpec $spec }
    }

    Write-Step "8. Restaurant states after the orders"
    foreach ($entry in $RestaurantByKey.Values) {
        if ($entry.Spec.Target -eq "SUSPENDED" -and $entry.Status -eq "ACTIVE") {
            Invoke-Api PUT "/api/v1/admin/restaurants/$($entry.Id)/suspend" @{ reason = $entry.Spec.SuspendReason } -As $Admin | Out-Null
            $entry.Status = "SUSPENDED"
            Write-Info "$($entry.Name) suspended"
        }
    }
} finally {
    if ($script:PaymentSwitched) {
        Write-Step "9. payment-service back to the settings of .env"
        try {
            Restart-PaymentService
            $script:PaymentSwitched = $false
        } catch {
            $paymentRestoreFailed = $true
            Write-Host "   payment-service could NOT be restarted with the settings of .env: $_" -ForegroundColor Red
            Write-Host "   Restart it yourself: .\start-local.ps1 -Stop, then .\start-local.ps1 -Detached" -ForegroundColor Red
        }
    }
}

# ------------------------------------------------------------------------------------------------------------------
# Summary: what the admin pages read now
# ------------------------------------------------------------------------------------------------------------------
Write-Step "Summary"
function Get-Total([string]$path) {
    $response = Invoke-Api GET $path -As $Admin -Allow 404, 503
    if ($null -eq $response) { return "n/a" }
    if ($response.PSObject.Properties.Name -contains "totalElements") { return $response.totalElements }
    return @($response).Count
}
$byStatus = Get-Items (Invoke-Api GET "/api/v1/admin/orders?size=100" -As $Admin) | Group-Object status | ForEach-Object { "$($_.Name) $($_.Count)" }
$calendar = Invoke-Api GET "/api/v1/admin/calendar?from=$(Format-Day $previousMonthStart)&to=$(Format-Day $nextMonthEnd)" -As $Admin -Allow 503
$rows = [ordered]@{
    "Categories      /api/v1/admin/categories" = (Get-Total "/api/v1/admin/categories")
    "Restaurants     /api/v1/admin/restaurants" = (Get-Total "/api/v1/admin/restaurants?size=1")
    "Foods           /api/v1/admin/menu-items" = (Get-Total "/api/v1/admin/menu-items?size=1")
    "Customers       /api/v1/admin/users?role=CUSTOMER" = (Get-Total "/api/v1/admin/users?role=CUSTOMER&size=1")
    "Partners        /api/v1/admin/delivery-partners" = (Get-Total "/api/v1/admin/delivery-partners?size=1")
    "Orders          /api/v1/admin/orders" = (Get-Total "/api/v1/admin/orders?size=1")
    "Payments        /api/v1/admin/payments" = (Get-Total "/api/v1/admin/payments?size=1")
    "Refunds         /api/v1/admin/refunds" = (Get-Total "/api/v1/admin/refunds?size=1")
    "Deliveries      /api/v1/admin/deliveries" = (Get-Total "/api/v1/admin/deliveries?size=1")
    "Coupons         /api/v1/admin/coupons" = (Get-Total "/api/v1/admin/coupons?size=1")
    "Reviews         /api/v1/admin/reviews" = (Get-Total "/api/v1/admin/reviews?size=1")
    "Calendar events /api/v1/admin/calendar (3 months)" = $(if ($calendar) { @($calendar.events).Count } else { "n/a" })
}
foreach ($row in $rows.Keys) { Write-Host ("   {0,-52} {1}" -f $row, $rows[$row]) }
Write-Host "   Orders by status: $($byStatus -join ', ')"
Write-Host "   Created in this run: $(($created.Keys | ForEach-Object { "$_ $($created[$_])" }) -join ', ')"
Write-Host "   Password of every demo account: $Password (e-mails @$Domain; list: app-credential.txt)"
if ($warnings.Count -gt 0) { Write-Host "   $($warnings.Count) warning(s) above" -ForegroundColor Yellow }
Write-Host ("   Done in {0:N0} s" -f ((Get-Date) - $started).TotalSeconds)
if ($paymentRestoreFailed) { exit 1 }
