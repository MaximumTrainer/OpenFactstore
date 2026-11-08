# Organisation single sign-on (OIDC)

Each organisation in OpenFactstore can sign its people in through its own OpenID Connect identity provider. Microsoft Entra ID and Okta are supported. This page sets one up from start to finish. For GitHub sign-in, see [github-oauth.md](github-oauth.md). The checks applied to every ID token are listed in [../authentication.md](../authentication.md#the-oidc-flow).

## 1. Register OpenFactstore with the identity provider

Create a **web** application (confidential client, authorization code flow) and register this **redirect URI**:

```
https://factstore.example.com/api/v1/organisations/<org-slug>/sso/callback
```

Use one redirect URI per organisation slug that signs in through this provider. The web UI builds it from the address it is served on, so it must match what users type in their browser.

Request the `openid`, `email` and `profile` scopes. To map IdP groups to roles, also have the provider emit a groups claim:

- **Entra ID:** App registrations → your app → **Token configuration → Add groups claim** (security groups). The claim is `groups` and holds group object IDs.
- **Okta:** Applications → your app → **Sign On → OpenID Connect ID Token → Groups claim filter**, for example `groups` matching `factstore-.*`.

Note the **issuer URL**, **client ID** and **client secret**:

| Provider | Issuer URL |
|---|---|
| Entra ID | `https://login.microsoftonline.com/<tenant-id>/v2.0` |
| Okta | `https://<your-domain>.okta.com/oauth2/default` (or your custom authorization server) |

The issuer must use HTTPS. OpenFactstore reads `<issuer>/.well-known/openid-configuration` to find the endpoints and the JWKS.

## 2. Configure the organisation

An organisation admin (the `admin` scope) creates the SSO configuration:

```bash
curl -X POST https://factstore.example.com/api/v1/organisations/acme/sso \
  -H "Authorization: Bearer $ADMIN_KEY" -H "Content-Type: application/json" \
  -d '{
    "provider": "OKTA",
    "issuerUrl": "https://acme.okta.com/oauth2/default",
    "clientId": "<client id>",
    "clientSecret": "<client secret>",
    "attributeMappings": "{\"email\":\"email\",\"name\":\"name\",\"role\":\"groups\"}",
    "groupRoleMappings": "{\"factstore-admins\":\"ADMIN\",\"factstore-engineers\":\"MEMBER\"}"
  }'
```

Then check it: `POST /api/v1/organisations/acme/sso/test` fetches the discovery document and reports problems before anyone tries to sign in.

- `attributeMappings` names the claims holding email, display name and groups (`role`).
- `groupRoleMappings` maps a group to `ADMIN`, `MEMBER` or `VIEWER`. With several matches the highest role wins. A user in **no mapped group is a `VIEWER`**. Group changes take effect at the next sign-in.

The backend also needs a session signing secret, `SSO_JWT_SECRET` (at least 32 random bytes, for example from `openssl rand -base64 48`). It refuses to start without one.

## 3. Sign in

On `/login`, the user enters the organisation slug and chooses **Continue with SSO**. They are redirected to the identity provider and come back signed in, with a session cookie for that organisation. A failed sign-in returns to `/login` with a reason and no IdP error detail.

## Troubleshooting

| Symptom | Cause |
|---|---|
| "No single sign-on is configured for 'acme'" | No SSO configuration exists for that slug. |
| The IdP rejects the redirect URI | The registered URI and the one the browser sends differ (host, port, scheme, or slug). |
| Signed in as `VIEWER` unexpectedly | The groups claim is missing from the ID token, or no group name matches `groupRoleMappings`. Group IDs, not names, for Entra ID. |
| Login fails after an IdP key rotation | OpenFactstore refetches the JWKS on an unknown `kid`; if it persists, check outbound HTTPS from the backend to the issuer. |
