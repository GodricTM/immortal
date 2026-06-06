# Submit an app to the Immortal Store

The Immortal App Store is open to the community. If you've built (or know of) an
app that runs well on Meta Portal, you can have it listed so every Immortal user
can find and install it.

Meta published an official Portal **sample app** and a guide to building Portal
apps — so building one "the way Meta documented" is straightforward, and those
apps are exactly what this store is for. Immortal is simply the home and the
storefront those apps now live in.

## What can be listed

- **Your own Portal app** — hosted as an APK on a stable URL (e.g. a GitHub
  Release), built for Portal (Android 10 / API 29, arm64, no Google Mobile
  Services).
- **An existing open-source app** that already runs on Portal — typically from
  **F-Droid** (these are resolved live so they never go stale).

Requirements:

- Works on Portal hardware (arm64, API 29, landscape, touch — or documents which
  models it supports).
- A stable download (GitHub Release `apkUrl`, or an F-Droid `fdroidId`).
- Not malware; no deceptive behavior. Listings are user-installed and reviewed
  only lightly — see the trust note below.

## How to submit

1. Open a **new issue** using the **"App submission"** template (Issues → New
   issue → App submission), or
2. Open a **pull request** adding your entry to [`catalog.json`](catalog.json).

Either way a maintainer reviews it and adds/merges it. We aim to keep turnaround
short.

### Catalog entry format (schema v2)

```jsonc
{
  "name": "Your App",
  "packageName": "com.example.yourapp",
  "source": "url",                     // "url" (direct APK) or "fdroid"
  "apkUrl": "https://github.com/you/yourapp/releases/latest/download/yourapp.apk",
  // for F-Droid apps instead: "source": "fdroid", "fdroidId": "com.example.yourapp"
  "versionCode": 123,                  // optional — pin a specific (e.g. arm64) build
  "minSdk": 29,                        // drives the store's compatibility badge
  "description": "One clear sentence (under 120 chars — it gets one line in the list).",
  "longDescription": "A paragraph for the app's detail page.",   // optional
  "iconUrl": "https://…/icon.png",     // optional https PNG; F-Droid apps can use
                                       // https://f-droid.org/repo/<id>/en-US/icon.png.
                                       // Without one the store shows a monogram tile.
  "author": "You / Your Project",      // optional, shown under the app name
  "homepage": "https://github.com/you/yourapp",   // optional, linked on the detail page
  "submittedBy": "your-handle",        // optional, credited on the detail page
  "devices": ["tv"]                    // optional — only if restricted to "touch" or "tv"
}
```

Every PR that touches `catalog.json` is validated automatically by CI: schema
shape, duplicate packages, https URLs, the F-Droid id resolving, and the icon and
APK URLs actually serving. You can run the same check locally:

```
python3 scripts/validate_catalog.py --network
```

Compatibility note: the store shows a "Needs Android X+" badge (and disables
install) on devices below your `minSdk`. First-gen Portals and the Portal TV are
API 28; the Go/Mini/gen-2 are API 29 — so apps with `minSdk` 30+ can't run on any
Portal and won't be listed.

Multi-architecture apps (e.g. ones shipping separate per-CPU APKs) should pin the
**arm64-v8a** build with `versionCode`, since Portal is arm64.

## Trust & safety

Listings are **community-submitted** and installed by the user on their own
device. Maintainers do a light review (is it a real Portal app, does the download
resolve, is it obviously not malware), but **we don't audit source code** and
can't guarantee third-party apps. Install what you trust. Apps that turn out to be
malicious or broken are removed.

## Building a Portal app

New to it? Meta's Portal sample app (the basis this project grew from) and Meta's
"build apps for Portal" developer materials are the best starting point. An app is
just a normal Android app targeting API 29, arm64, no GMS, designed for a
landscape touchscreen. If it installs and runs on your Portal, it can be listed.
