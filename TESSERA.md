# Tessera — Identity

> Project-specific notes for the `tessera` skill. The tracker is the record; this file is notes.
> Fix it in the same response as any call that contradicts it.
> Verified: 2026-08-20

## Project

- **Key** `ID` — the short key chosen on 2026-08-17. It is permanent, so issues read `ID-7` and never
  `IDNT-7`.
- **Name** `Identity` — this is the `scope` argument, verbatim.
- **Lead** `SU`
- **Board** every open issue (not sprint-scoped)
- **Sprints** not used — `planning: board`.

## Issue types

| Type | Used for |
|---|---|
| `Epic` | `ID-1` — the access system, user creation and MCP, parent of `ID-2`…`ID-10` |
| `Story` | most tickets |
| `UI changes` | `ID-16` — screen work with no behaviour behind it |
| `Bug` | `ID-17` — something that is wrong rather than missing |
| `Task`, `Sub-task` | legal, installation-wide, unused here so far |

⚠️ **`Papercut` and `Nit` do not exist as rows yet**, installation-wide. Until they are created by hand
on `/administration` → Issue types, §1's "work nobody asked to file" rule files under `Task` here.

## Statuses and the path through them

`To Do` → `WIP` → `In Review` → `Done`

- ⚠️ The in-flight status is **`WIP`**, not "In Progress". `In Progress` exists in the installation's
  catalog but is not on this path, and naming it is refused.
- Finishing is **two transitions**, not one. Read `canMoveTo` before each.

## Resolutions

`Done`, `Won't Do`, `Duplicate`, `Cannot Reproduce` — required when moving into a Done status, ignored
everywhere else.

## Mirror

`Identity/.tessera/` — one file per issue, named for the key.

⚠️ **Git: this is the case where the mirror is not free.** Identity is a **single repository whose root
is the product root** (`BE/` and `UI/` are just subdirectories), so a mirror written here lands straight
in `git status`. It is ignored by `/.tessera/` at the bottom of `Identity/.gitignore`. Never remove that
line, and never commit the mirror.

## Prose that belongs to this project

- `Identity/CLAUDE.md` — the authorization-server setup, registered clients, JWKS.

## Local notes

- Access here is **internal to Identity** and does not contradict "authorization is not centralized":
  Identity governs its own screens, it does not decide for other products.
- Identity supports no dynamic client registration, which is why an MCP client cannot be pointed at it
  and why each product mints its own endpoint-confined credential.
