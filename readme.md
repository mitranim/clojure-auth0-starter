## Overview

Quickstart/template for a Clojure/Ring webserver with Auth0.

Auth0 is a cloud authentication service: https://auth0.com. It has a free plan
suitable for small apps. This template uses the Auth0 HTTPS API without any SDK.


## Usage

  * clone and `cd` to the repo
  * [setup Auth0](#auth0-setup)
  * setup [env secrets](#env-secrets)
  * [run](#run)


### Auth0 Setup

1. Register with Auth0 at https://auth0.com.

2. Create a tenant. In Auth0 terms: "tenant" = "account" ≈ app ≈ brand. You need
   one "tenant" per application. The "create tenant" button is in the dropdown
   under your profile. Make sure to pick the region closest to your users.

3. Create a "client" for that tenant. It represents your app. When prompted for
   app type and technology, ignore it, scroll up, and click "Settings".

4. You should be seeing things like "Domain" and "Client ID". Copy these
   into the [env secrets](#env-secrets):

```properties
AUTH0_DOMAIN=<domain>
AUTH0_CLIENT_ID=<client id>
AUTH0_CLIENT_SECRET=<client secret>
```

5. Allow application URLs.

In "Allowed Callback URLs", add something like:

```
http://<host:port>/auth/callback,
https://<host-prod>/auth/callback`
```

Where:
  * `<host:port>` is something like `localhost:NNNN`; get the port from
    `.env.properties.example` → `LOCAL_PORT`
  * `<host-prod>` is your official domain.

In "Allowed Logout URLs", add something like:

```
http://<host:port>/auth/logout,
https://<host-prod>/auth/logout
```

Replacing the hosts as before.

6. Get a server-to-server authentication key.

Auth0 has two API tiers: untrusted (called "authentication API") and trusted
(called "management API"). Most tutorials focus on the untrusted API, which
forces you to jump through extra hoops and frankly doesn't make sense on a
trusted server.

Go to the Auth0 dashboard → API → should see Auth0 Management API → API Explorer.

Set "Token Expiration" to a duration that makes sense, e.g. `31536000` → Update
& Regenerate → copy. Add it to the [env secrets](#env-secrets) under
`AUTH0_API_KEY`.

7. Get the signing certificate

We'll need Auth0's certificate for verifying (unsigning) JWT tokens. Go back to
"Clients" → pick your app → "Settings" → scroll down → "Show Advanced Settings"
→ "Certificates" → download as PEM. It's a plain text file. Add the certificate
it contains to the [env secrets](#env-secrets) under `AUTH0_PEM_CERTIFICATE`,
joining the lines or adding a backslash after each:

```properties
AUTH0_PEM_CERTIFICATE=\
-----BEGIN CERTIFICATE-----\n\
xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\
..............................................\
xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\
\n-----END CERTIFICATE-----
```


### Env Secrets

Copy or rename `.env.properties.example` → `.env.properties`:

```sh
cp .env.properties.example .env.properties
```

Fill out the missing keys with the secrets from [Auth0 Setup](#auth0-setup).


### Run

Now you can run the app:

```sh
lein repl
```

Or:

```sh
lein repl :headless
# another tab
lein repl :connect
```

If you have completed all previous steps, this should launch the app and report
a localhost URL to open. It should display a webpage with the authentication
status and a login link.


## Misc

If you have question or suggestions, open an issue, reach me on
skype:mitranim.web, or email to me@mitranim.com.
