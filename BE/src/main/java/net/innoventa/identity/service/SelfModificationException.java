package net.innoventa.identity.service;

/**
 * Thrown when an admin's action targets their own {@code identity_users} row — disabling or
 * deleting yourself from the admin panel would either lock you out or, for the last remaining
 * admin, strand the whole panel with nobody able to manage users at all.
 */
public class SelfModificationException extends RuntimeException {
}
