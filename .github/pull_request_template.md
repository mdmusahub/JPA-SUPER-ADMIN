## What this changes and why

<!-- One purpose per pull request. Link the issue it closes ("Closes #123") if there is one. -->

## How you checked it

<!-- The commands you ran and what you saw, or the test that now fails without the change. -->

## Checklist

- [ ] `mvn -B clean verify` passes (and, after `mvn -B -DskipTests install`, `mvn -B -f host-check/pom.xml verify`)
- [ ] New behaviour has a test in the same change; anything that touches security has a test that shows the bad input is refused
- [ ] No sensitive value (password, token, key) can reach a page, an export, a log or an error message because of this change
- [ ] User-visible change: `CHANGELOG.md` (under `[Unreleased]`) and the documentation are updated; a new property or annotation option is documented with its default
- [ ] No new dependency, or the reason is written above (the build and the Spring Boot libraries could not do the job)
- [ ] No change to a public annotation or property name, or it is agreed in an issue first
- [ ] I read [CONTRIBUTING.md](../blob/main/CONTRIBUTING.md) and the rules it points to, and I follow the [Code of Conduct](../blob/main/CODE_OF_CONDUCT.md)

<!-- Security problems are not reported here: see SECURITY.md. -->
