# Code quality (Phase 15a)

Every project (the 11 services and `event-contracts`) runs the same checks. They run as part of the normal build
and fail it, so a problem shows up on the developer's machine, not later:

```text
mvnw verify
  test      unit, slice and Testcontainers integration tests, plus ArchitectureTest (ArchUnit)
  verify    JaCoCo     coverage report (target/site/jacoco/index.html); fails below the project's minimum
            Checkstyle style and coding rules (quality/checkstyle.xml)
            SpotBugs   bug patterns in the compiled code (quality/spotbugs-exclude.xml lists the exceptions)

quality/sonar-scan.ps1   SonarQube: everything above in one place, history, and a quality gate per project
```

The Docker images are built with `mvn package -DskipTests`, so none of this slows down `docker compose ... --build`.
The end-to-end tests (`e2e-tests`, test code only) follow the same Checkstyle rules.

## What is checked

| Tool | Checks | Where the rules are |
|---|---|---|
| ArchUnit (`ArchitectureTest` in each service) | The requirements' production coding rules and dependency direction: controllers use services, never repositories (rules 1, 2); no JPA entity in the REST API, in or out (rules 3, 14); `@Transactional` not on controllers (17); no EAGER relationships (11); no `System.out` (7); constructor injection only; repositories, entities and DTOs do not reach up to services or controllers; `@RestController`, `@Entity`, repositories and Kafka listeners live in their packages | `src/test/java/.../ArchitectureTest.java` |
| Checkstyle | The code's style (4-space indent, at most 130 columns, import order: static, `java`, then the rest), naming, braces, `equals`/`hashCode`, no `catch (Exception)` unless justified (rule 12), no `process()`/`handle()`/`doSomething()`/`execute()` method names ("Recommended naming"), no `java.util.Date`, no Lombok `@Data` | `checkstyle.xml`, `checkstyle-suppressions.xml` |
| SpotBugs | Null dereferences, unsafe synchronization, resource leaks, wrong `equals`, and the other bug patterns at "medium" confidence and above | `spotbugs-exclude.xml` |
| JaCoCo | Line coverage of the main code (Lombok-generated code is left out, see `lombok.config`) | `jacoco.minimum.line.coverage` in each `pom.xml` |
| SonarQube | Bugs, vulnerabilities, security hotspots, code smells, duplication and coverage, with history | Quality gate "Food Delivery" (`sonar-scan.ps1`) |

### When a rule does not fit

Fix the code if you can. If the rule is wrong for that one place, say so where it happens:

```java
@SuppressWarnings("checkstyle:IllegalCatch") // any failure marks the event for a retry: nothing may stop the relay
private boolean publish(OutboxEvent event) {
```

A rule that does not fit a whole group of files goes in `checkstyle-suppressions.xml` or `spotbugs-exclude.xml`,
with the reason next to it.

## SonarQube

```powershell
docker compose --profile quality up -d     # SonarQube + its PostgreSQL; http://localhost:9000
.\quality\sonar-scan.ps1                   # build, test and analyse every project (about 15 minutes)
.\quality\sonar-scan.ps1 -Projects order-service -SkipBuild   # analyse one project's last build only
```

- SonarQube needs about 3 GB of memory. If the machine is short of it, stop the services first
  (`.\start-local.ps1 -Stop`, or close their tabs).
- The first run sets the `admin` password to `SONARQUBE_ADMIN_PASSWORD` from `.env` and creates the
  **Food Delivery** quality gate. Each run creates an analysis token and revokes it at the end, so no token is
  stored anywhere.
- The script prints, per project, the quality gate result, bugs, vulnerabilities, security hotspots, code smells,
  coverage and duplication, and exits with 1 when a gate fails.

The **Food Delivery** quality gate:

| On | Condition |
|---|---|
| All code | Reliability A (no bugs), security A (no vulnerabilities), maintainability A, coverage at least 60 %, at most 5 % duplicated lines |
| New code | Coverage at least 80 %, at most 3 % duplicated lines, no new issues |

Security hotspots are not part of the gate: they need a person to review them in SonarQube (Security Hotspots tab).

The Java rules are the **Food Delivery way** quality profile: SonarQube's "Sonar way", inherited (so it follows
SonarQube's updates), with one change to agree with Checkstyle: at most 10 parameters per method (S107; Sonar way
says 7). The script keeps both the gate and the profile in line with what it declares at its top.

A Sonar rule that is wrong for one place is justified the same way as a Checkstyle one:
`@SuppressWarnings("java:S2925") // simulates a slow service on purpose`.

Without the script, any project can be analysed with Maven (a token from SonarQube: My Account > Security):

```powershell
$env:SONAR_TOKEN = "<token>"; .\mvnw.cmd verify sonar:sonar
```
