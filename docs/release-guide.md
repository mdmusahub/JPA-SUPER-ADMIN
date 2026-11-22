# Release guide

For the maintainer. A release is **a tag**: pushing `v0.1.0` starts the release workflow, which builds and tests that commit, signs every published module, checks that Maven Central has every file it asks for, and uploads one bundle. **Nothing becomes public until you press Publish on the Central portal.**

## What is published

Five artifacts, all `io.github.mdmusahub`:

| Artifact | Packaging | Files Central receives |
|---|---|---|
| `jpa-super-admin` (the parent) | pom | the pom |
| `jpa-admin-core` | jar | jar, sources jar, javadoc jar, pom |
| `jpa-admin-ui` | jar | jar, sources jar, javadoc jar, pom |
| `jpa-admin-autoconfigure` | jar | jar, sources jar, javadoc jar, pom |
| `springboot-super-admin` (the starter) | pom | the pom |

Each file has a `.asc` signature, and the Central plugin adds the checksums. `sample-app`, `jpa-admin-ui-preview` and `host-check` are **never** published: the first two switch every release step off in their own poms and are named in the Central plugin's exclusion list, and `host-check` is not part of the build at all. A test reads all of that.

## One-time setup

1. **Maven Central account and namespace.** Sign in at <https://central.sonatype.com> and verify the namespace `io.github.mdmusahub` (a GitHub-based namespace is verified by creating a temporary public repository with the name the portal shows you). Also check that the artifact name `springboot-super-admin` is still free.
2. **A user token.** On the portal, **Account, Generate User Token**. It gives a username and a password: these are *not* your portal login.
3. **A signing key.** Create one (`gpg --full-generate-key`, with a passphrase) and publish the public key to a key server Central reads, for example `gpg --keyserver keyserver.ubuntu.com --send-keys <KEY-ID>`. Keep the secret key and the passphrase out of the repository, forever.
4. **Four repository secrets** (GitHub, **Settings, Secrets and variables, Actions, New repository secret**). The workflow reads exactly these names:

    | Secret | Holds |
    |---|---|
    | `GPG_PRIVATE_KEY` | the armored secret key (`gpg --armor --export-secret-keys <KEY-ID>`, pasted into the box) |
    | `GPG_PASSPHRASE` | its passphrase |
    | `CENTRAL_TOKEN_USERNAME` | the token's username |
    | `CENTRAL_TOKEN_PASSWORD` | the token's password |

5. **GitHub Pages.** **Settings, Pages, Source: GitHub Actions.**
6. **Private vulnerability reporting.** **Settings, Code security, Private vulnerability reporting: Enable.** (`SECURITY.md` points people to it.)

## Before every release

- CI is green on `main` (both Java 17 and 21).
- Every `pom.xml` is on the release version (no `-SNAPSHOT`), and the tag will be exactly `v` plus that version: the workflow checks this and stops if they differ.
- In `CHANGELOG.md` the `[Unreleased]` section becomes `[x.y.z] - date`, and a new empty `[Unreleased]` is added above it.
- The manual checks that no automated test can make (a screen reader, a physical keyboard and phone, a file dropped onto the upload area, print, a private window) are done.
- The dry run below passes.

## Dry run (uploads nothing, uses no key of yours)

This builds the release exactly as the workflow does, signed with a **throwaway** key that is deleted afterwards. Use a folder under `target/` (the root `target/` folder is removed by `mvn clean`, so do not run `clean` between the steps):

```
mvn -B clean
export GNUPGHOME="$PWD/target/dryrun-gnupg" && mkdir -p "$GNUPGHOME" && chmod 700 "$GNUPGHOME"
gpg --batch --passphrase '' --quick-generate-key "Dry run <dry-run@invalid.example>" ed25519 sign 1d
mvn -B -Prelease verify -Dgpg.keyname=<fingerprint of that key>
bash scripts/check-central-bundle.sh --verify
gpgconf --kill gpg-agent && rm -rf "$GNUPGHOME"
```

Keep the keyring folder's path short: gpg's agent talks over a socket file in that folder, and the path of a socket is limited to about 100 characters (a long path fails with "No agent running").

`scripts/check-central-bundle.sh` fails, and names the file, if any published module is missing its jar, sources jar, javadoc jar or pom, or any signature, or if an unpublished module left a file that could be uploaded. `--verify` also runs `gpg --verify` on every signature.

To see the bundle the Central plugin would upload without sending it, add `-DskipUpload=true`, point the plugin at an address that cannot be reached, and use an empty settings file (no credentials): `mvn -B -s empty-settings.xml -Prelease deploy -DskipTests -DskipUpload=true -DcentralBaseUrl=http://127.0.0.1:9`. The bundle is written to `target/central-publishing/central-bundle.zip`.

!!! note "On Windows"
    Git for Windows' `gpg` accepts only POSIX-style paths (`/c/Users/...`), while Maven passes the plugin's `gpg.homedir` as a Windows path. Either run the dry run in WSL, or give the plugin a one-line wrapper script with `-Dgpg.executable=...` that calls `gpg --homedir /c/path/to/the/keyring %*`.

## Release

```
git checkout main && git pull
git tag -a v0.1.0 -m "JPA Super Admin 0.1.0"
git push origin v0.1.0
```

1. Open the **Actions** tab and watch **Release**. It runs the whole test suite first (about 20 minutes), then signs, checks and uploads.
2. On the Central portal, open **Deployments**. The upload appears there as *validated* and **waits**. Look at the files it lists, then press **Publish**.
3. Central syncs the release to its repository after a short wait. The coordinate then works for everyone: `io.github.mdmusahub:springboot-super-admin:0.1.0` (with `<type>pom</type>`).
4. Run **Actions, Docs, Run workflow** to publish the documentation site.

### What `autoPublish=false` means

The Central plugin is configured with `autoPublish` set to `false`. After the upload, Central runs its checks (signatures, checksums, required metadata, sources and javadoc) and, if they pass, **holds the release in a staging area and does nothing more** until a person presses **Publish** on the portal. If you do not press it, nothing is public; if you press **Drop**, the upload is thrown away and you can fix the problem and push the tag again. With `autoPublish` set to `true` the release would go public by itself as soon as the checks pass, and a mistake could not be taken back: **a version that is public on Maven Central can never be changed or removed.**

## If something fails

| Where | What it means |
|---|---|
| "Check that the tag is the version of the project" | The tag and the pom disagree, or the pom still says `-SNAPSHOT`. Nothing was built. Fix the pom, delete the tag, tag again |
| "Build and test" | A test failed (or Docker or Chrome was missing: the release requires both). Nothing was signed or uploaded. Fix it on `main`, move the tag |
| "Import the signing key" | The `GPG_PRIVATE_KEY` secret is empty or not an armored secret key |
| "Sign, then check" | A wrong passphrase, or a file is missing (the script names it) |
| "Upload the bundle" | Central refused the bundle (the log says which check). Nothing was published. Fix and push the tag again |

If a tag was pushed and **nothing reached Central**, you may delete the tag (`git push origin :refs/tags/v0.1.0`, then `git tag -d v0.1.0`) and tag again. Once a version is published, never reuse its number: release `0.1.1`.
