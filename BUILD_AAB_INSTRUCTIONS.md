# 📦 How to Build AAB File for Google Play

## ✅ Step-by-Step Instructions

### Method 1: Using Android Studio (Easiest)

1. **Open your project** in Android Studio

2. **Build → Generate Signed Bundle/APK**
   - Click on `Build` menu at the top
   - Select `Generate Signed Bundle/APK`

3. **Choose Android App Bundle**
   - Select "Android App Bundle" (recommended by Google)
   - Click "Next"

4. **Select your keystore**
   - If you have a keystore file, browse and select it
   - Enter your keystore password
   - Enter your key alias
   - Enter your key password
   
   ⚠️ **IMPORTANT**: If this is your first release, you'll need to create a keystore:
   - Click "Create new..."
   - Fill in the keystore information
   - **SAVE THIS KEYSTORE FILE SAFELY** - you'll need it for all future updates!

5. **Select build variant**
   - Choose "release" (not debug)
   - Click "Finish"

6. **Wait for build to complete**
   - Android Studio will build your AAB file
   - Location: `app/release/app-release.aab`

### Method 2: Using Command Line (Terminal)

1. **Open Terminal/Command Prompt** in your project folder

2. **For Windows (PowerShell):**
   ```powershell
   cd C:\Art\AndroidGame
   .\gradlew bundleRelease
   ```

3. **For Mac/Linux:**
   ```bash
   cd /path/to/AndroidGame
   ./gradlew bundleRelease
   ```

4. **Sign the AAB** (if not auto-signed):
   ```bash
   jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 -keystore your-keystore.jks app/build/outputs/bundle/release/app-release.aab your-key-alias
   ```

5. **Find your AAB file:**
   - Location: `app/build/outputs/bundle/release/app-release.aab`

### Method 3: Using Gradle in Android Studio

1. **Open Gradle panel** (right side of Android Studio)

2. **Navigate to:**
   - `app` → `Tasks` → `bundle` → `bundleRelease`

3. **Double-click `bundleRelease`**

4. **Wait for build to complete**

5. **Find your AAB:**
   - `app/build/outputs/bundle/release/app-release.aab`

## 🔐 Keystore Information

### If you already have a keystore:
- Use the same keystore file for all updates
- Keep it safe - you cannot update your app without it!

### If you need to create a keystore:
1. In Android Studio: Build → Generate Signed Bundle/APK → Create new
2. Or use command line:
   ```bash
   keytool -genkey -v -keystore my-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias my-key-alias
   ```

## ✅ Verification Checklist

Before uploading to Google Play:

- [ ] AAB file created successfully
- [ ] File size is reasonable (usually 5-50 MB)
- [ ] Version code is 3 (check in build.gradle)
- [ ] Version name is "1.1"
- [ ] Keystore file is saved safely
- [ ] Tested on a real device (optional but recommended)

## 📤 Upload to Google Play Console

1. Go to: https://play.google.com/console
2. Select your app
3. Go to: **Production** → **Create new release**
4. Upload your `app-release.aab` file
5. Add release notes
6. Review and publish

## 🐛 Troubleshooting

### Error: "Keystore file not found"
- Make sure you have your keystore file
- If first release, create a new keystore
- If updating, use the same keystore as before

### Error: "Gradle build failed"
- Check for compilation errors
- Make sure all dependencies are downloaded
- Try: `File` → `Invalidate Caches / Restart`

### Error: "Signing failed"
- Verify keystore password is correct
- Check key alias is correct
- Ensure keystore file is not corrupted

## 📝 Notes

- **AAB vs APK**: Google Play prefers AAB (smaller downloads for users)
- **Release build**: Always use "release" variant, not "debug"
- **Keystore security**: Never commit keystore to Git!
- **Version code**: Must always increase (3, 4, 5, etc.)

---

**Need help?** Check Android Studio's build output for specific error messages.



