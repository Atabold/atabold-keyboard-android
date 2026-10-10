> [!IMPORTANT]
> **This is not FUTO Keyboard.** It is **Atabold Keyboard**, a modified fork of FUTO Keyboard, made for one person's use and shared as it is. It is not affiliated with, endorsed by or supported by FUTO. Please do not ask FUTO for help with it.

# Atabold Keyboard (a fork of FUTO Keyboard)

**Start at the main repository: [Atabold/atabold-keyboard](https://github.com/Atabold/atabold-keyboard).** It has the install guide, the backend, the documentation and the licence of the parts written for this project. This repository only holds the Android app.

## What this fork changes

- **Toolbar actions:** Corriger, Améliorer, Traduire, Format, Warm up and Online. They correct, rephrase, translate and format what you are typing, through a backend that you host yourself.
- **Privacy rules written into the code:** online features and learning only in a fixed list of apps, never in password or code fields, no backups, no logs in release builds, a clipboard history that lives in memory only.
- **Signed requests:** every request is signed with a key that lives in the Android Keystore, so only your registered phone can use your backend.
- **Branding and settings:** renamed (the app is called Clavier Atabold on the phone), its own application ID (it installs next to FUTO Keyboard), a "Correction & privacy" settings page, and an "About this fork" notice.

FUTO Keyboard is FUTO's work and property; the modifications described here, and this name and icon, are the work of [Atabold](https://github.com/Atabold). Every edit to a file that comes from FUTO is marked with a `kbfr fork:` comment; the fork's own code is in `java/src/org/futo/inputmethod/latin/kbfr/`. The payment functionality of FUTO Keyboard is untouched: it is FUTO's, it stays because the FUTO licence requires it, and the author of this fork receives no share of anything paid there. One card was added at the top of the payment screen to say exactly that.

## Where it comes from, and updates

Based on FUTO Keyboard at commit `70a5d390c` (2026-09-23). **Updates from FUTO are not guaranteed to be merged, security fixes included.** Read [UPSTREAM.md](UPSTREAM.md).

## Building

You need your own backend URL and your own signing certificate: copy `kbfr.properties.example` to `kbfr.properties`, fill it in (it also asks which language you want the explanations in, and which one *Translate* writes by default), and follow the install guide in the main repository. A release build stops with an explanation while `kbfr.properties` is missing.

## Licence

[FUTO Source First License 1.1-kb](LICENSE.md), unchanged, governs this repository and the app as a whole: FUTO's code belongs to FUTO. In short, and not as legal advice: you may use the software for any purpose, but you may modify it only for non-commercial purposes, and you may share it only free of charge and for non-commercial purposes, with the licence text and the notices kept. It is a source-available licence, not an open source one. The files written from scratch for this fork are, in addition, available under the AGPL-3.0-or-later: [LICENSING.md](LICENSING.md) says which ones and why. The original AOSP keyboard code is under Apache-2.0 (see [NOTICE](NOTICE)).

## Reporting problems

Problems with this fork go to the [main repository](https://github.com/Atabold/atabold-keyboard/issues). Security problems: see [SECURITY.md](SECURITY.md). Do not send issues or pull requests about this fork to FUTO.

---

# FUTO Keyboard (the original README, unmodified)

The goal is to make a good modern keyboard that stays offline and doesn't spy on you. This keyboard is a fork of [LatinIME, The Android Open-Source Keyboard](https://android.googlesource.com/platform/packages/inputmethods/LatinIME), with significant changes made to it.

Check out the [FUTO Keyboard website](https://keyboard.futo.tech/) for downloads and more information.

The code is licensed under the [FUTO Source First License 1.1](LICENSE.md).

## Issue tracking and contributing

Please check the GitHub repository to report issues: [https://github.com/futo-org/android-keyboard/](https://github.com/futo-org/android-keyboard/)

The source code is hosted on our [internal GitLab](https://gitlab.futo.org/keyboard/latinime) and mirrored to [GitHub](https://github.com/futo-org/android-keyboard/). As registration is closed on our internal GitLab, we use GitHub instead for issues and pull requests.

If you want to help translate the app, please do so via our Pontoon instance: https://i18n-keyboard.futo.org/

Due to custom license, pull requests to this repository require signing a [CLA](https://cla.futo.org/) which you can do after opening a PR. Contributions to the [layouts repo](https://github.com/futo-org/futo-keyboard-layouts) don't require CLA as they're Apache-2.0

Please do not submit AI-generated pull requests, as these tend to make the codebase more difficult to understand. Pull requests made with clear heavy use of AI may be closed without comment. Please also avoid using AI to write issues, and try to explain it in your own words instead.

## Layouts

If you want to contribute layouts, check out the [layouts repo](https://github.com/futo-org/futo-keyboard-layouts).

## Building

When cloning the repository, you must perform a recursive clone to fetch all dependencies:
```
git clone --recursive https://gitlab.futo.org/keyboard/latinime.git
```

If you forgot to specify recursive clone, use this to fetch submodules:
```
git submodule update --init --recursive
```

You can then open the project in Android Studio and build it that way, or use gradle commands:
```
./gradlew assembleUnstableDebug
./gradlew assembleStableRelease
```

## APK signing

For official FUTO Keyboard versions, you can verify the APK's signing key fingerprint for integrity.

```
Signing key fingerprint for all versions except Google Play:

MD5: 3A:BB:71:C6:BB:E4:92:27:B1:E3:5D:81:01:48:6A:B0
SHA1: 5D:15:B3:6E:C9:6A:96:28:41:09:DD:62:93:0D:9C:39:9F:5F:06:43
SHA-256: 74:3F:AD:58:64:AB:C4:26:50:0B:2D:C2:C4:7C:8A:D3:24:CB:CD:16:03:3F:80:16:99:48:41:35:63:74:F9:95

```

---

## Thank you, FUTO

This fork stands on FUTO Keyboard: the keyboard engine, the swipe, the autocorrect, the offline speech-to-text and the payment screens are the work and the property of FUTO Holdings, Inc. Thank you for building a keyboard that stays offline and does not spy on you, and for sharing its source. If you want to support the keyboard, do it through FUTO: payments made in the app go to them, not to the author of this fork, who can be found at [github.com/Atabold](https://github.com/Atabold).
