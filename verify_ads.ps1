# Ad Verification Script for Pre-Release Check
# This script verifies that all ads are configured with real IDs (not test ads)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  AdMob Ad Verification Script" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$errors = 0
$warnings = 0

# Test Ad Unit IDs (should NOT be found in production code)
$testAdIds = @(
    "ca-app-pub-3940256099942544/6300978111",
    "ca-app-pub-3940256099942544/1033173712",
    "ca-app-pub-3940256099942544/5224354917"
)

# Real Ad Unit IDs (should be found)
$realAdIds = @(
    "ca-app-pub-2049534800625732/8506433174",
    "ca-app-pub-2049534800625732/8282964095",
    "ca-app-pub-2049534800625732/6071841522"
)

Write-Host "1. Checking build.gradle for USE_TEST_ADS configuration..." -ForegroundColor Yellow
$buildGradle = Get-Content "app\build.gradle" -Raw
if ($buildGradle -match 'release\s*\{[^}]*buildConfigField\s+"boolean",\s+"USE_TEST_ADS",\s+"false"') {
    Write-Host "   ✅ Release build: USE_TEST_ADS = false" -ForegroundColor Green
} else {
    Write-Host "   ❌ ERROR: Release build does not have USE_TEST_ADS = false" -ForegroundColor Red
    $errors++
}

if ($buildGradle -match 'debug\s*\{[^}]*buildConfigField\s+"boolean",\s+"USE_TEST_ADS",\s+"true"') {
    Write-Host "   ✅ Debug build: USE_TEST_ADS = true (OK for testing)" -ForegroundColor Green
} else {
    Write-Host "   ⚠️  WARNING: Debug build may not have USE_TEST_ADS = true" -ForegroundColor Yellow
    $warnings++
}

Write-Host ""
Write-Host "2. Checking for test ad IDs in production code..." -ForegroundColor Yellow
$foundTestIds = $false
foreach ($testId in $testAdIds) {
    $files = Get-ChildItem -Path "app\src\main" -Recurse -Include *.kt,*.xml,*.java | 
        Select-String -Pattern [regex]::Escape($testId) | 
        Where-Object { $_.Path -notmatch "AdManager\.kt" -or $_.Line -notmatch "TEST_.*AD_UNIT_ID" }
    
    if ($files) {
        Write-Host "   ❌ ERROR: Found test ad ID in production code:" -ForegroundColor Red
        foreach ($file in $files) {
            Write-Host "      $($file.Path):$($file.LineNumber) - $($file.Line.Trim())" -ForegroundColor Red
        }
        $errors++
        $foundTestIds = $true
    }
}

if (-not $foundTestIds) {
    Write-Host "   ✅ No test ad IDs found in production code (only in AdManager.kt constants)" -ForegroundColor Green
}

Write-Host ""
Write-Host "3. Checking for real ad IDs in code..." -ForegroundColor Yellow
$foundRealIds = $false
foreach ($realId in $realAdIds) {
    $files = Get-ChildItem -Path "app\src\main" -Recurse -Include *.kt,*.xml,*.java | 
        Select-String -Pattern [regex]::Escape($realId)
    
    if ($files) {
        Write-Host "   ✅ Found real ad ID: $realId" -ForegroundColor Green
        $foundRealIds = $true
    }
}

if (-not $foundRealIds) {
    Write-Host "   ❌ ERROR: No real ad IDs found in code!" -ForegroundColor Red
    $errors++
}

Write-Host ""
Write-Host "4. Checking AndroidManifest.xml for AdMob App ID..." -ForegroundColor Yellow
$manifest = Get-Content "app\src\main\AndroidManifest.xml" -Raw
if ($manifest -match 'ca-app-pub-2049534800625732~1014670551') {
    Write-Host "   ✅ Real AdMob App ID found in AndroidManifest.xml" -ForegroundColor Green
} else {
    Write-Host "   ❌ ERROR: Real AdMob App ID not found in AndroidManifest.xml" -ForegroundColor Red
    $errors++
}

Write-Host ""
Write-Host "5. Checking XML layouts for banner ad IDs..." -ForegroundColor Yellow
$mainXml = Get-Content "app\src\main\res\layout\activity_main.xml" -Raw
$dailyXml = Get-Content "app\src\main\res\layout\activity_daily_challenge.xml" -Raw

if ($mainXml -match 'ca-app-pub-2049534800625732/8506433174') {
    Write-Host "   ✅ activity_main.xml has real banner ad ID" -ForegroundColor Green
} else {
    Write-Host "   ❌ ERROR: activity_main.xml does not have real banner ad ID" -ForegroundColor Red
    $errors++
}

if ($dailyXml -match 'ca-app-pub-2049534800625732/8506433174') {
    Write-Host "   ✅ activity_daily_challenge.xml has real banner ad ID" -ForegroundColor Green
} else {
    Write-Host "   ❌ ERROR: activity_daily_challenge.xml does not have real banner ad ID" -ForegroundColor Red
    $errors++
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Verification Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

if ($errors -eq 0 -and $warnings -eq 0) {
    Write-Host ""
    Write-Host "✅ ALL CHECKS PASSED!" -ForegroundColor Green
    Write-Host "   Your app is configured correctly for production." -ForegroundColor Green
    Write-Host "   You can safely build a release APK/AAB and upload to Play Console." -ForegroundColor Green
    Write-Host ""
    exit 0
} elseif ($errors -eq 0) {
    Write-Host ""
    Write-Host "⚠️  WARNINGS FOUND (but no errors)" -ForegroundColor Yellow
    Write-Host "   Review the warnings above, but you can proceed with release." -ForegroundColor Yellow
    Write-Host ""
    exit 0
} else {
    Write-Host ""
    Write-Host "❌ ERRORS FOUND: $errors error(s), $warnings warning(s)" -ForegroundColor Red
    Write-Host "   Please fix the errors above before building a release." -ForegroundColor Red
    Write-Host ""
    exit 1
}
