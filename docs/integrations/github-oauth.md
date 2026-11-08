# Sign in with GitHub

People can sign in to the OpenFactstore web UI with their GitHub account. This page sets that up from start to finish. To sign in through your organisation's identity provider (Entra ID, Okta) instead, see [oidc-sso.md](oidc-sso.md). Roles and sessions are covered in [../authentication.md](../authentication.md).

## 1. Register an OAuth app on GitHub

In GitHub, open **Settings → Developer settings → OAuth Apps → New OAuth App**. Use your organisation's settings page instead to own the app at organisation level. Fill in:

| Field | Value |
|---|---|
| Application name | `OpenFactstore` (anything recognisable) |
| Homepage URL | `https://factstore.example.com` (where your users open the UI) |
| **Authorization callback URL** | `https://factstore.example.com/login/oauth2/code/github` |

The callback URL must match exactly, including the scheme. Spring Security handles that path; you don't build it. For a local run use `http://localhost:8080/login/oauth2/code/github`.

Create the app, then **Generate a new client secret**. Copy the client ID and the secret.

## 2. Configure OpenFactstore

Set the registration on the backend (command service). The variable names are Spring Boot's relaxed binding of `spring.security.oauth2.client.registration.github.*`:

```bash
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GITHUB_CLIENT_ID=<client id>
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GITHUB_CLIENT_SECRET=<client secret>
GITHUB_DEFAULT_ORG=<organisation slug>   # optional, see below
SSO_JWT_SECRET=<at least 32 random bytes>
```

Generate the session secret with `openssl rand -base64 48`. The application refuses to start with sign-in enabled and no strong secret.

Keep the client secret in your secret store (Vault, a Kubernetes Secret), not in a committed file.

## 3. What happens at sign-in

1. **Continue with GitHub** on `/login` goes to `/oauth2/authorization/github`, which redirects to GitHub.
2. GitHub returns to the callback URL. Spring Security exchanges the code and reads the user's profile.
3. OpenFactstore matches the GitHub account to a user:
   - by **GitHub id** if the account has signed in before;
   - otherwise by **email**, linking the GitHub account to that existing user;
   - otherwise it creates a new user.

   An account that keeps its email private is keyed by GitHub's no-reply address, `<id>+<login>@users.noreply.github.com`. If the email already belongs to a user linked to a *different* GitHub account, sign-in is refused.
4. A **first-time** user joins `GITHUB_DEFAULT_ORG` as a `VIEWER`. Signing in never grants write access by itself; an admin raises the role under the organisation's members. An existing member keeps the role an admin gave them. With no default organisation set, the user signs in but belongs to no organisation until an admin adds them.
5. A session is issued as an `HttpOnly`, `SameSite=Strict` cookie, the same as for organisation SSO, and the browser lands on the dashboard.

A cancelled or failed sign-in returns to `/login?reason=failed`. GitHub's own error text never appears in the address bar. Every sign-in is an audit event (`USER_SIGNED_IN`, provider `GITHUB`).

## Troubleshooting

| Symptom | Cause |
|---|---|
| GitHub says *"The redirect_uri is not associated with this application"* | The callback URL in the GitHub app does not match the one OpenFactstore sends. Check the scheme, host and port, and `server.forward-headers-strategy` if TLS ends at a proxy. |
| Back on `/login` with "Sign-in did not complete" | The email is linked to another GitHub account, or the user cancelled. The backend log has the reason. |
| Signed in, but every page is empty | The user has no organisation. Set `GITHUB_DEFAULT_ORG` or add them as a member. |
