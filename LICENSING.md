# Licensing of this repository

This repository is a modified fork of FUTO Keyboard. Three kinds of material live here, under different terms. This page says which is which, and why. It is not legal advice.

## 1. FUTO's code, and the app as a whole: FUTO Source First License 1.1-kb

The file [LICENSE.md](LICENSE.md), unchanged, governs the repository and the app as it is built. FUTO Keyboard, with its engine, swipe, autocorrect, speech-to-text, payment screens and every other file that comes from it, belongs to FUTO Holdings, Inc. That includes the FUTO files that this fork edits (every edit to a source file is marked `kbfr fork:`): an edit of someone else's code is a derivative of it, so it stays under their licence.

In short: you may use the software for any purpose; you may modify it only for non-commercial purposes; you may share it only free of charge and for non-commercial purposes; and you must keep FUTO's notices and payment functionality and say that you modified it. Only FUTO can change these terms. Because of them the app as a whole cannot be AGPL, and it is source-available, not open source.

## 2. Files written from scratch by Atabold: also AGPL-3.0-or-later

Copyright (C) 2026 Atabold. These files were written for this fork and contain no code copied from FUTO Keyboard. Checked on 2026-10-10 against FUTO's code at the base commit: of 1,287 comparable lines, six also occur in FUTO's code, all of them one-line uses of Android framework APIs such as `Build.VERSION.SDK_INT >= Build.VERSION_CODES.P`.

- everything in `java/src/org/futo/inputmethod/latin/kbfr/` and in `src/test/java/org/futo/inputmethod/latin/kbfr/`;
- `java/res/values/kbfr-strings.xml`, `java/res/values-it/kbfr-strings.xml` and `java/res/xml/kbfr_data_extraction_rules.xml`;
- the icon's colours, card and layers, with nothing traced from FUTO's: `java/res/values/ic_launcher_background.xml`, `java/unstable/res/values/ic_launcher_background.xml`, `java/res/drawable/ic_launcher_background.xml`, `java/res/drawable/ic_launcher_card.xml`, `java/res/mipmap-anydpi/ic_launcher.xml` and `java/res/mipmap-anydpi/ic_launcher_round.xml` (the letter itself is in section 3);
- `kbfr.properties.example`, `.gitleaks.toml`, `LICENSING.md`, `SECURITY.md`, `UPSTREAM.md`, and the notice at the top of `README.md`.

For these files you may choose either licence:

- the **GNU Affero General Public License, version 3 or any later version** ([LICENSES/AGPL-3.0-or-later.txt](LICENSES/AGPL-3.0-or-later.txt)), for example to reuse them in another program that is AGPL- or GPL-compatible, as long as you do not bring FUTO's code along; or
- **FUTO's licence**, which you must follow anyway as soon as you combine these files with FUTO's code, as this app does.

They are written against FUTO's internal classes, so on their own they do not build. The AGPL option is about reusing the code and the ideas elsewhere, not about the app.

## 3. Other people's files

- The seven toolbar icons `java/res/drawable/kbfr_*.xml` come from [Lucide](https://lucide.dev/) (ISC, and MIT for the icons derived from Feather). They are not Atabold's. The full text is in `THIRD_PARTY_NOTICES.md` in the [main repository](https://github.com/Atabold/atabold-keyboard).
- The letter é of the icon, in `java/res/drawable/ic_launcher_foreground.xml`, is outlined from Gelasio Bold, © The Gelasio Project Authors, under the SIL Open Font License 1.1 ([LICENSES/OFL-1.1-Gelasio.txt](LICENSES/OFL-1.1-Gelasio.txt)). That one file stays under the OFL and is not offered under the AGPL.
- The original Android Open Source Project keyboard code is under the Apache License 2.0, and the dictionaries are © Lexiteria LLC, used by permission: see [NOTICE](NOTICE).

## In practice

| You want to... | Which terms |
|---|---|
| use the app | FUTO's licence: for any purpose |
| change the app and share it | FUTO's licence: non-commercially and free of charge, with its notices and a note that you modified it |
| sell the app, or ship it in a commercial product | not allowed (FUTO's licence) |
| reuse Atabold's files in a different program | AGPL-3.0-or-later (or FUTO's terms, if you combine them with FUTO's code) |

Questions about FUTO's terms belong to FUTO. The author of this fork is [Atabold](https://github.com/Atabold).
