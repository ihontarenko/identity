/**
 * The permission names the backend answers with, so no screen spells one itself.
 *
 * ⚠️ These are the mirror of `security/access/Permissions.java`, and nothing checks that the two
 * agree — the backend compares its constants against `policy/identity.jmp` in both directions at
 * boot, but that guard cannot see this file. A name misspelt here does not break anything loudly: it
 * simply never matches, so the control it gates stays hidden and the screen looks merely empty.
 *
 * Which is why a control is rendered from one of these constants and never from a literal.
 */
export const PERMISSIONS = {
  /** See the register of accounts. */
  READ_USER: "user:read",
  /** Raise a new account. */
  CREATE_USER: "user:create",
  /** Edit an account's profile. */
  EDIT_USER: "user:edit",
  /** Block and unblock an account — the reversible half. */
  DISABLE_USER: "user:disable",
  /** Delete an account permanently. ⚠️ Carried by no role; held personally or not at all. */
  DELETE_USER: "user:delete",
  /** Set another person's password. */
  SET_PASSWORD: "user:password",
  /** Edit the roles this installation shares, and see who holds what. */
  ADMINISTER_ACCESS: "access:administer",
} as const

export type Permission = (typeof PERMISSIONS)[keyof typeof PERMISSIONS]
