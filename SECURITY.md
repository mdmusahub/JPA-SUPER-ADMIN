# Security policy

JPA Super Admin is an admin console: it can read and change every record of the application that hosts it. A security problem in it is serious, and reports are welcome and taken seriously.

## Supported versions

| Version | Supported |
|---|---|
| 0.1.x | Yes (security fixes) |
| Anything older (a snapshot or a release candidate, `0.1.0-rc.*`) | No |

The console targets Spring Boot 4.1.x and Java 17 or newer. A problem that only exists in an unsupported Spring Boot version is outside what this project can fix.

## How to report a vulnerability

**Please do not open a public issue, a pull request or a discussion for a security problem.** Use one of these two private ways:

1. **GitHub private vulnerability reporting (preferred).** Open the repository's **Security** tab, choose **Report a vulnerability**, and fill in the form: <https://github.com/mdmusahub/JPA-SUPER-ADMIN/security/advisories/new>. Only the maintainer and you can see it.
2. **Email** [mdmusaalam98019@gmail.com](mailto:mdmusaalam98019@gmail.com) with the subject `JPA Super Admin security`.

Please include, as far as you can:

- what the problem is and which rule of the console it breaks (for example "a sensitive field shows in the CSV export");
- the version of the starter (`io.github.mdmusahub:springboot-super-admin`), the Spring Boot version and the Java version;
- the smallest set of steps, a request or a test that shows it;
- what an attacker gains, and whether the console had to be reachable from the internet or only from a trusted network.

Do not include a real password, key or customer record in a report. A made-up example is enough.

## What happens next

This is a small project with one maintainer, so there is no service-level promise, but a report is read by a person. The maintainer will confirm that it arrived, say whether it is accepted as a vulnerability, work on a fix with you in private, publish the fixed version, and then publish a GitHub security advisory that credits you (unless you prefer not to be named). Please give a reasonable time to fix a problem before you tell anyone else.

## What is in scope

The promises the console makes (they are listed as rules SEC-1 to SEC-15 and UPL-1 to UPL-10 in [docs/RULES.md](docs/RULES.md), and each has a test):

- a sensitive field (a name in `jpa.admin.sensitive-fields` or `@AdminSensitive`) appearing in any page, export, log or error message;
- the console reading or changing something it was configured to hide or forbid (a hidden entity, a read-only entity, an export that is switched off), or an unknown sort, search or filter name reaching a query;
- a way past the sign-in, the CSRF protection or the console's own security chain, or a change to the host application's own security rules;
- an uploaded file that is stored under a name chosen by the client, is executed or served as something it is not, or reaches a folder outside the storage;
- a cross-site scripting hole, an open redirect, or a way to run a spreadsheet formula from an exported cell.

## What is not in scope

- A console served over plain HTTP, on a public address with no VPN or allow-list, or with a weak password. The documentation says how to set it up safely ([Security](https://mdmusahub.github.io/JPA-SUPER-ADMIN/security/)), and login rate limiting is planned for v0.2.
- A problem that needs the administrator's own password, or the host's database or file system, to begin with.
- Reports from automated scanners with no demonstrated effect, missing headers that the console does not claim to set, and findings in a dependency that the project's build does not use.
