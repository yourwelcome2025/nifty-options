==========================================================
  NIFTY OPTIONS — Android App
  How to turn this code into an app on your phone
==========================================================

WHAT THIS IS
  A one-screen Android app. You tap REFRESH, it fetches the live
  NIFTY option chain from NSE, calculates the option Greeks, and
  shows the most suitable CALL and PUT strike for intraday trading,
  with Spot, ATM, PCR and Max Pain.

  Educational tool only. NOT investment advice. Always confirm the
  numbers on your broker app before trading.

----------------------------------------------------------
  IMPORTANT — the honest bit
----------------------------------------------------------
  This folder is the app's SOURCE CODE. To install it on your
  phone you first need to "build" it into an APK file (the app
  installer). Building can NOT be done on the phone itself.

  You have two ways to build it. The first needs no software on
  your computer at all — it builds in the cloud for free.

==========================================================
  OPTION 1 — BUILD IN THE CLOUD (recommended, free)
  You need: a free GitHub account + a computer/laptop for the
  one-time setup (about 10 minutes). After that, only your phone.
==========================================================

  1. Go to github.com and create a free account (if you don't have one).

  2. Click the "+" at top-right > "New repository".
       - Give it any name, e.g.  nifty-options
       - Choose "Private" if you like.
       - Click "Create repository".

  3. On the new repo page, click "uploading an existing file".
       - Open this NiftyOptionsApp folder on your computer.
       - Select EVERYTHING inside it (including the hidden
         ".github" folder) and drag it all into the browser.
         (On Windows: turn on "Hidden items" in File Explorer's
          View menu so you can see the .github folder.)
       - Wait for the file list to show app/, .github/, build.gradle, etc.
       - Click "Commit changes".

  4. The build starts automatically. Click the "Actions" tab.
       - You'll see a job "Build NIFTY Options APK" running (yellow dot).
       - Wait ~3-5 minutes until it turns into a green tick.
       - If it's red, open it, copy the error, and send it to me — I'll fix it.

  5. Open the finished green run. Scroll to the bottom to
     "Artifacts" and download "NiftyOptions-APK".
       - This downloads a .zip. Unzip it to get  app-debug.apk.
       - (You can do steps 4-5 from your phone's browser too.)

  6. Put app-debug.apk on your phone (email it to yourself,
     Google Drive, or USB cable). Tap it to install.
       - Android will ask to allow "install unknown apps" for your
         browser / Files app — allow it, then install.

  7. Open the "NIFTY Options" app and tap REFRESH. Done — it now
     works from your phone alone.

==========================================================
  OPTION 2 — BUILD ON A COMPUTER WITH ANDROID STUDIO
  (if you, or a tech-savvy friend, prefer it)
==========================================================

  1. Download and install Android Studio (free) from
     developer.android.com/studio
  2. Open Android Studio > "Open" > select this NiftyOptionsApp folder.
  3. Wait for it to finish loading (it downloads what it needs).
  4. Menu: Build > Build Bundle(s)/APK(s) > Build APK(s).
  5. When done, click "locate" to find app-debug.apk, then copy it
     to your phone and install as in Option 1, steps 6-7.

----------------------------------------------------------
  NOTES
----------------------------------------------------------
  * If REFRESH ever fails, NSE is usually just rate-limiting.
    Wait a few seconds and tap again. Use normal mobile data or
    Wi-Fi (some VPNs are blocked by NSE).
  * The app shows the nearest expiry first; tap the EXPIRY chips to
    switch to next / later expiries instantly (no re-fetch needed).
    It counts the live clock, so Greeks change through the day.
  * Besides BEST CALL and BEST PUT, a BALANCED card suggests the most
    two-sided (near-ATM) strike for a neutral straddle/strangle.
  * No data leaves your phone except the request to NSE.

  Don't have a computer at all for the one-time build? Tell me —
  we can switch to the Google Sheets version instead, which needs
  no build step.
