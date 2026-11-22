# Phase 14b report: the release configuration

Branch `phase/14b-release-config`, from `main` (Phase 14a merged, PR #19, `8190ffb`). One session on both tracks (the recorded exception to AI-11, as Phases 2 to 14a). **Nothing is committed, tagged, pushed or published; no secret was created, read or printed; no file was deleted; Phase 15 does not exist.** Plan: `docs/logs/phase-14b-plan.md` (with a section at the end on what changed). Every file and command: `docs/logs/phase-14b-change-log.md`. Decisions: **D-137 to D-144**.

**Result in one paragraph.** The release is configured and rehearsed. A profile `release` that nothing switches on holds the four approved plugins at verified versions; five modules are published and three are not; `autoPublish` is `false`, so Maven Central validates an upload and then **waits for you to press Publish**. A dry run with a throwaway key signed all five modules (14 files, 14 signatures, every one verified with `gpg --verify`), `scripts/check-central-bundle.sh` passed on it and failed on every removed or spoiled file, and the bundle the Central plugin built locally (with the upload switched off) held exactly the five modules. `release.yml` runs only on a pushed tag; `docs.yml` only when you press the button; the 16-page site builds under `mkdocs build --strict`. 64 new tests and 15 mutation checks guard all of it. The last full runs: `mvn -B clean verify` **BUILD SUCCESS** (16:41 min, 8808 tests in the reactor, 0 failures; the two skips are the symbolic-link tests this Windows account cannot make), `mvn -B -DskipTests install` and `mvn -B -f host-check/pom.xml verify` (37 tests) **BUILD SUCCESS**, `mvn -B -Prelease verify` with the throwaway key **BUILD SUCCESS**, `scripts/check-central-bundle.sh --verify` **OK**, `mkdocs build --strict` **exit 0** (16 pages). **What I could not run: anything on GitHub or on Maven Central** (the three workflows on a Linux runner, Pages, the portal, the real key), so no claim is made that they pass there.

## What you must do (the short list)

1. **Before the tag (once):** verify the Maven Central namespace `io.github.mdmusahub` and that the name `springboot-super-admin` is free (D-127 left both open); make a signing key, publish its public half to a key server; generate a Central user token; add the four repository secrets `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`, `CENTRAL_TOKEN_USERNAME`, `CENTRAL_TOKEN_PASSWORD` (Settings, Secrets and variables, Actions). Steps and commands: `docs/release-guide.md`.
2. **Also in the GitHub settings:** Settings, Pages, Source = **GitHub Actions**; Settings, Code security, **Private vulnerability reporting: Enable** (SECURITY.md points people to it).
3. **Review, merge to `main`, wait for CI green.** The first run of the workflow on a Linux runner is still the open item of 14a, and `ci.yml` has one new step (below).
4. **Move `[Unreleased]` to `[0.1.0] - <date>` in `CHANGELOG.md`** and paste the prepared summary (it is in a comment right under the `[Unreleased]` heading); then **tag and push `v0.1.0`**. The release workflow runs the whole suite (about 20 minutes), signs, checks and uploads.
5. **On the Central portal (central.sonatype.com, Deployments): look at the files and press Publish.** Nothing is public before that.
6. **Actions, Docs, Run workflow** to publish the site, once Pages is enabled.
7. **Decide** the points under "Findings" below (the starter's packaging, the placeholder javadoc class, the README name, the 0.1.0 text).

## 1. Files

`git status --short` (nothing deleted; `rules.txt` was untracked before this phase and is not mine):

```
 M .github/workflows/ci.yml                  M jpa-admin-core/pom.xml
 M CHANGELOG.md                              M jpa-admin-core/src/main/.../annotation/AdminBadge.java
 M CLAUDE.md                                 M jpa-admin-core/src/main/.../annotation/AdminLookup.java
 M LICENSE                                   M jpa-admin-core/src/main/.../annotation/AdminResource.java
 M README.md                                 M jpa-admin-core/src/main/.../annotation/AdminUpload.java
 M docs/DECISIONS.md                         M jpa-admin-core/src/main/.../service/BulkDeleteIds.java
 M docs/PLAN.md                              M jpa-admin-core/src/main/.../storage/StorageProvider.java
 M docs/PROJECT_DOCS.md                      M jpa-admin-core/src/main/.../storage/StoredContent.java
 M host-check/README.md                      M jpa-admin-core/src/main/.../storage/StoredFile.java
 M host-check/pom.xml                        M jpa-admin-starter/pom.xml
 M jpa-admin-autoconfigure/pom.xml           M jpa-admin-ui-preview/pom.xml
 M jpa-admin-core/CLAUDE.md                  M jpa-admin-ui/pom.xml
 M pom.xml                                   M sample-app/pom.xml
?? .github/ISSUE_TEMPLATE/ (bug_report.yml, feature_request.yml, config.yml)
?? .github/pull_request_template.md
?? .github/workflows/docs.yml
?? .github/workflows/release.yml
?? CODE_OF_CONDUCT.md  CONTRIBUTING.md  SECURITY.md  mkdocs.yml
?? docs/index.md installation.md run-the-sample.md configuration.md using-the-console.md customizing-entities.md
?? docs/file-uploads.md security.md annotations.md endpoints.md deployment.md troubleshooting.md
?? docs/limitations-and-roadmap.md architecture.md development.md release-guide.md requirements.txt
?? docs/logs/phase-14b-plan.md  phase-14b-change-log.md  phase-14b-report.md
?? jpa-admin-ui/src/main/java/   (package-info.java, ResourceFolders.java)
?? sample-app/src/test/java/.../sample/ (CentralBundleScriptTest, ReleaseSettingsGuardTest, ReleaseWorkflowGuardTest, RepositoryFiles)
?? scripts/   (check-central-bundle.sh)
?? rules.txt   (not mine)
```

`git diff --stat` (tracked files; new files are not counted): **26 files changed, 331 insertions(+), 33 deletions(-)**; the biggest are `pom.xml` (+140: the metadata and the profile), `CHANGELOG.md` (+25), `docs/PLAN.md` (+19) and the Javadoc of `StorageProvider` (+32). The root `docs/` folder now also holds the 16 site pages and `requirements.txt`.

| Area | Created | Edited |
|---|---|---|
| Build | `scripts/check-central-bundle.sh`, `jpa-admin-ui/src/main/java/.../ui/{package-info,ResourceFolders}.java` | `pom.xml` (metadata, profile `release`), the six module poms, `sample-app` and `jpa-admin-ui-preview` (opt-out properties), `jpa-admin-ui` (`show=package`), `host-check/pom.xml` (version) |
| Workflows | `.github/workflows/release.yml`, `.github/workflows/docs.yml` | `.github/workflows/ci.yml` (one step) |
| Library code | none | **comments only** in 7 files of `jpa-admin-core` (Javadoc tags, one doclint error); no code, no template, no property, annotation or route changed |
| Tests | 4 classes in `sample-app` (64 tests) | none (the existing tests are untouched; nothing was weakened) |
| Public project files | `SECURITY.md`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `.github/ISSUE_TEMPLATE/*`, `.github/pull_request_template.md` | `README.md`, `LICENSE` (one line) |
| Docs site | `mkdocs.yml`, 16 pages, `docs/requirements.txt` | |
| Project papers | `docs/logs/phase-14b-{plan,change-log,report}.md` | `docs/DECISIONS.md` (D-137 to D-144), `docs/PLAN.md`, `docs/PROJECT_DOCS.md`, `CHANGELOG.md`, `CLAUDE.md` and `jpa-admin-core/CLAUDE.md` (the jar name), `host-check/README.md` |
| Deleted | nothing in the repository. Outside it: the throwaway keyring, as required (see section 3) | |

**Secrets:** none was created in the repository, read or printed. The only key is the throwaway one (below). `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`, `CENTRAL_TOKEN_USERNAME` and `CENTRAL_TOKEN_PASSWORD` appear only as names in the workflow and the guide. This machine has no GPG keyring of its own (`~/.gnupg` does not exist) and no credential variable was set in any shell I used (checked before each upload-capable run).

## 2. The plugins

Rule: released at least 14 days before 2026-11-22. Each was checked on that day, not from memory: the repository's own listing at `repo1.maven.org` (`maven-metadata.xml` for the newest release, the `Last-Modified` header of each `.pom` for the date; the same data central.sonatype.com shows, and `search.maven.org`'s index was stale for two of them), the Spring Boot 4.1.2 BOM, and the `plugin.xml` inside each downloaded jar for the option names.

| Coordinate | Version | Released | Where checked | Note |
|---|---|---|---|---|
| `org.sonatype.central:central-publishing-maven-plugin` | **0.12.0** | 2026-10-13 (40 days) | repo1 listing and `.pom` date; not in the BOM | pinned in the root pom |
| `org.apache.maven.plugins:maven-gpg-plugin` | **3.2.8** | 2025-06-28 | repo1 listing and `.pom` date; not in the BOM | pinned in the root pom |
| `org.apache.maven.plugins:maven-source-plugin` | **3.4.0** | 2025-11-22 | BOM `maven-source-plugin.version`, repo1 | no version in my pom; `4.0.0-beta-1` is newer and is a beta, not used |
| `org.apache.maven.plugins:maven-javadoc-plugin` | **3.12.0** | 2025-09-16 | BOM `maven-javadoc-plugin.version`, repo1 | no version in my pom |
| `mkdocs` (PyPI, docs only) | **1.6.1** | 2024-08-30 | `pypi.org/pypi/mkdocs/json` | the 2.0 releases are development builds |
| `mkdocs-material` (PyPI, docs only) | **9.8.1** | 2026-11-07 (15 days) | `pypi.org/pypi/mkdocs-material/json` | `9.8.2` is 3 days old, skipped; **Material reaches end of life on 2027-05-05** |
| 27 other Python packages | pinned with SHA-256 hashes | all at least 14 days old | PyPI JSON | five of the versions pip picks today were younger and were replaced (`docs/requirements.txt`, D-137) |
| `actions/checkout` | v7.0.1 `3d3c42e5aac5ba805825da76410c181273ba90b1` | 2026-07-20 | GitHub API, the action's own repository | already in `ci.yml` |
| `actions/setup-java` | v6.1.0 `b843ebf9b913146b38da2eaa7aeddf76074ae1ad` | 2026-10-14 | same; its `src/auth.ts` read at that commit | already in `ci.yml`; v6.2.0 and v6.3.0 are newer |
| `actions/setup-python` | v7.1.0 `6d4fd22b20465ccc0f7354476588e8df7c9ca0e0` | 2026-10-22 | same | `docs.yml` |
| `actions/upload-pages-artifact` | v5.0.0 `fc324d3547104276b827a68afc52ff2a11cc49c9` | 2026-04-10 | same | `docs.yml` |
| `actions/deploy-pages` | v5.0.1 `368f82528645a54fb793d4d04e342629a3f51346` | 2026-09-01 | same | `docs.yml` |

Nothing else was added to the build: no deploy, jar, release, flatten or versions plugin, and no Java dependency.

## 3. Dry run and bundle check

**What was run, in this order** (`phase-14b-change-log.md` entries 18 to 22 and 36 to 40):

1. A **throwaway key** (ed25519, no passphrase, valid one day, the made-up address `dry-run@invalid.example`) was made in a temporary `GNUPGHOME` under `target/dryrun-gnupg`, used only for this check. The real key was never read, `GPG_PRIVATE_KEY` never used; this machine has no real key.
2. **`mvn -B -Prelease verify`**, with `JPA_ADMIN_REQUIRE_ENGINES` and `JPA_ADMIN_REQUIRE_BROWSER` set, full tests: **BUILD SUCCESS, 16:32 min** (core 4282, autoconfigure 581, preview 2788, sample-app 1157; 30 browser and 225 engine tests, none skipped). On Windows the plugin was given a one-line wrapper for Git's `gpg` (`-Dgpg.executable`, outside the repository); the runner needs none. `verify` stops before the deploy phase, so nothing could be uploaded.
3. **`scripts/check-central-bundle.sh --verify`**: `OK: 14 file(s) checked, each with its signature, every signature verified`, and `gpg --verify` run by hand on each of the 14 `.asc` files: **all GOOD**.
4. **The keyring was deleted** (agent stopped, folder removed) and its absence confirmed; nothing with its fingerprint is in the repository.

**Result per module and per required file** (size in bytes; every signature `GOOD` against the throwaway public key):

| Module (artifact) | Packaging | jar | sources jar | javadoc jar | POM | `.asc` of each |
|---|---|---|---|---|---|---|
| `jpa-super-admin` (parent) | pom | n/a | n/a | n/a | 9,806 | 1 of 1 |
| `jpa-admin-core` | jar | 458,821 | 258,721 | 1,161,692 | 7,895 | 4 of 4 |
| `jpa-admin-ui` | jar | 72,128 | 72,458 | 77,626 | 1,220 | 4 of 4 |
| `jpa-admin-autoconfigure` | jar | 59,402 | 44,112 | 98,987 | 5,513 | 4 of 4 |
| `springboot-super-admin` (starter) | pom | n/a | n/a | n/a | 2,880 | 1 of 1 |
| `sample-app` (not published) | jar | built, **no** signature, no sources jar, no javadoc jar | | | | none |
| `jpa-admin-ui-preview` (not published) | jar | built, **no** signature, no sources jar, no javadoc jar | | | | none |
| `host-check` (not published) | outside the reactor | not built by this project | | | | none |

**The bundle check, and the removed-file test.** The script passed on the real tree. It was then run against a **copy** of the built tree, with **one file removed or spoiled at a time**, and restored afterwards (the real tree was never touched): the javadoc jar of core removed, the sources jar of ui removed, the main jar of autoconfigure removed, the signature of the starter's pom removed, the signature of the parent's pom removed, a signature replaced by junk, a jar changed after signing (`--verify`: bad signature), a signature appearing for `sample-app`, a javadoc jar appearing for the preview app: **9 of 9 failed with the file named, each passed again once restored (19 of 19 runs as expected)**. The same cases now run in the build: `CentralBundleScriptTest`, 36 tests, one for each of the 28 files (14 and their signatures) and the cases above. Two more mutation checks broke the script itself (it stopped looking for the javadoc jar; it stopped reading what a signature looks like): both made tests fail.

**The bundle the Central plugin would upload** (built locally with `-DskipUpload=true`, an unreachable `centralBaseUrl`, an empty settings file and no signing key in the environment: no upload was possible): **exactly the five published modules, 84 entries with signatures** (14 artifacts, 14 signatures, four checksum files for each artifact), and **none of the three others**. Each of the two exclusion mechanisms alone keeps `sample-app` and `jpa-admin-ui-preview` out; with both removed they are in the bundle (D-138).

## 4. Decisions, deviations and findings

**`autoPublish=false`, in plain words (D-139).** After the upload, Maven Central checks the bundle (signatures, checksums, required metadata, sources and javadoc for every jar). If it passes, Central **keeps it in a waiting area and does nothing more** until you open central.sonatype.com, look at the files and press **Publish** (or **Drop**, which throws the upload away so you can fix it and send again). Until you press, nothing is public. With `true` the release would go public by itself the moment the checks pass, and **a published version can never be changed or removed**. `waitUntil=VALIDATED` makes the build wait for Central's checks and **fail** if Central refuses the bundle.

**Decisions** (all in `docs/DECISIONS.md`): D-137 the plugins, versions, metadata, docs tools and action pins; D-138 the five published modules, the three that are not, and the three ways they are kept out; D-139 `autoPublish`; D-140 the throwaway-key dry run, the check script, the bundle built without upload; D-141 the docs site and the manual-only workflow; D-142 doclint and the module with no Java; D-143 how the release workflow treats the secrets; D-144 the starter needs `<type>pom</type>`.

**Deviations from the brief, and why**

1. **Pom modules have no jar.** The brief says to confirm "the jar, the sources jar, the javadoc jar, the POM and a `.asc` for each" for each published module. The parent and the starter have packaging `pom`, so for them there is only the POM and its `.asc` (Central asks for sources and javadoc for a jar). The three jar modules have all four files and four signatures; 14 files and 14 signatures in all.
2. **The release workflow runs `mvn -B -Prelease -DskipTests deploy`, not `mvn -B -Prelease deploy`.** The tests run in an earlier step with no secret in the environment, so no test JVM ever holds the key's passphrase or the token; a `verify` and the file check run before the `deploy`, so a missing file fails before any upload (D-143).
3. **`ci.yml` has one new step** (the only change): `mvn -B -Prelease -DskipTests -Dgpg.skip=true verify`, on Java 17 only. A normal build never runs the release profile, so a Javadoc error or a missing file would otherwise first appear on release day. It signs nothing and stops before any upload; a test fails if CI ever runs the profile any other way or sees a secret.
4. **`jpa-admin-ui` got a package-private class** (`ResourceFolders`) and a package page, so that it has a javadoc jar (D-142). This was the one place near a stop condition: the usual fix for a resource-only module is a placeholder jar made by `maven-jar-plugin`, which is **not** on the approved list, so I took the way that stays inside the four plugins and adds no public API, and recorded the other. **If you prefer the placeholder jar, say so: it is one pom change and the class goes.**
5. **The 742 `missing`-group Javadoc warnings of `jpa-admin-core` are not fixed.** Doclint passes (no error; the one error was fixed), and the public API (annotations and the storage SPI) is documented. The rest are missing `@param`/`@return`/comments on public members of internal classes; fixing them is large, mechanical and risks the ARCH-12 name check, and Central does not check them.
6. **README author name:** it said "Muhammad Musa"; the brief names the developer "Musa Malik". The README now says Musa Malik. Revert it if the first name was meant.
7. **`CODE_OF_CONDUCT.md`** is the Contributor Covenant 2.1 text from its official site with one line changed: the contact is `mdmusaalam98019@gmail.com`.
8. **`docs.yml` uses the official Pages actions**, not `mkdocs gh-deploy`, so no branch is written and no write token on `contents` is needed; the price is that Pages must be set to the source "GitHub Actions".

**Findings you should know**

- **The install snippet was wrong (D-144).** The starter is packaging `pom`, so a dependency without `<type>pom</type>` fails with "Could not find artifact ...:jar:0.1.0" (tested with a scratch project). The old README and the drafts omitted the line. All now show it. **Option, not done:** publish the starter as an empty jar so a plain dependency works (as Spring Boot's own starters do); it changes the published artifact and needs another placeholder javadoc class, and it can be done in 0.1.1 without breaking anyone who wrote the type. Your decision.
- **Windows traps** (written up in `docs/release-guide.md`): Git's `gpg` takes only POSIX paths, so the laptop dry run used a one-line wrapper; a gpg-agent path over about 100 characters fails; `mvn clean` deletes a keyring under `target/`; and a literal `©` in the Javadoc footer broke the plugin's own up-to-date check on this machine's Cp1252 charset (fixed with `&amp;#169;`). None affects the Linux runner.
- **Material for MkDocs reaches end of life on 2027-05-05** (its own build says so). Fine for 0.1; plan a move (its notice names Zensical).
- **`mvn install` put version 0.1.0 into your `~/.m2`.** After the real release, delete `~/.m2/repository/io/github/mdmusahub` so Maven uses Central's copy and not yours.

**Not done, honestly**

- **Nothing was run on GitHub or on Central:** the Linux runner (`ci.yml`, `release.yml`, `docs.yml` have never run), GitHub Pages, private vulnerability reporting, the Central portal and the real signing key. The workflows are checked by 13 tests that read them, by the actions' own sources and by dry runs of every Maven and script step they contain; the first real runs are the proof. The first run of `release.yml` is the first time the secrets are used: that is why `autoPublish` is `false`.
- **From `docs/logs/phase-14-scope.md`, still open and not in this phase's brief:** the security-test pass over SEC-1 to SEC-15 and UPL-1 to UPL-10 (item 5), JitPack, the README quick start timed at five minutes on a clean machine, a demo GIF, and the manual passes of Phase 13 (item 8). The 14a decisions that were left to you (`@Lob` search, chart option, relation picker, input border, orphan cleanup) are also still yours; the `@Lob` limit is documented as a known issue on the site.
- **The site pages** were cut from the drafts and corrected for the statements I found false (D-141); I did not re-verify every sentence of the long rule lists (sort, filter, record and form rules) against the code line by line.
- **Rules:** none could not be satisfied. AI-2 (plan, then wait): the plan is `docs/logs/phase-14b-plan.md`; the brief said not to wait, and pre-approved the plugins, so I did not ask. AI-8 and CODE-5: no dependency beyond the pre-approved four plugins and the two Python packages (with their pinned dependencies). SEC-1 to SEC-3 and ARCH-12 hold: this phase changed Javadoc comments only in the library, and `NameCheckTest` (42) and `HostIsolationTest` (15) pass.

## 5. Tests

| Module | Phase 14a report | Now | Change |
|---|---|---|---|
| jpa-admin-core | 4280 (2 skipped) | **4282** (2 skipped) | +2: `PinnedTestZoneTest` of D-135, after the 14a report; this phase added none and changed comments only |
| jpa-admin-autoconfigure | 581 | **581** | 0 |
| jpa-admin-ui-preview | 2788 | **2788** | 0 |
| sample-app | 1093 (0 skipped) | **1157** (0 skipped) | **+64**: `ReleaseSettingsGuardTest` 15, `ReleaseWorkflowGuardTest` 13, `CentralBundleScriptTest` 36 |
| reactor total | 8742 | **8808** | +66 |
| host-check (outside the reactor) | 37 | **37** | 0 |

Run as CI runs it (`JPA_ADMIN_REQUIRE_ENGINES=true`, `JPA_ADMIN_REQUIRE_BROWSER=true`, Docker Desktop started first): **30 browser tests and 225 engine tests ran, none skipped.** The only skips are the two symbolic-link tests of `jpa-admin-core`, because this Windows account cannot create symbolic links (the Linux runner runs them), as in 14a. `mvn -B clean verify`: BUILD SUCCESS in 16:41 min. `mvn -B -DskipTests install` and `mvn -B -f host-check/pom.xml verify` (37 tests): BUILD SUCCESS. **ARCH-12 `NameCheckTest` (42) and `HostIsolationTest` (15): passed in that run.** The Enforcer rule (ARCH-11) passed in every build.

**What the new tests show** (all read the files, so none needs a key, a network or Central):

- *A normal build does not sign or publish:* the profile has no `<activation>` and no `activeByDefault` anywhere; none of the four plugins is in any module's default build or in a plugin-management block; no build extension outside a profile.
- *The three unpublished modules are excluded:* the published modules are exactly the parent, core, ui, autoconfigure and the starter; `sample-app` and `jpa-admin-ui-preview` switch five release properties on in their own poms and are in `excludeArtifacts`; `host-check` is not in the reactor, has Spring Boot's parent and nothing that could publish it. At bundle level, built locally: each mechanism alone keeps them out, and with both removed they are in the bundle.
- *Secrets and staging:* `autoPublish` is `false`, no `<passphrase>` or credential in any pom, the passphrase read from `GPG_PASSPHRASE`, `bestPractices` on; the workflows trigger and permit what they should, every action is pinned to a 40-character SHA with its release, each secret reaches only its own step, nothing prints one.
- *The check script* (36): passes a complete tree, fails and names the file when any one of the 28 files (14 and their signatures) is missing, and for an empty file, a junk signature, a missing `target` folder, a leftover in an unpublished module, a snapshot version, and a module that stops opting out.

**Mutation checks** (each: break one rule, see the matching test fail, restore byte for byte by MD5, confirm green; `phase-14b-change-log.md` entry 33): 17 in all, **all caught, all restored identical**: the profile switching itself on; `autoPublish` true; `sample-app` leaving `skipPublishing`; leaving `excludeArtifacts`; the signing plugin in the default build; a `<passphrase>` in a pom; the preview app signing; the release also on a branch push; an action pinned to a tag; a secret in the test step; docs publishing on a push; write permission; the key echoed; CI running the profile to the upload; that CI step on every Java; the script ignoring the javadoc jar; the script ignoring what a signature looks like.

**The Linux runner and the Java 21 leg** could not be run here (Windows; JDK 17 only), so no claim is made that `ci.yml` or the two new workflows pass there.

## 6. Suggested commits (PowerShell-safe, by path, never `git add .`, no trailer; `rules.txt` stays out)

```powershell
# 1. build: version 0.1.0, the Central metadata, the release profile and the modules that opt out of it
git add pom.xml jpa-admin-core/pom.xml jpa-admin-ui/pom.xml jpa-admin-autoconfigure/pom.xml jpa-admin-ui-preview/pom.xml jpa-admin-starter/pom.xml sample-app/pom.xml host-check/pom.xml host-check/README.md CLAUDE.md jpa-admin-core/CLAUDE.md
git commit -m "build: release profile for Maven Central (signing, source and javadoc jars, staged upload), version 0.1.0 and the Central metadata (Phase 14b, D-137 to D-139)"

# 2. javadoc: the doclint error, the public API, and a javadoc jar for the module with no Java
git add jpa-admin-core/src/main jpa-admin-ui/src/main
git commit -m "docs: fix the doclint error, document the annotations and the storage SPI, give jpa-admin-ui a javadoc jar (Phase 14b, D-142)"

# 3. the bundle check, the two workflows and the CI step for the release profile
git add scripts/check-central-bundle.sh .github/workflows/release.yml .github/workflows/docs.yml .github/workflows/ci.yml
git update-index --chmod=+x scripts/check-central-bundle.sh
git commit -m "ci: release workflow on a pushed tag, manual docs workflow, check of the Central bundle, release-profile build in CI (Phase 14b, D-140, D-141, D-143)"

# 4. tests
git add sample-app/src/test
git commit -m "test: release profile is off by default, unpublished modules are excluded, workflows and bundle check (Phase 14b)"

# 5. the files a public project needs
git add README.md LICENSE SECURITY.md CONTRIBUTING.md CODE_OF_CONDUCT.md .github/ISSUE_TEMPLATE .github/pull_request_template.md
git commit -m "docs: README for the release, security policy, contributing guide, code of conduct, issue forms, pull request template, licence copyright (Phase 14b)"

# 6. the documentation site
git add mkdocs.yml docs/requirements.txt docs/index.md docs/installation.md docs/run-the-sample.md docs/configuration.md docs/using-the-console.md docs/customizing-entities.md docs/file-uploads.md docs/security.md docs/annotations.md docs/endpoints.md docs/deployment.md docs/troubleshooting.md docs/limitations-and-roadmap.md docs/architecture.md docs/development.md docs/release-guide.md
git commit -m "docs: the documentation site, built with mkdocs --strict, tools pinned with hashes (Phase 14b, D-141)"

# 7. the project papers
git add CHANGELOG.md docs/DECISIONS.md docs/PLAN.md docs/PROJECT_DOCS.md docs/logs/phase-14b-plan.md docs/logs/phase-14b-change-log.md docs/logs/phase-14b-report.md
git commit -m "docs: Phase 14b decisions D-137 to D-144, plan, change log and report, changelog entries and the prepared 0.1.0 text"
```

`rules.txt` was untracked before this phase and is not mine: leave it out. Then, your steps: review, push the branch, watch CI (its first Linux run, and the new release-profile step), merge, and follow "What you must do" above. **Do not tag until the dry run and the secrets are in place.**
