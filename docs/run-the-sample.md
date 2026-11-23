# Run the sample

The repository contains a small demo shop (`sample-app`) with products, categories, customers and orders. It is the quickest way to see the console with data in it, and the application the project's integration and browser tests run against.

You need **Java 17 or newer** and **Maven**. Get the source:

```
git clone https://github.com/mdmusahub/JPA-SUPER-ADMIN.git
cd JPA-SUPER-ADMIN
```

The sample deliberately has no admin credentials in its configuration, so you set the two required properties yourself (a password of 12 or more characters). Build the jar, set the variables, run it, then open <http://localhost:8080/admin>. Stop it with Ctrl+C.

=== "PowerShell"

    ```powershell
    mvn -pl sample-app -am package -DskipTests
    $env:JPA_ADMIN_USERNAME = 'admin'
    $env:JPA_ADMIN_PASSWORD = '<12 or more characters>'    # this PowerShell session only
    java -jar sample-app/target/sample-app-0.1.0.jar
    ```

=== "bash"

    ```bash
    mvn -pl sample-app -am package -DskipTests
    export JPA_ADMIN_USERNAME=admin
    export JPA_ADMIN_PASSWORD='<12 or more characters>'
    java -jar sample-app/target/sample-app-0.1.0.jar
    ```

!!! note "Do not use `spring-boot:run` without `-am`"
    `mvn -pl sample-app spring-boot:run` takes the other modules from your local Maven repository, where an older `mvn install` can leave stale jars, so it can run old code (the symptom is a 404 at `/admin`). The jar built above is made from the current sources.

The demo uses an in-memory H2 database that is filled at every start. Nothing you change is kept.
