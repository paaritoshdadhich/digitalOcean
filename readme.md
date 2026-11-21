# digitalOcean

Coding workspace for the Feature Flag Management REST API assignment.

## Project

See [`workspaces/flag-management-system`](workspaces/flag-management-system) for the service, full README, and local run instructions.

```bash
cd workspaces/flag-management-system
docker compose up -d
mvn spring-boot:run
```

CI: [`.github/workflows/ci.yml`](.github/workflows/ci.yml) builds and tests on push and pull request.
