# digitalOcean

Coding workspace for the Feature Flag Management REST API assignment.

## Project

See [`workspaces/flag-management-system`](workspaces/flag-management-system) for the service.

| Doc | Purpose |
|-----|---------|
| [workspaces/flag-management-system/README.md](workspaces/flag-management-system/README.md) | Overview & quick start |
| [workspaces/flag-management-system/docs/ARCHITECTURE.md](workspaces/flag-management-system/docs/ARCHITECTURE.md) | Architecture, structure, services, tests |
| [workspaces/flag-management-system/docs/SETUP_AND_DEMO_GUIDE.md](workspaces/flag-management-system/docs/SETUP_AND_DEMO_GUIDE.md) | Fresh setup, endpoints, DB inspection |

```bash
cd workspaces/flag-management-system
docker compose up -d
mvn spring-boot:run
./scripts/demo-e2e.sh
```

CI: [`.github/workflows/ci.yml`](.github/workflows/ci.yml) builds and tests on push and pull request.
