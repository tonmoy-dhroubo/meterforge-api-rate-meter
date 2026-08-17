package io.meterforge.controlplane.identity.infrastructure;

import java.security.Principal;
import java.util.UUID;

public record StaffPrincipal(
        UUID userId,
        String email
) implements Principal {
    @Override
    public String getName() {
        return email;
    }
}
