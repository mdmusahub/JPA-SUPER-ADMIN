# Development

How to build the project, run the tests and work on the documentation. The contribution rules are in [CONTRIBUTING.md](https://github.com/mdmusahub/JPA-SUPER-ADMIN/blob/main/CONTRIBUTING.md).

## Build and test

You need **JDK 17** and **Maven 3.6.3 or newer**.

```
mvn -B clean verify                      # build everything and run every test (about 20 minutes)
mvn -B -pl jpa-admin-core test           # one module
mvn -B -pl sample-app -am test           # the demo application and what it needs
```

Two groups of tests need something on the machine. They are **skipped with their reason** when it is missing (a skipped test is never reported as passed), and **fail** on continuous integration:

| Tests | Need | Switch that makes a missing one fail |
|---|---|---|
| The same scenarios on H2, PostgreSQL and MySQL | Docker (the two databases run in containers) | `JPA_ADMIN_REQUIRE_ENGINES=true` |
| Real-browser tests of the layout, contrast, accessibility, the content security policy and behaviour | Chrome or Edge (or `-Dtest.chrome=<path>`, or `CHROME_BIN`) | `JPA_ADMIN_REQUIRE_BROWSER=true` |

Measurements on 200,000 rows are never part of a normal build: `mvn -B -pl jpa-admin-core -Pperf test`.

## The host check

`host-check/` is a separate project (not part of the reactor) with its own entities. It depends on the installed starter exactly as a user would, and proves the library is not tied to the demo's entities:

```
mvn -B -DskipTests install
mvn -B -f host-check/pom.xml verify
```

`mvn install` puts version 0.1.0 into your local Maven repository. When you later want the released version from Central, delete `~/.m2/repository/io/github/mdmusahub` first, so Maven does not use your own build.

## Run the demo

See [Run the sample](run-the-sample.md).

## The release profile

A normal build never signs and never publishes. The profile `release` (the only place the signing, source, Javadoc and publishing plugins appear) is switched on only with `-Prelease`. How to check it without publishing anything is in the [Release guide](release-guide.md).

## Work on the documentation

The site is made with [MkDocs](https://www.mkdocs.org/) and the Material theme, from the pages in `docs/` listed in `mkdocs.yml` (the project's own papers in the same folder are left out of the site). The tools are pinned, with hashes, in `docs/requirements.txt`; they are not a Maven dependency.

```
python -m venv .venv
.venv/bin/python -m pip install --require-hashes --no-deps -r docs/requirements.txt    # Windows: .venv\Scripts\python.exe
.venv/bin/mkdocs serve                  # preview at http://127.0.0.1:8000
.venv/bin/mkdocs build --strict         # the same build the workflow runs: a broken link fails it
```

The site is published only by hand: **Actions, Docs, Run workflow**. Nothing publishes it on a push.
