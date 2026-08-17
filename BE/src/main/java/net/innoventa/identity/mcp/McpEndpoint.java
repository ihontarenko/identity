package net.innoventa.identity.mcp;

/**
 * The one path the Model Context Protocol is served on.
 *
 * <p>Two unrelated things have to agree on it: the transport that publishes the protocol there, and the
 * security rule that decides what a credential reaching it may do. A constant is what keeps them from
 * drifting — a servlet mapped somewhere the filter chain does not cover is the protocol served outside
 * its own security boundary, and wrong from neither side alone.
 *
 * <p>It sits under {@code /api} so any reverse proxy already forwards it, and deliberately does not say
 * "ai": this is a tool protocol, not an AI feature. Innoventa and Tessera chose the same path for the
 * same two reasons.
 */
public final class McpEndpoint {

    /** JSON-RPC on POST, an event stream on GET, and a session teardown on DELETE. */
    public static final String PATH = "/api/mcp";

    /** The servlet mapping, and the pattern the security rule uses. */
    public static final String ALL_PATTERN = PATH + "/*";

    private McpEndpoint() {
    }
}
