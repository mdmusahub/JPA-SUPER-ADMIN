# host-check

A second, unrelated Spring Boot application that adds **only** the JPA Super Admin starter and the two required
properties, with its own entities (`Book`, `Author`, `Publisher`). It proves the library is entity-agnostic
(ARCH-12, D-26): if the console had hard-coded the demo shop's entities, or assumed anything about the host's model,
this project would not show the right sidebar.

It is **outside the reactor build** on purpose: it is not in the root `pom.xml`'s `<modules>`, and its parent is
`spring-boot-starter-parent`, not this project's parent. It depends on the published artifact
`jpa-super-admin-spring-boot-starter:0.1.0-SNAPSHOT`, which must be in your local Maven repository first.

## Build and test it (PowerShell)

Maven must be on your `PATH`. From the repository root:

```powershell
# 1. Put the snapshot into the local Maven repository (the reactor, no tests needed for this step).
mvn -B -DskipTests install

# 2. Build and test host-check against it.
mvn -B -f host-check/pom.xml verify
```

The test `ConsoleListsThisHostsEntitiesTest` signs in as the console admin (credentials from
`src/test/resources/application.properties`, which are fake and protect nothing) and asserts the sidebar lists
exactly `Book`, `Author` and `Publisher` with the seeded counts (5 books, 3 authors, 2 publishers) and nothing of
the demo shop.

## Run it by hand and open it in a browser (PowerShell)

```powershell
mvn -B -DskipTests install
mvn -B -f host-check/pom.xml -DskipTests package
$env:JPA_ADMIN_USERNAME = 'admin'
$env:JPA_ADMIN_PASSWORD = '<12 or more characters>'    # this PowerShell session only
java -jar host-check/target/host-check-1.0.0-SNAPSHOT.jar
```

Then open <http://localhost:8090/admin>, sign in, and you should see `Book`, `Author` and `Publisher` in the
sidebar with their counts. Stop it with Ctrl+C.

bash is the same with `export JPA_ADMIN_USERNAME=admin` and `export JPA_ADMIN_PASSWORD='<12 or more characters>'`.
