import type { Parser } from "@lezer/common"
import { highlightTree } from "@lezer/highlight"
import { StyleModule } from "style-mod"
import { IDENTITY_HIGHLIGHT_STYLE } from "./highlightStyle"
import { jmpSyntaxLanguage } from "./jmpSyntax"

/**
 * Highlighting for a block of code that is being *read* rather than edited — Identity has no editor, and
 * this is the whole of its colouring machinery.
 *
 * <p>It renders through the {@link IDENTITY_HIGHLIGHT_STYLE} `HighlightStyle` rather than the built-in
 * `classHighlighter`, whose tag→class coverage differs: the style is where every `--syntax-*` decision
 * lives, so resolving it is what makes a `deny` line loud. There is no CodeMirror view to mount the
 * stylesheet it generates, so we mount it ourselves, once.
 *
 * <h2>⚠️ One language, and no catalogue behind it</h2>
 *
 * <p>WiQ's copy of this file resolves a fence name against `@codemirror/language-data`, lazily loading
 * SQL or JSON for a code block inside a wiki page. Identity has no fences and one screen that shows one
 * document, so the catalogue is deliberately absent — it would be a dependency carried, and a
 * lazy-loading path exercised, for a language nothing here can produce. Every other name resolves to no
 * parser and renders as plain text, which is the right outcome and the reason nothing here throws.
 *
 * <p>⚠️ If a second language ever turns up on a second screen, add `@codemirror/language-data` and the
 * `LanguageDescription.matchLanguageName` branch back — WiQ's file is the reference. Do not hand-write a
 * second grammar.
 */

const HTML_ESCAPES: Record<string, string> = {
  "&": "&amp;",
  "<": "&lt;",
  ">": "&gt;",
  '"': "&quot;",
  "'": "&#39;",
}

let highlightStylesMounted = false

/** Mount the shared highlight stylesheet once, so its generated token classes have colours here too. */
function ensureHighlightStyles(): void {
  if (highlightStylesMounted || typeof document === "undefined") {
    return
  }

  if (IDENTITY_HIGHLIGHT_STYLE.module) {
    StyleModule.mount(document, IDENTITY_HIGHLIGHT_STYLE.module)
  }

  highlightStylesMounted = true
}

function escapeHtml(text: string): string {
  return text.replace(/[&<>"']/g, (character) => HTML_ESCAPES[character])
}

/** The three names a policy turns up under — Innoventa's manual uses all of them. */
function isPolicyLanguage(name: string): boolean {
  return name === "jmp" || name === "jmouse-policy" || name === "policy"
}

/** Render `code` to an HTML string of highlighted `<span>` runs using the given Lezer parser. */
export function highlightToHtml(parser: Parser, code: string): string {
  ensureHighlightStyles()

  const tree = parser.parse(code)
  let html = ""
  let position = 0

  highlightTree(tree, IDENTITY_HIGHLIGHT_STYLE, (from, to, classes) => {
    if (from > position) {
      html += escapeHtml(code.slice(position, from))
    }

    html += `<span class="${classes}">${escapeHtml(code.slice(from, to))}</span>`
    position = to
  })

  if (position < code.length) {
    html += escapeHtml(code.slice(position))
  }

  return html
}

/**
 * Resolve a language name to a Lezer parser.
 *
 * <p>Returns `null` for anything but a policy, so the caller falls back to plain, unhighlighted code —
 * which is the right outcome. A missing grammar is not an error worth telling a reader about.
 *
 * <p>⚠️ **Asynchronous with nothing to await, on purpose.** WiQ's identical signature loads a grammar
 * over the network; keeping the shape means the two files stay one edit apart, and means the day a
 * catalogue is added back no caller changes.
 */
export async function resolveParser(languageName: string): Promise<Parser | null> {
  const normalized = languageName.trim().toLowerCase()

  if (!isPolicyLanguage(normalized)) {
    return null
  }

  return jmpSyntaxLanguage.parser
}
