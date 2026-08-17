# ADR-0001 · Identity issues its own protocol credential rather than gaining dynamic client registration

**Status:** accepted · 2026-08-17
**Context:** ID-9. The first ADR this service has; the directory starts here.

## The question this had to answer

Identity now serves the Model Context Protocol at `/api/mcp`. A protocol client — Claude Code — has
**nowhere to be told a client id**: it reads the authorization server's metadata, registers itself
(RFC 7591), and refuses to continue when there is no `registration_endpoint`.

Identity has none. That is written up at length in `CLAUDE.md`, along with the dead `tessera-mcp`
entry under `identity.clients` that was written for exactly that caller and could never be reached by
it. The same file then predicts this decision:

> If a second product ever serves the protocol, that is the point to reconsider: a registration
> endpoint here, with the audience resolved from the `resource` parameter (RFC 8707), would serve all
> of them — at the cost of an installation-wide authorization server that accepts anonymous client
> registrations.

**Identity is that second product**, and it is also the authorization server itself. So the question
could no longer be deferred.

## The decision

**Identity issues its own credential for its own protocol endpoint, exactly as Tessera does**
(`Tessera/docs/adr/0020`). It does **not** gain dynamic client registration.

The protocol's authorization walk — registration, the S256 challenge, the loopback address, the
one-shot code, the consent screen — is `jmouse-ai-mcp-authorization`'s and is shared with Tessera.
What Identity adds is minting: an HS256 token over a secret only this service holds, carrying a `cid`
claim that names a row in `mcp_credentials`.

## Why not add registration to the real authorization server

Because of what it would mean, not because of what it would cost to build.

⚠️ **A `registration_endpoint` on Identity is an installation-wide authorization server that accepts
anonymous client registrations.** Every product here trusts Identity's RSA key through JWKS; a client
that could register itself could ask for an authorization code and, with a person's approval, hold an
ordinary access token for *any* audience the flow would grant. The blast radius of getting that wrong
is every product at once, and the thing being enabled is one endpoint on one service.

The confined credential has the opposite shape: it is useless everywhere except where it was issued
for, and that is a property of the signature rather than a check somebody remembered to write.

## What "confined" means here, precisely

- **A different signature.** Identity's ordinary tokens are RS256 and verifiable by everyone through
  JWKS. A protocol credential is HS256 over `identity.mcp.signing-secret`. Presented to Innoventa,
  Moneta, Tessera or WiQ it is not a token that fails a check — it is a signature that does not verify.
- **A different audience.** `identity-mcp`, deliberately not one of the audiences under
  `identity.clients`. Two independent reasons to be refused elsewhere is one more than strictly needed;
  the cheap one is the one that survives a refactor.
- **A different filter chain.** `mcpSecurityFilterChain` accepts only the HS256 decoder, and it is
  stateless — a program presenting a bearer token must not walk away with a session cookie, which
  would be the confinement undone in one hop.
- ⚠️ **Verified in both directions rather than assumed.** The protocol credential answers 302 (i.e.
  unauthenticated) on `/api/me` and `/api/admin/users`; a browser session answers 401 on `/api/mcp`.

## ⚠️ The wrinkle that is Identity's alone: two authorization servers on one host

Tessera publishes its protocol metadata at `/.well-known/oauth-authorization-server`, because nothing
else on that host is an authorization server. **Identity is one**, and Spring Authorization Server
already answers that exact path with this service's real metadata — the document that describes
`/oauth2/authorize`, the JWKS, and famously no `registration_endpoint`.

The two documents cannot share an address, and the standard already says what to do: RFC 8414 §3.1
puts the metadata for an issuer with a path component at
`/.well-known/oauth-authorization-server/{path}`. So:

| Document | Address | Describes |
|---|---|---|
| Identity's own | `/.well-known/oauth-authorization-server` | the real authorization server, untouched |
| the protocol's | `/.well-known/oauth-authorization-server/api/agents/authorization` | the confined flow, with a `registration_endpoint` |
| the resource | `/.well-known/oauth-protected-resource` | names the protocol issuer explicitly |

Two authorization servers on one host is the case the suffix was invented for, so this is the standard
working rather than a trick — but it is the part most likely to surprise a client, which is why the
protected-resource document names the issuer rather than letting anything infer it.

## What this cost outside Identity

One change to `jmouse-ai-mcp-authorization`'s shared consent screen. It read a bearer token from web
storage and sent `Authorization: Bearer`; **Identity keeps no token at all** — it is signed in by a
session cookie, which is what an authorization server's own interface looks like. Configuring no
storage key now means "send the origin's cookies instead", and the signed-out check moves to the
server's 401 rather than a token the page cannot see.

That was a third shape the library had not met, and Identity is the case that proves the abstraction
was drawn in the right place: two products' sign-in mechanisms having nothing in common was already
the stated reason the screen is shared.

## Consequences

- The `tessera-mcp` entry under `identity.clients` remains unreachable and remains documented as such.
  Nothing in this decision revives it.
- ⚠️ **`identity.mcp.signing-secret` has no default and the boot fails without one.** Tessera generates
  one per run when unset, which is right on a laptop and wrong here: a restart that silently
  invalidates every protocol connection on the service every other product's sign-in depends on is a
  failure nobody would attribute to a missing property.
- A protocol client acts as the person who approved it and holds exactly what they hold. Identity has
  no agent accounts — the consent screen offers one choice, themselves — so the epic's invariant holds
  by construction rather than by a check.
- If a **third** product serves the protocol, nothing here needs revisiting. The reconsideration this
  ADR answers was specifically about Identity, and the answer is that being the authorization server is
  a reason for confinement rather than against it.
