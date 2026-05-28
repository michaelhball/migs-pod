# Releasing MigsPod

This is the canonical release process. It mirrors the migs-music setup so the
same secret names and patterns apply across both Android projects.

## One-time setup

### 1. Generate a release keystore

You only do this once. Store the resulting `.jks` somewhere safe (1Password,
a USB stick, *not* the repo).

```sh
keytool -genkeypair \
    -alias migs-pod \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -keystore ~/migs-pod-release.jks
```

### 2. Configure local signing

Copy the template and fill in the four fields. This file is gitignored.

```sh
cp keystore.properties.example keystore.properties
$EDITOR keystore.properties
```

```
storeFile=/absolute/path/to/migs-pod-release.jks
storePassword=...
keyAlias=migs-pod
keyPassword=...
```

Verify locally that a signed release builds:

```sh
./gradlew :app:bundleRelease
```

### 3. Opt into Play App Signing

When you upload the first build to Play Console, accept the offer to let
Google manage the master signing key. You keep the upload key (the one in
the keystore above); Google re-signs with the master key. This means losing
the upload key isn't catastrophic — you can reset it via Play Console.

### 4. (Optional) Set up Firebase / Crashlytics

If you want crash reports:

1. Create a Firebase project at https://console.firebase.google.com
2. Add an Android app with package `com.migspod`
3. Download `google-services.json` and drop it in `app/`

The Gradle config in `app/build.gradle.kts` only applies the Firebase
plugins if `app/google-services.json` exists, so the project builds fine
without it. The CI release workflow does the same — if you also store the
JSON contents in a `GOOGLE_SERVICES_JSON` GitHub secret, the release build
will include Crashlytics; otherwise it won't.

### 5. (Optional) Set up Play Store automated uploads

1. In Google Cloud Console, create a service account
2. Grant it the Play Developer API + "Release manager" role in Play Console
3. Download the JSON key
4. Add it as a single GitHub secret `PLAY_SERVICE_ACCOUNT_JSON`

The release workflow will then auto-upload every signed AAB to the
**Internal** testing track. Promotion to closed → open → production stays
manual in Play Console (intentional — gives you a chance to sanity-check
each step).

### 6. GitHub secrets

Add these in repo Settings → Secrets and variables → Actions:

| Secret | Required | Source |
|---|---|---|
| `KEYSTORE_BASE64` | yes | `base64 -i ~/migs-pod-release.jks \| pbcopy` |
| `KEYSTORE_PASSWORD` | yes | the password you set in step 1 |
| `KEY_ALIAS` | yes | `migs-pod` (or your alias) |
| `KEY_PASSWORD` | yes | the key password from step 1 |
| `GOOGLE_SERVICES_JSON` | optional | contents of `app/google-services.json` |
| `PLAY_SERVICE_ACCOUNT_JSON` | optional | service account JSON from step 5 |

## Cutting a release

Before tagging:

- [ ] `./gradlew :app:testDebugUnitTest :app:lintDebug` — green.
- [ ] `./scripts/device-smoke-test.sh` — green on a real device.
- [ ] Work through the relevant sections of [MANUAL_TESTING.md](MANUAL_TESTING.md).
- [ ] All `[Unreleased]` items in CHANGELOG.md reflect what's actually in this release.

Cut the release:

```sh
./release.sh 1.0.1 --tag    # bumps version, commits, tags v1.0.1
git push && git push --tags  # CI takes over from here
```

CI will:

1. Build a signed APK + AAB
2. Create a GitHub Release named `MigsPod v1.0.1` with both artifacts attached
3. Upload the AAB to the Play Store Internal track (if `PLAY_SERVICE_ACCOUNT_JSON` is configured)

After CI is green:

- [ ] Smoke-test the Internal track on your test device for at least a few hours.
- [ ] Promote in Play Console (manual click) when satisfied: Internal → Closed → Open → Production.

## Versioning convention

- `versionName` is semver (`major.minor.patch`), shown to users.
- `versionCode` is a monotonic integer that Play Store uses to decide which build is "newer". The release script bumps it by exactly one on every release. **Never reuse a versionCode** — Play Store rejects duplicate codes.
