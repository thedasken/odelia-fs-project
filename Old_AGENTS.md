# Odelia Full-Stack Project

This is a training project. Prefer simple, minimal and educational changes.

## Project structure

* `frontend/` — Angular 20, standalone components, signals
* `backend/` — Jakarta EE 11, MicroProfile, OpenLiberty
* `keycloak/` — Keycloak authentication configuration
* `exercises/` — course exercises
* `.devcontainer/` — development environment
* `docker-compose.yml` — PostgreSQL, Keycloak and application services

The project is intended to run inside the Dev Container.

## General rules

* Do only what the user explicitly asks.
* Make the smallest change necessary.
* Do not scan the entire repository unless required.
* Read only the files relevant to the current task.
* Never inspect `.git/`.
* Do not refactor unrelated code.
* Do not modify unrelated files.
* Do not add dependencies unless absolutely necessary.
* Preserve the existing project architecture and coding style.
* Prefer existing components, services, resources and patterns.
* Keep explanations concise.

## Exercises

When the user refers to an exercise number:

1. Read only the corresponding file in `exercises/`.
2. Identify the files required to complete that exercise.
3. Inspect only those files.
4. Implement only what the exercise requests.
5. Do not read `exercises/solutions/` unless the user explicitly asks for it.

Do not implement future exercises.

## Frontend

For frontend tasks, work primarily in `frontend/`.

The frontend uses:

* Angular 20
* standalone components
* signals
* Angular CDK
* `keycloak-js`

Preserve the existing Angular architecture.

Do not modify authentication code unless required by the task.

## Backend

For backend tasks, work primarily in `backend/`.

The backend uses:

* Java 21
* Jakarta EE 11
* MicroProfile 7.1
* OpenLiberty
* Jakarta Data
* PostgreSQL

Preserve the existing resource, repository and security structure.

Do not replace Jakarta Data with custom persistence code unless explicitly requested.

## Security

Authentication is handled by Keycloak and MicroProfile JWT.

Do not weaken or bypass:

* JWT authentication
* role checks
* board ownership checks
* `CurrentUser`
* `BoardAccess`

A user must not gain access to another user's board through a frontend or backend change.

## Development environment

Assume commands are executed inside the Dev Container unless the user says otherwise.

Backend development command:

`cd backend && mvn liberty:dev`

Frontend development command:

`cd frontend && npm start`

Avoid rebuilding or restarting the whole environment unless necessary.

## Testing

Run only checks relevant to the files changed.

Do not run the entire test suite for a trivial change unless necessary.

If a test fails because of an unrelated existing problem, report it instead of fixing unrelated code.

## Response style

After completing a task, report only:

* what was changed;
* which files were modified;
* whether relevant checks passed;
* any important issue the user needs to know.

Keep the response short.

Stop once the requested task is complete.
