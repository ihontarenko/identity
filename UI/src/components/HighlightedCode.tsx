import { useEffect, useState } from "react"
import { cn } from "@/lib/helpers"
import { highlightToHtml, resolveParser } from "@/lib/codemirror"

/**
 * A read-only block of code, coloured by the `.jmp` grammar and Identity's syntax palette.
 *
 * <h2>⚠️ Code that is not highlighted is code nobody reads carefully</h2>
 *
 * <p>The one document this product renders is its own authorization, and the one line in it that must
 * never be skimmed past is a `deny`. A `<pre>` renders that line in exactly the colour of the twelve
 * `allow` lines above it.
 *
 * <p>⚠️ **Plain text is the fallback, not an error.** A language the grammar does not know resolves to no
 * parser and the block renders unhighlighted — which is the right outcome, and the reason nothing here
 * throws or warns.
 *
 * <p>⚠️ **The markup is built from token offsets, so this sets `innerHTML`.** Every character of `code`
 * goes through `escapeHtml` in {@link highlightToHtml} before it lands in that string; the spans are
 * generated from the parse tree, never from the content itself.
 */
export function HighlightedCode({
  code,
  language,
  className,
}: {
  code: string
  /** A language name — `jmp`, `jmouse-policy`, `policy`. Anything else renders as plain text. */
  language: string
  className?: string
}) {
  const [highlighted, setHighlighted] = useState<string | null>(null)

  useEffect(() => {
    // ⚠️ The parser resolves asynchronously and `code` can change under it, so a late answer for a
    // previous block must not paint over the current one.
    let currentRequest = true

    setHighlighted(null)

    void resolveParser(language).then((parser) => {
      if (!currentRequest || !parser) {
        return
      }

      setHighlighted(highlightToHtml(parser, code))
    })

    return () => {
      currentRequest = false
    }
  }, [code, language])

  const shared = cn(
    "overflow-x-auto rounded-lg border bg-muted/40 p-4 font-mono text-[12px] leading-relaxed",
    className,
  )

  if (highlighted === null) {
    return <pre className={shared}>{code}</pre>
  }

  return <pre className={shared} dangerouslySetInnerHTML={{ __html: highlighted }} />
}
