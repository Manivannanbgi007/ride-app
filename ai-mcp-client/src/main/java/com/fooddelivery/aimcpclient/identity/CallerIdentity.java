package com.fooddelivery.aimcpclient.identity;

/**
 * A VERIFIED caller: taken from an access token whose signature, expiry, issuer and audience Spring Security checked.
 * Services and clients only ever see this record, never the token's claims.
 *
 * @param userId      id of the calling user (the token's subject)
 * @param role        the role the token was issued for
 * @param accessToken the token itself, forwarded to ai-mcp-server, which calls the platform with it. Only the
 *                    client package may read it (ArchitectureTest), and it never appears in a log line.
 */
public record CallerIdentity(long userId, Role role, String accessToken) {

    // Records print every field: the token must not reach a log line by accident
    @Override
    public String toString() {
        return "CallerIdentity[userId=" + userId + ", role=" + role + ", accessToken=****]";
    }
}
