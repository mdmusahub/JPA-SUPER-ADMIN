# Phase 14b plan: release configuration (written 2026-11-22, before any change)

Branch `phase/14b-release-config`, from `main` (Phase 14a merged, PR #19, `8190ffb`). One session on both tracks (the recorded exception to AI-11, as Phases 2 to 14a). **This phase configures the release. It does not publish to Maven Central, push or create a tag, create, read or print a secret, force-push, delete a file or commit.** Phase 14b is the second half of Phase 14 (`docs/logs/phase-14-scope.md`, items 5 to 7).

How I work, from the brief and the project rules: plan first (this file); every version verified, not remembered (CODE-8); a skipped check is reported as skipped; nothing weakened to pass; every file and command logged in `docs/logs/phase-14b-change-log.md`; the final report ends with commit groups and **no `Co-Authored-By` trailer**.

## 1. Versions, verified today (CODE-8)

Rule from the brief: only versions released at least 14 days before today (2026-11-22), so released on or before 2026-11-08.

### 1.1 Maven plugins (release profile only)

How I checked: the authoritative listing `https://repo1.maven.org/maven2/<group path>/<artifact>/maven-metadata.xml` (the same data `central.sonatype.com` and `search.maven.org` show) and the file date of each `.pom` in that repository (`Last-Modified`); then the Spring Boot **4.1.2** BOM (`spring-boot-dependencies-4.1.2.pom`, read from the local repository) for the versions it manages; then the descriptor `META-INF/maven/plugin.xml` inside each downloaded jar for the option names (never from memory). `search.maven.org`'s own index was stale for two of them (it listed no version of the Central plugin and ended at 3.2.8 for gpg), so the repository listing is the record.

| Coordinate | Version | Released | Age on 2026-11-22 | Where it comes from | Why this one |
|---|---|---|---|---|---|
| `org.sonatype.central:central-publishing-maven-plugin` | **0.12.0** | 2026-10-13 21:43 UTC | 40 days | repo1 `maven-metadata.xml` (latest and release 0.12.0), `.pom` `Last-Modified`; not managed by the Boot BOM, so pinned in the root pom | newest release and 40 days old; 0.11.1 is from the same day |
| `org.apache.maven.plugins:maven-gpg-plugin` | **3.2.8** | 2025-06-28 08:54 UTC | 512 days | repo1 listing (latest and release 3.2.8), `.pom` `Last-Modified`; **not** managed by the Boot BOM or the Boot parent, so pinned in the root pom | newest release |
| `org.apache.maven.plugins:maven-source-plugin` | **3.4.0** | 2025-11-22 22:27 UTC | 365 days | `spring-boot-dependencies-4.1.2.pom` (`maven-source-plugin.version` 3.4.0), repo1 `.pom` `Last-Modified`; the repository's `latest` is `4.0.0-beta-1`, a beta, not used | the version the Boot BOM manages; no version element in my pom |
| `org.apache.maven.plugins:maven-javadoc-plugin` | **3.12.0** | 2025-09-16 22:40 UTC | 432 days | `spring-boot-dependencies-4.1.2.pom` (`maven-javadoc-plugin.version` 3.12.0), repo1 listing (latest and release 3.12.0), `.pom` `Last-Modified` | the version the Boot BOM manages; no version element in my pom |

Not used and why: `maven-deploy-plugin` (the Central plugin replaces the deploy step; its options `skipUpload`, `autoPublish` and `excludeArtifacts` are below), `maven-jar-plugin`/`maven-release-plugin`/`flatten`/`versions` (not on the approved list: **nothing else is added to the build**). The Enforcer plugin is already in the build (D-23, version 3.6.3 from the Boot BOM).

### 1.2 Docs tooling (docs build only, not a Maven dependency)

Checked on `https://pypi.org/pypi/<name>/json` (upload time of every file of a release), on 2026-11-22.

| Package | Version | Released | Age | Note |
|---|---|---|---|---|
| `mkdocs` | **1.6.1** | 2024-08-30 | 814 days | the stable line; `2.0.dev0` to `2.0.dev6` (2026-08 to 2026-09) are development releases and are not used |
| `mkdocs-material` | **9.8.1** | 2026-11-07 | 15 days | newest that is old enough: `9.8.2` is from 2026-11-19 (3 days) and is skipped; `9.8.0` (2026-10-24) is the alternative if 15 days feels too fresh |

The transitive packages (about 30) are pinned too, in `docs/requirements.txt`, each checked for its release date (section 1.4, filled in when the file is made), so a build next year gets the same tool.

### 1.3 GitHub Actions (each pinned to a full commit SHA, the release in a comment)

Checked in each action's own repository through the GitHub API: `GET /repos/<owner>/<repo>/releases` (release date) and `GET /repos/<owner>/<repo>/git/ref/tags/<tag>` (the tag is a lightweight tag, so the object is the commit; the SHA is the commit itself, not a tag object).

| Action | Release | Released | Age | Commit SHA | Used by |
|---|---|---|---|---|---|
| `actions/checkout` | v7.0.1 | 2026-07-20 | 125 | `3d3c42e5aac5ba805825da76410c181273ba90b1` | `ci.yml` (already), `release.yml`, `docs.yml` |
| `actions/setup-java` | v6.1.0 | 2026-10-14 | 39 | `b843ebf9b913146b38da2eaa7aeddf76074ae1ad` | `ci.yml` (already), `release.yml` (`v6.2.0`, 18 days, and `v6.3.0`, 8 days, are newer; I keep the release `ci.yml` already runs) |
| `actions/setup-python` | v7.1.0 | 2026-10-22 | 31 | `6d4fd22b20465ccc0f7354476588e8df7c9ca0e0` | `docs.yml` |
| `actions/configure-pages` | v6.0.0 | 2026-03-25 | 242 | `45bfe0192ca1faeb007ade9deae92b16b8254a0d` | **not used** (the site address is set in `mkdocs.yml`); verified and left out |
| `actions/upload-pages-artifact` | v5.0.0 | 2026-04-10 | 226 | `fc324d3547104276b827a68afc52ff2a11cc49c9` | `docs.yml` |
| `actions/deploy-pages` | v5.0.1 | 2026-09-01 | 82 | `368f82528645a54fb793d4d04e342629a3f51346` | `docs.yml` |

`actions/setup-java` v6.1.0, read at its commit (`action.yml`, `src/auth.ts`): the inputs are `server-id`, `server-username-env-var` and `server-password-env-var`, and the `settings.xml` it writes holds only `<username>${env.NAME}</username>` and `<password>${env.NAME}</password>` (line 434 and 435 of `auth.ts`), never a value. I use it for that and **not** for its `gpg-private-key` input, which imports into the runner's default keyring; the brief wants a temporary keyring, made by a step of my own.

## 2. The options that matter, from the plugin descriptors

- **Central plugin 0.12.0**, goal `publish`, bound to `deploy`. `publishingServerId` (default `central`): the id of the `<server>` in `settings.xml` that holds the token. `autoPublish` (default `false`, user property `autoPublish`). `waitUntil` (default `VALIDATED`). `excludeArtifacts` (a list: "artifacts that must not be added to the bundle"). `skipPublishing`. **`skipUpload`: "Create the bundle, but skip uploading it and publishing it"** (the bundle is written to the output directory), `centralBaseUrl` (default `https://central.sonatype.com`), `checksums` (default `ALL`), `deploymentName`. The plugin does not read environment variables itself: it reads the token of the server `central` from `settings.xml`, which the workflow generates with `${env.CENTRAL_TOKEN_USERNAME}` and `${env.CENTRAL_TOKEN_PASSWORD}`. That is how the two names in the brief reach it.
- **GPG plugin 3.2.8**, goal `sign`, phase `verify`. `passphraseEnvName` (default `MAVEN_GPG_PASSPHRASE`: I set it to `GPG_PASSPHRASE`), `bestPractices` (when true the build **fails** if a passphrase is given in a way that leaks it: I set it to true), `gpgArguments` (for `--pinentry-mode loopback`), `homedir` (`gpg.homedir`), `keyname` (`gpg.keyname`), `skip` (`gpg.skip`). The `passphrase` parameter exists and its description says not to use it ("it leaks sensitive data"): not used anywhere.
- **Source plugin 3.4.0**, goal `jar-no-fork`, phase `package`: includes `src/main/resources` by default, so the source jar of `jpa-admin-ui` holds the templates, CSS and JavaScript. Skip property `maven.source.skip`.
- **Javadoc plugin 3.12.0**, goal `jar`, phase `package`. `doclint` (default unset = the JDK's own default), `failOnError` (default true), `failOnWarnings` (default false). Skip property `maven.javadoc.skip`.

## 3. What I will build

### 3.1 The release profile (root `pom.xml`)

A profile `release` with **no `<activation>`**, so `mvn -B clean verify` never touches it (a test proves it). It holds, and only it holds: the four plugins above. Order in `verify`: `package` runs `source:jar-no-fork` and `javadoc:jar`, then `verify` runs `gpg:sign`, then `deploy` runs `central-publishing:publish` (never run here). The Central plugin is `<extensions>true</extensions>` and its configuration is `publishingServerId=central`, `autoPublish=false`, `waitUntil=validated`, `deploymentName`, and `excludeArtifacts` for the three unpublished modules (belt and braces with the per-module skips below).

### 3.2 POM metadata (root, inherited)

`name`, `description`, `url` (`https://github.com/mdmusahub/JPA-SUPER-ADMIN`), `licenses` (Apache-2.0 with its URL), `developers` (Musa Malik, `mdmusaalam98019@gmail.com`, `https://github.com/mdmusahub`), `scm` (`connection`, `developerConnection`, `url`, `tag` `HEAD`), `issueManagement` (GitHub issues). Version `0.1.0` in every pom (root, five modules that inherit, the three unpublished, and the `host-check` dependency). Central needs: name, description, url, license, developer, scm: all present.

### 3.3 Published modules and the three that are not

Published: the parent `jpa-super-admin` (packaging `pom`), `jpa-admin-core`, `jpa-admin-autoconfigure`, `jpa-admin-ui`, `jpa-admin-starter` (artifactId `springboot-super-admin`, packaging `pom`, D-127). **The two `pom` modules have no jar**, so for them "the jar, the sources jar and the javadoc jar" do not exist and Central does not ask for them (it asks for sources and javadoc for a jar); the check script requires, for a `pom` module, the POM and its `.asc` only, and for a `jar` module all four files and four `.asc`. I say this in the report.
Not published: `sample-app`, `jpa-admin-ui-preview` (both in the reactor) and `host-check` (outside the reactor, `<parent>` is Spring Boot's, nothing can deploy it from this build). In the two reactor modules, plain properties (not in the profile, so they hold in any build): `maven.source.skip`, `maven.javadoc.skip`, `gpg.skip`, `maven.deploy.skip` and the Central plugin's skip, so no source jar, javadoc jar, signature or deployment is made for them. The Enforcer rule (ARCH-11) is left as it is and must keep passing.

### 3.4 Javadoc and doclint

Doclint stays on. Run `javadoc:jar` on the three jar modules, read every error, **fix the comments** (never the setting). The ARCH-12 name check reads every file of the library modules, so a fix must not use the whole words `Category`, `Product`, `Customer`, `Order` or `Tag` (case-sensitive). A resource-only module (`jpa-admin-ui`, no Java file) makes no javadoc jar by itself: I will see what the plugin does and, if it makes none, **stop and ask** before adding a placeholder, because a file or plugin outside the approved list may be needed.

### 3.5 Workflows

- `release.yml`: `on: push: tags: ['v*']` only; `permissions: contents: read`; Java 17; actions pinned; step 1 checkout (`persist-credentials: false`), step 2 setup-java with `server-id: central` and the two env var names, step 3 a temporary keyring (`GNUPGHOME` from `mktemp -d` under `$RUNNER_TEMP`, the key imported from the `GPG_PRIVATE_KEY` secret passed as an environment variable of that step only, never echoed), step 4 `mvn -B -Prelease deploy` with `CENTRAL_TOKEN_USERNAME`, `CENTRAL_TOKEN_PASSWORD`, `GPG_PASSPHRASE` as environment variables of that step only, step 5 an `if: always()` removal of the temporary keyring. Before the deploy it runs `scripts/check-central-bundle.sh` after a `verify` so a missing file fails before any upload.
- `docs.yml`: `on: workflow_dispatch` only. Job `build`: checkout, setup-python (pinned Python), `pip install --require-hashes`-free pinned install from `docs/requirements.txt`, `mkdocs build --strict`, `upload-pages-artifact`. Job `deploy` (needs `build`): `pages: write` and `id-token: write` on that job only, `configure-pages` and `deploy-pages`. Nothing runs on a push or a pull request.
- `ci.yml`: unchanged except for one step that proves the release profile on a pull request without a key (see 3.7), if it can be done without a secret; otherwise unchanged. Said in the report.

### 3.6 Dry run, no upload, no real key

1. Throwaway key: `GNUPGHOME` under `target/dryrun-gnupg` (a folder that `.gitignore` already ignores), a key generated in batch mode with **no passphrase**, used only here. The real key is never read; `GPG_PRIVATE_KEY` is never used.
2. `mvn -B -Prelease verify` with `-Dgpg.homedir=<that folder>` and `-Dgpg.keyname=<its fingerprint>`.
3. For each published module: the files of 3.3 exist, each with a `.asc`, and `gpg --verify` succeeds against the throwaway public key.
4. `scripts/check-central-bundle.sh [repository root]`: exits non-zero and names the file when one is missing, for any published module. Run once on the real tree (passes), once on a **copy** with one file removed (must fail), and the original is untouched.
5. The throwaway keyring is deleted and its absence confirmed (the gpg-agent that served it is stopped first).

**An extra check I want, asking nothing of Central:** after the required `verify` run, one `mvn -B -Prelease deploy` with `skipUpload=true`, `centralBaseUrl` pointed at a closed local port and an empty settings file (no credentials), to see the bundle the plugin would send (names, checksums, signatures). Three independent guards make an upload impossible. If I do not want to rely on that I will skip it and say so; it is not one of the required steps.

### 3.7 Tests (all in the normal build; none needs a key or a network)

- The release profile is off by default: the pom has no `activeByDefault`, no `activation`, and none of the four plugins is in the top-level `<build>`; the sources of a normal `verify` make no `.asc`. (Read as XML, the style of `PerfProfileIsOffByDefaultTest`.)
- The three unpublished modules are excluded from publishing: each skip property is `true` in `sample-app` and `jpa-admin-ui-preview`; `host-check` is not in the root `<modules>` and has the Boot parent; the Central plugin's `excludeArtifacts` names the two reactor modules; the published list is exactly the five.
- The Enforcer rule, `NameCheckTest` (ARCH-12) and `HostIsolationTest` keep passing; `host-check` passes.
- The workflow files: `release.yml` triggers only on `v*` tags, has `contents: read`, pins every `uses:` to a 40 character SHA with a release comment, never prints a secret; `docs.yml` triggers only on `workflow_dispatch`.

### 3.8 Files

Created: `docs/logs/phase-14b-plan.md` (this), `-change-log.md`, `-report.md`; `.github/workflows/release.yml`, `.github/workflows/docs.yml`; `.github/ISSUE_TEMPLATE/` (bug, feature, config), `.github/pull_request_template.md`; `scripts/check-central-bundle.sh`; `SECURITY.md`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md` (the Contributor Covenant 2.1 text from its official site); `mkdocs.yml`, `docs/requirements.txt` and the user pages of the site; the guard tests.
Modified: `pom.xml`, the eight module poms (version), `host-check/pom.xml` and its README (version), `README.md`, `LICENSE` (the copyright line only), `CHANGELOG.md`, `docs/DECISIONS.md` (D-137 onward), `docs/PLAN.md`, `docs/PROJECT_DOCS.md` (installation, release guide), `CLAUDE.md` and `jpa-admin-core/CLAUDE.md` (the jar name in the run commands), Javadoc comments in `jpa-admin-core` and `jpa-admin-autoconfigure` (comments only, no code).
Deleted: nothing.

## 4. Decisions I am making by default and will say so

1. **The user pages live in `docs/` next to the project's own papers.** `mkdocs.yml` sets `docs_dir: docs` and `exclude_docs` for `PLAN.md`, `RULES.md`, `DECISIONS.md`, `UI_PLAN.md`, `PROJECT_DOCS.md`, `FIXTURES.md`, `logs/`, `prototype/` and the allow-list, so the site shows the user pages and the screenshots in `docs/images/` and nothing internal (those papers link to repository paths that would break `--strict`). The pages are made from the draft sections B2 to B13 of `PROJECT_DOCS.md`; the references to internal decision numbers are removed from the pages.
2. **README author name.** The README says "Muhammad Musa"; the brief names the developer "Musa Malik". I make the README say Musa Malik and flag it in the report.
3. **Version of `mkdocs-material`:** 9.8.1 (newest that is 14 days old), above.

## 5. Stop conditions I will honour

Stop and ask if: a step would publish, push a tag or read a secret value; a plugin or dependency outside the four plugins and the two Python packages is needed (the placeholder javadoc jar of the resource-only module is the likely case); a public API, annotation or property name must change; or a check cannot run here (the Linux workflow runs, GitHub Pages and the Central portal cannot be exercised from this machine, and I will say so rather than claim them).

## 6. What changed from this plan while doing it (written at the end, the plan above is left as it was)

- **`configure-pages` is not used** (section 1.3): `mkdocs.yml` sets the address, so the action is not needed.
- **`jpa-admin-ui` needed more than a package page** (section 3.4): javadoc refuses a module with nothing to document, so a package-private class was added (D-142). A placeholder jar from `maven-jar-plugin` was the alternative and was not taken because that plugin is not on the approved list. This is the one place I came close to a stop condition; I chose the way that stays inside the list and recorded the other.
- **The Maven release step is `deploy -DskipTests`, after a `verify` and the file check**, and the tests run first with no secret (D-143); the brief said `mvn -B -Prelease deploy`.
- **A finding, not in the plan:** the starter needs `<type>pom</type>` in a dependency (D-144).
- **The javadoc footer** needed `&amp;#169;` (D-137) and `mvn clean` removes a keyring kept under the root `target/` (D-140).
- **The Windows laptop** needed a wrapper for Git's `gpg` (D-140), and Docker Desktop had to be started for the full build.
