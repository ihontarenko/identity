interface DisplayableAccount {
  displayName?: string | null
  email?: string | null
}

/** The best human-facing label an account offers: display name, else email, else a fallback. */
export function accountName(
  account?: DisplayableAccount | null,
  fallback = "an account that no longer exists",
): string {
  if (!account) {
    return fallback
  }

  return account.displayName || account.email || fallback
}

/** Up to two initials for the face, from the display name (or the email's local part). */
export function accountInitials(account?: DisplayableAccount | null): string {
  const source = account?.displayName || account?.email || "?"
  const parts = source.trim().split(/\s+/).filter(Boolean)

  if (parts.length >= 2) {
    return (parts[0][0] + parts[1][0]).toUpperCase()
  }

  return source.slice(0, 2).toUpperCase()
}
