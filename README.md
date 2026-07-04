# Philips MMS8085B Remote — Custom Android App
Built specifically for Aditya | Realme GT 7 + Philips MMS8085B

## How to Build & Install

### Requirements
- Android Studio (download from developer.android.com/studio)
- USB cable OR ADB wireless debugging

### Steps
1. Open Android Studio → File → Open → select this folder (PhilipsRemote)
2. Wait for Gradle sync to finish (~2-3 min first time)
3. Connect Realme GT 7 via USB (enable Developer Options → USB Debugging)
4. Click ▶ Run button (or Shift+F10)
5. App installs directly on your phone

### First Launch
- The app now defaults to **NEC @ 38 kHz** (most likely for India-market MMS8085B)
- Point phone IR at the **SUBWOOFER front panel** (not the satellite speakers!)
- If buttons don't respond → tap ⚙ Setup → run Auto-Scan NEC Addresses
- If NEC doesn't work → try RC-6 (addr 16) → then RC-5 (addr 16)

### How the Setup/Scan Works
1. Auto-Scan (NEC mode) sends VOL+ across **25 common OEM addresses** every 1.5 s
2. When your MMS8085B volume increases → Stop and note the address shown
3. Confirm that address, then tap each button label and adjust Command slider until it works
4. Once all buttons confirmed, tap Save

### Files
- RC5Encoder.java    → RC-5 IR protocol encoder (36 kHz, 889µs half-bit)
- IrHelper.java      → ConsumerIrManager wrapper
- Prefs.java         → Saves discovered codes to SharedPreferences
- MainActivity.java  → Main remote UI
- SetupActivity.java → IR code scanner / setup wizard

### If MMS8085B uses a non-RC5 protocol
Some India-market Philips multimedia speakers use a variant protocol.
If auto-scan finds no working address, open SetupActivity and try:
- Carrier frequency: 38000 instead of 36000 (edit RC5Encoder.CARRIER_FREQ)
- This covers NEC protocol compatibility
# Phillips_Remote
