# Additive

An adaptive addition and subtraction game with a read-only [Parent view](http://localhost:8080/progress.html). Built with Kotlin, Spring Boot, and Postgres as a backend interview project.

## Run the demo

With Docker and Docker Compose running, from the project directory:

```sh
docker compose up --build -d --wait
```

Open [localhost:8080](http://localhost:8080/) and start a game. Compose waits for Postgres, and Spring Boot runs the Flyway migrations on startup. The database volume survives `docker compose down`; the browser remembers the game token in the same browser profile. The default ports and database credentials are for local development. If port 8080 is busy, run the command with `ADDITIVE_PORT=8081` and open port 8081 instead.

If your network requires a custom certificate authority for dependency downloads, pass its trusted root certificate as a PEM file. It is used during the build and is not included in the runtime image:

```sh
docker build --secret id=build_ca,src=/path/to/root-ca.pem -t additive:local .
docker compose up --no-build -d --wait
```

## Develop and test

With JDK 21, Node.js 24, and Docker running:

```sh
docker compose up -d --wait db
./gradlew bootRun
```

Run `./gradlew build` to compile, build the Tailwind CSS, and run the tests. The integration tests start their own Postgres instances using Testcontainers, so they do not need the Compose database.

## How it works

```text
Game / Parent view → Spring Boot API → Exposed → Postgres
                           │                     ↑
                           └─ Kotlin policy      Flyway migrations
```

- **Retries:** An attempt UUID is the primary key. Retrying the same submission returns its stored result.
- **Rewards:** A composite primary key gives each child one ice cream per operation and ordered operands. A fresh presentation of a repeat can still advance the streak.
- **Concurrent updates:** A transaction locks the child row before recording an attempt, updating progress, and awarding any ice cream. One Postgres test checks that 20 concurrent progress updates preserve the entire streak and cause exactly one promotion.

For a short demo, solve a problem, make a wrong first guess on another, and open Parent view to show how first-try accuracy differs from the current streak. Then show the two concurrency tests: double submissions at the API and distinct updates to the same child row.
