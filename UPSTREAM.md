# Relationship with FUTO Keyboard

Atabold Keyboard is a modified fork of [FUTO Keyboard](https://keyboard.futo.tech/). It is **not** FUTO Keyboard, it is not affiliated with FUTO, and FUTO does not support it.

| | |
|---|---|
| Based on | FUTO Keyboard commit `70a5d390c505a6bbcc4e14966e5628e43ca3f1fc` (2026-09-23) |
| Upstream | <https://gitlab.futo.org/keyboard/latinime> (mirrored at <https://github.com/futo-org/android-keyboard>) |
| This fork's branch | `main` (the default branch). `master` is FUTO's code at the base commit, unchanged |
| What changed | `git diff master..main` |

## Updates from FUTO are not guaranteed

The maintainer of this fork merges FUTO's changes **when there is time, and without any schedule or promise**. That includes security fixes. If FUTO publishes a fix you need, do not wait for this fork:

1. Watch FUTO's repository (GitHub: *Watch → Custom → Releases*), and read their release notes.
2. Merge the fix yourself, as described below, or run FUTO Keyboard itself for the features that matter to you.

## How to see and merge what changed

Every edit to a file that comes from FUTO is marked with a `kbfr fork:` comment, and the fork's own code lives in one package, `java/src/org/futo/inputmethod/latin/kbfr/`. That keeps merges small.

```sh
git remote add upstream https://gitlab.futo.org/keyboard/latinime.git
git remote set-url --push upstream DISABLED-no-push-to-FUTO   # FUTO asks for no pull requests
git fetch upstream
git switch -c merge-upstream main
git merge upstream/master
# resolve conflicts around the "kbfr fork:" markers, then:
./gradlew testUnstableDebugUnitTest --tests "org.futo.inputmethod.latin.kbfr.*"
```

Then rebuild, sign and install as described in the install guide of the main repository.

## No contributions to FUTO from here

FUTO's own README asks for no AI-generated pull requests and requires a CLA for pull requests to their repository. Nothing from this fork is meant to be sent to FUTO. Report problems with this fork in the [main repository](https://github.com/Atabold/atabold-keyboard), not to FUTO.
