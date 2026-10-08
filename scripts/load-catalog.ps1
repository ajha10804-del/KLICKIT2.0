param(
    [string]$BaseUrl = "http://localhost:8080/api",
    [string]$CsvPath = ".\data\catalog.csv",
    [string]$AdminEmail = $env:KLICKIT_ADMIN_EMAIL,
    [string]$AdminPassword = $env:KLICKIT_ADMIN_PASSWORD
)

if (-not $AdminEmail -or -not $AdminPassword) {
    Write-Error "Admin credentials missing. Please set KLICKIT_ADMIN_EMAIL and KLICKIT_ADMIN_PASSWORD environment variables or pass -AdminEmail and -AdminPassword parameters."
    exit 1
}

if (-not (Test-Path $CsvPath)) {
    Write-Error "CSV file not found at $CsvPath"
    exit 1
}

# 1. Login
$loginBody = @{
    email = $AdminEmail
    password = $AdminPassword
} | ConvertTo-Json

try {
    $loginResponse = Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method Post -ContentType "application/json" -Body $loginBody
    $token = $loginResponse.data.token
} catch {
    Write-Error "Login failed: $_"
    exit 1
}

# 2. Get existing products
$headers = @{
    "Authorization" = "Bearer $token"
}

try {
    $existingResponse = Invoke-RestMethod -Uri "$BaseUrl/products" -Method Get -Headers $headers
    $existingProducts = $existingResponse.data
    $existingNames = @{}
    if ($null -ne $existingProducts) {
        foreach ($p in $existingProducts) {
            if ($null -ne $p.name) {
                $existingNames[$p.name.Trim().ToLower()] = $true
            }
        }
    }
} catch {
    Write-Error "Failed to fetch existing products: $_"
    exit 1
}

# 3. Read CSV and process
$createdCount = 0
$skippedCount = 0
$failedCount = 0

$csvData = Import-Csv -Path $CsvPath -Encoding UTF8
$lineNumber = 1

foreach ($row in $csvData) {
    $lineNumber++
    $name = $row.name
    $description = $row.description
    $price = $row.price
    $imageUrl = $row.imageUrl
    $category = $row.category

    $trimmedName = ""
    if ($null -ne $name) {
        $trimmedName = $name.Trim()
    }

    # Validation
    if ([string]::IsNullOrWhiteSpace($trimmedName)) {
        Write-Host "Row $lineNumber failed: Name cannot be blank." -ForegroundColor Red
        $failedCount++
        continue
    }

    $parsedPrice = 0.0
    if (-not [double]::TryParse($price, [ref]$parsedPrice) -or $parsedPrice -le 0) {
        Write-Host "Row $lineNumber failed: Price must be a number greater than 0." -ForegroundColor Red
        $failedCount++
        continue
    }

    # Idempotency check
    if ($existingNames.ContainsKey($trimmedName.ToLower())) {
        Write-Host "Skipped: '$trimmedName' already exists." -ForegroundColor Yellow
        $skippedCount++
        continue
    }

    # POST new product
    $productReq = @{
        name = $trimmedName
        description = $description
        price = $parsedPrice
        category = $category
    }
    
    if (-not [string]::IsNullOrWhiteSpace($imageUrl)) {
        $productReq.imageUrl = $imageUrl
    }

    $jsonBody = $productReq | ConvertTo-Json -Depth 5 -Compress
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($jsonBody)

    try {
        $requestUri = "$BaseUrl/products"
        $webRequest = [System.Net.WebRequest]::Create($requestUri)
        $webRequest.Method = "POST"
        $webRequest.ContentType = "application/json; charset=utf-8"
        $webRequest.Headers.Add("Authorization", "Bearer $token")
        
        $stream = $webRequest.GetRequestStream()
        $stream.Write($bytes, 0, $bytes.Length)
        $stream.Close()
        
        $response = $webRequest.GetResponse()
        $response.Close()
        
        $createdCount++
        $existingNames[$trimmedName.ToLower()] = $true
        Write-Host "Created: '$trimmedName'" -ForegroundColor Green
    } catch {
        $exMessage = $_.Exception.Message
        Write-Host "Row $lineNumber failed: Failed to create '$trimmedName' - $exMessage" -ForegroundColor Red
        $failedCount++
    }
}

Write-Host "--------------------------------"
Write-Host "Summary: Created $createdCount, Skipped $skippedCount, Failed $failedCount"
if ($failedCount -gt 0) {
    exit 1
} else {
    exit 0
}
