import {
  createStaticHighlighter,
  POLICY_LANGUAGE,
  SYNTAX_HIGHLIGHT_STYLE,
} from "@jmouse/codemirror/highlight"

/**
 * Identity's whole colouring machinery: one policy grammar, the house palette, no editor.
 *
 * <h2>⚠️ One language, and no catalogue behind it</h2>
 *
 * <p>The other interfaces pass `languages` from `@codemirror/language-data`, so a ` ```sql ` fence
 * inside a page loads the SQL grammar. Identity has no fences and one screen that shows one document,
 * so the catalogue is deliberately absent — it would be a dependency carried, and a lazy-loading path
 * exercised, for a language nothing here can produce. Every other name resolves to no parser and
 * renders as plain text, which is the right outcome and the reason nothing here throws.
 *
 * <p>⚠️ **The document rendered here is Identity's own authorization**, projected back out of its own
 * tables — not, as in a tracker or a wiki, a fence somebody else wrote. A `deny` line that comes out the
 * same grey as everything around it is the one line in this product a reader must never skim past.
 *
 * <p>⚠️ **This colours; it does not decide.** Whether a policy is valid is answered by the real parser
 * on the backend and never here.
 */
export const { highlightToHtml, resolveParser } = createStaticHighlighter({
  highlightStyle: SYNTAX_HIGHLIGHT_STYLE,
  grammars: [POLICY_LANGUAGE],
})
