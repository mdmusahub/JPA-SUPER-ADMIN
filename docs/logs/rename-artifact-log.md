# Rename log: published starter artifact

Branch `chore/rename-artifact`, from `main` (tag `v0.1.0-rc.13`). Rename only. Nothing is committed. The published starter artifact is now `io.github.mdmusahub:springboot-super-admin` (was `jpa-super-admin-spring-boot-starter`). Decision: D-127.

## Classification of every hit

`git grep -n "jpa-super-admin-spring-boot-starter"` (11 files, 12 lines):

| File and line | Class | Action |
|---|---|---|
| `jpa-admin-starter/pom.xml:13` (artifactId) | published coordinate | changed |
| `pom.xml:62` (managed dependency, `type pom`) | published coordinate | changed |
| `sample-app/pom.xml:22` (dependency) | published coordinate | changed |
| `host-check/pom.xml:40` (dependency) | published coordinate | changed |
| `host-check/README.md:10` | published coordinate | changed |
| `README.md:33` (quick start) | published coordinate | changed |
| `docs/PROJECT_DOCS.md:242` (installation) | published coordinate | changed |
| `docs/PLAN.md:75`, `:81`, `:489` (module tree, "the only artifact users add", decisions list) | published name in the current plan | changed (`:489` also points to D-127) |
| `CLAUDE.md:35` (repository layout) | published name in the project instructions | changed (not on your list, but it is the same name) |
| `CHANGELOG.md:206` (the `[0.0.1]` release entry) | historical | kept |
| `docs/DECISIONS.md:9` (D-1) | historical decision | kept; D-127 supersedes its artifactId |

`git grep -n "jpa-super-admin"` (the rest, 17 lines): the parent artifactId and its uses, all **kept**: `pom.xml:15`; `<parent>` blocks in `jpa-admin-core`, `jpa-admin-ui`, `jpa-admin-autoconfigure`, `jpa-admin-ui-preview`, `jpa-admin-starter` and `sample-app` (line 9 of each pom); `host-check/pom.xml:4` (a comment that says the host's parent is NOT `jpa-super-admin`); `CLAUDE.md:42`, `docs/DECISIONS.md:10` (D-2) and `docs/PLAN.md:69` (the parent's name and the root folder).

No file under `docs/logs/` contained either string, so there was no historical log entry to preserve. `docs/logs/phase-14-scope.md` had no old coordinate either; I **added** the new coordinate and the namespace check to its Release item (you listed the file), so please say if you wanted nothing added there. No Java file, template, stylesheet or script contained the old coordinate, so no test string needed to change.

## Kept on purpose

The parent artifactId `jpa-super-admin` (D-2), every module folder (`jpa-admin-core`, `jpa-admin-autoconfigure`, `jpa-admin-ui`, `jpa-admin-ui-preview`, `jpa-admin-starter`, `sample-app`), the package `io.github.mdmusahub.jpaadmin`, the groupId, the project name "JPA Super Admin" and the starter module's display name `<name>JPA Super Admin Spring Boot Starter</name>` (a display name, not the artifactId).

## Files and commands

| # | Action | Path or command | Result |
|---|---|---|---|
| 1 | command | `git branch --show-current`, `git describe --tags --always` | `chore/rename-artifact`, `v0.1.0-rc.13`, clean tree |
| 2 | command | `git grep -n "jpa-super-admin-spring-boot-starter"` and `git grep -n "jpa-super-admin"` | the lists above |
| 3 | read | the five pom sections, `host-check/README.md`, `docs/PROJECT_DOCS.md` (B3), `CHANGELOG.md` (Unreleased), the tail of `docs/DECISIONS.md`, `.github/workflows/ci.yml` (no hit) | |
| 4 | edit | `sed -i` (old name to new name) in `jpa-admin-starter/pom.xml`, `pom.xml`, `sample-app/pom.xml`, `host-check/pom.xml`, `host-check/README.md`, `README.md`, `docs/PROJECT_DOCS.md`, `docs/PLAN.md` (3), `CLAUDE.md` | 10 replacements; line endings still LF |
| 5 | edit | `docs/logs/phase-14-scope.md` (Release item), `docs/PLAN.md:489` (pointer to D-127), `docs/DECISIONS.md` (new row D-127, next free number), `CHANGELOG.md` (one line under `[Unreleased]` / Changed) | a throwaway Java helper outside the repo that fails if a text is missing or not unique |
| 6 | command | `mvn -B clean verify` | **BUILD SUCCESS**: core 4033 (1 skipped), autoconfigure 581, preview 2788, sample-app 1039 (0 skipped), **8441** in the reactor, the same as Phase 13; the starter built as `io.github.mdmusahub:springboot-super-admin` |
| 7 | command | `mvn -B -DskipTests install` | **BUILD SUCCESS** (installs `springboot-super-admin`) |
| 8 | command | `mvn -B -f host-check/pom.xml verify` | **BUILD SUCCESS**, **37** tests, the same as Phase 13; the host depends on the new coordinate |
| 9 | command | `git status --short`, `git diff --stat` | in the report in chat |
| 10 | create | `docs/logs/rename-artifact-log.md` | this file |

Note: the local Maven repository (`~/.m2`) still holds the old `jpa-super-admin-spring-boot-starter` 0.1.0-SNAPSHOT from earlier installs. It is harmless (nothing refers to it now); I did not delete it because it is outside the repository.
