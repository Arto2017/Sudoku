# Play Console Update Guide

## Step-by-Step Guide to Update Your App in Google Play Console

---

## 📦 **Step 1: Build Release AAB (Android App Bundle)**

### Option A: Using Android Studio
1. Open your project in Android Studio
2. Go to `Build` → `Select Build Variant`
3. Select **release** for the app module
4. Go to `Build` → `Generate Signed Bundle / APK`
5. Select **Android App Bundle**
6. Choose your keystore file (or create a new one if first time)
7. Enter keystore password and key alias password
8. Click **Next** → Select **release** → Click **Finish**
9. The AAB will be generated in: `app/release/app-release.aab`

### Option B: Using Command Line
```bash
cd c:\AndroidGame
.\gradlew bundleRelease
```
The AAB will be in: `app\build\outputs\bundle\release\app-release.aab`

**⚠️ Important:** 
- Use the **SAME keystore** you used for the previous version
- If you lose your keystore, you cannot update your app anymore!

---

## 📋 **Step 2: Prepare Update Information**

Before uploading, prepare:

1. **Version Information:**
   - Check current version in `app/build.gradle`:
     - `versionCode` - Must be **higher** than current Play Store version
     - `versionName` - User-visible version (e.g., "1.1", "1.2", "2.0")

2. **What's New in This Version:**
   - Write release notes (what changed/fixed/added)
   - Keep it concise (4000 character limit)
   - Focus on user-facing changes

3. **Screenshots (if changed):**
   - If you changed UI significantly, update screenshots
   - Required sizes vary by device type

---

## 🚀 **Step 3: Upload to Play Console**

1. **Go to Google Play Console:**
   - Visit: https://play.google.com/console
   - Sign in with your developer account

2. **Select Your App:**
   - Click on your app name (SudokuPro)

3. **Go to Production (or Testing Track):**
   - In the left menu, click **Production** (or **Internal testing** / **Closed testing** if testing first)
   - Click **Create new release**

4. **Upload AAB:**
   - Click **Upload** button
   - Select your `app-release.aab` file
   - Wait for upload to complete
   - Google will analyze the bundle (may take a few minutes)

---

## ✍️ **Step 4: Fill Out Release Details**

### 4.1 Release Name (Optional)
- Usually auto-filled with version name
- Can customize if needed

### 4.2 Release Notes
**Required for Production releases**

Write what's new in this version. Example:
```
What's new in this version:
• Fixed daily challenge timer display
• Improved ad loading performance
• Bug fixes and stability improvements
• Enhanced game difficulty balancing
```

**Tips:**
- Be honest about changes
- Focus on user benefits
- Keep it simple and clear
- Use bullet points for readability

### 4.3 Review Release
- Check the version code and name
- Verify the AAB size
- Review any warnings or errors

---

## ✅ **Step 5: Complete Pre-Launch Checklist**

Before submitting, verify:

### Required Items:
- [ ] **Content Rating** - Up to date (if content changed)
- [ ] **Target Audience** - Correctly set
- [ ] **Data Safety** - Accurate privacy information
- [ ] **Store Listing** - App description, screenshots, etc.
- [ ] **Pricing & Distribution** - Countries and pricing

### Important Checks:
- [ ] **Ads Policy Compliance** - Your ads are real (not test ads) ✅
- [ ] **Privacy Policy** - Required if you collect user data
- [ ] **Permissions** - Only request necessary permissions
- [ ] **Target SDK** - Should be recent (you're using 35 ✅)

---

## 📤 **Step 6: Submit for Review**

1. **Review Everything:**
   - Double-check release notes
   - Verify version numbers
   - Check all warnings

2. **Start Rollout:**
   - Click **Start rollout to Production** (or your chosen track)
   - Confirm the release

3. **Review Process:**
   - Google will review your update (usually 1-3 days)
   - You'll receive email notifications about status
   - Check Play Console for review status

---

## 🔍 **Step 7: Monitor Release**

After submission:

1. **Check Review Status:**
   - Go to **Production** → **Releases**
   - See status: "In review", "Approved", or "Rejected"

2. **If Approved:**
   - App will be available to users gradually (staged rollout)
   - Monitor crash reports and user feedback

3. **If Rejected:**
   - Read the rejection reason
   - Fix issues and resubmit

---

## 📊 **Step 8: Post-Release Monitoring**

After your update goes live:

1. **Monitor Metrics:**
   - **Crashes & ANRs** - Check for new issues
   - **User Reviews** - Respond to feedback
   - **Ad Performance** - Check AdMob dashboard

2. **Check AdMob:**
   - Verify ads are showing (not test ads)
   - Monitor revenue and impressions
   - Check for any ad policy violations

3. **Version Rollout:**
   - If issues found, you can pause rollout
   - Or create a hotfix if critical bugs found

---

## 🚨 **Common Issues & Solutions**

### Issue: "Version code must be higher"
**Solution:** Increment `versionCode` in `app/build.gradle`
```gradle
versionCode 3  // Was 2, now 3
versionName "1.1"
```

### Issue: "Keystore mismatch"
**Solution:** You must use the SAME keystore as the original release
- Never lose your keystore file!
- Keep backups in secure location

### Issue: "App rejected - Test ads detected"
**Solution:** 
- Verify you built RELEASE version (not debug)
- Check `USE_TEST_ADS = false` in release build
- Rebuild and resubmit

### Issue: "Missing privacy policy"
**Solution:** 
- Add privacy policy URL in Play Console
- Required if app collects any user data
- Or if using ads (AdMob requires it)

---

## 📝 **Quick Checklist Before Submitting**

- [ ] ✅ Built **release** AAB (not debug)
- [ ] ✅ Incremented `versionCode` in `build.gradle`
- [ ] ✅ Updated `versionName` (optional but recommended)
- [ ] ✅ Tested release build on real device
- [ ] ✅ Verified ads are real (not test ads)
- [ ] ✅ Written release notes
- [ ] ✅ All required Play Console sections completed
- [ ] ✅ Privacy policy added (if required)
- [ ] ✅ Content rating up to date
- [ ] ✅ Data safety form completed

---

## 🎯 **Recommended Update Workflow**

### For First Update:
1. Test in **Internal testing** track first
2. Share with a few testers
3. Fix any critical issues
4. Then promote to **Production**

### For Regular Updates:
1. Build release AAB
2. Upload to **Production** directly
3. Monitor for issues
4. Gradually increase rollout percentage

---

## 📞 **Need Help?**

- **Play Console Help:** https://support.google.com/googleplay/android-developer
- **AdMob Support:** https://support.google.com/admob
- **Policy Issues:** Check Play Console → Policy status

---

## ✅ **You're Ready!**

Your app is configured correctly with real ads. Follow the steps above to update your app in Play Console.

**Good luck with your update! 🚀**
