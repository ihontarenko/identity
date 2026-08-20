import { Tag, tags } from '@lezer/highlight';

/**
 * The tags jMouse's own languages emit, on top of the standard set.
 *
 * <h2>⚠️ Why Identity carries a language at all</h2>
 *
 * <p>Because it *has* a policy document, and the access screen shows one. Unlike Innoventa's manual or
 * Tessera's ticket bodies — which quote a ` ```jmp ` fence somebody else wrote — the document rendered
 * here is Identity's own authorization, projected back out of its own tables. A `deny` line that comes
 * out the same grey as everything around it is the one line in this product a reader must never skim
 * past.
 *
 * <p>⚠️ **This colours; it does not decide.** Whether a policy is valid is answered by the real parser
 * on the backend and never here — a TypeScript re-implementation of the grammar would agree for about
 * a month.
 *
 * <p>Ported verbatim from `WiQ/UI/src/lib/codemirror/tags.ts`, which took it from Innoventa's with the
 * jME half dropped: an expression language is only worth colouring where there is something to
 * evaluate, and there is nothing here.
 *
 * <h2>Why these exist at all</h2>
 *
 * <p>`.jmp` and jME used to borrow generic tags — a role name was `tags.className`, a language word
 * was `tags.keyword` — and both resolve to the same colour in any style that does not care about the
 * difference. So `grants` and `ORGANIZATION_ADMIN` came out identically, which is precisely the pair
 * a reader most needs told apart: one is the language, the other is the thing being granted.
 *
 * <p>A borrowed tag also cannot be recoloured without recolouring every other language. `tags.keyword`
 * belongs to Markdown fences, SQL, JSON and everything else the editor renders; making *policy*
 * keywords pink by editing it would repaint the whole product.
 *
 * <h2>⚠️ Every tag names a parent, and that is load-bearing</h2>
 *
 * <p>`Tag.define(parent)` means a style written for the parent still applies when nothing claims the
 * child. Identity's own style names every tag below explicitly, so the parents change nothing today —
 * they are what makes the file survive being handed to a style that has never heard of
 * {@link policyKeyword}: under one, a policy stays coloured in that style's own scheme rather than
 * collapsing to plain text. Which is also why this file is kept identical to the other products' copies
 * rather than trimmed to what this one screen needs.
 *
 * <p>⚠️ A parent must be a **plain** tag: `Tag.define` refuses a modified one, so
 * `tags.definition(tags.variableName)` and `tags.function(tags.variableName)` cannot be inherited
 * from however well they describe the child. Where the natural parent was a modified tag, the closest
 * plain relative is used instead — the choice only decides what a foreign theme falls back to, since
 * our own style names every tag here explicitly.
 */

// ── Policy (.jmp) ────────────────────────────────────────────────────────────

/** The language's own words — `policy`, `permissions`, `grants`, `when`, `include`. */
export const policyKeyword = Tag.define(tags.keyword);

/**
 * `declare` and `assign` — which *kind* of statement the block that follows is.
 *
 * <p>Its own tag because it is not a block, it *labels* one, and the difference is the whole reason
 * the words exist: `declare` opens something that says what exists at all, `assign` something that
 * says who has what. That is ADR-0018's line — the document owns structure, the row owns the case —
 * moved to the left margin so a reader sees it before reading anything.
 *
 * <p>⚠️ **Deliberately not a colour of its own.** A prefix painted a second colour reads as a second
 * kind of thing, and the point is the opposite: it qualifies the keyword beside it. So it takes the
 * keyword's hue and is set apart by weight and slant instead — visible when scanning, silent when
 * reading.
 */
export const policyStatementKind = Tag.define(tags.modifier);

/**
 * `deny`, and nothing else.
 *
 * <p>Its own tag because a denial wins over every allow, at every level, from every grant source. It
 * is the one word in a policy file that must never be skimmed past, so it is the one word no palette
 * is allowed to make quiet.
 */
export const policyDeny = Tag.define(tags.atom);

/** A role being declared or handed out — `role SPACE_ADMIN`, `grants ORGANIZATION_ADMIN`. */
export const policyRole = Tag.define(tags.className);

/** Who a block is about — the account named by `subject 'usr_7f21c'`. */
export const policySubject = Tag.define(tags.className);

/** A scope kind: `@GLOBAL`, `@ORGANIZATION`, `@SPACE`, `@SELF`. */
export const policyScope = Tag.define(tags.typeName);

/** Which one — the `'sp_kyiv'` half of `@SPACE:'sp_kyiv'`, or `*` for all of them. */
export const policyInstance = Tag.define(tags.string);

/** The namespace half of a permission — the `entry` of `entry:delete`. */
export const policyNamespace = Tag.define(tags.variableName);

/** The action half of a permission — the `delete` of `entry:delete`, or the `*` that means all. */
export const policyAction = Tag.define(tags.propertyName);

/**
 * What a capability statement is about — the `custody` of `gate custody`, the `storage-byte` of
 * `storage-byte 100GB per month`, the `workspace` of `allow workspace 25`.
 *
 * <p>Its own tag, sharing a colour with {@link policyNamespace} today, because it occupies the same
 * position a permission's namespace does: the *subject* of the line, with everything after it saying
 * how much and until when. Sharing the colour without sharing the tag is what lets a palette split
 * the two later without anybody having to touch the grammar.
 */
export const policyCapability = Tag.define(tags.propertyName);
