package org.example.ai.guest;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Only explicitly trusted proxy hops may supply a forwarded client address. No DNS lookups. */
@Component
public class GuestClientAddress {
    private final Set<String> trusted;
    public GuestClientAddress(@Value("${ai.guest.trusted-proxies:}") String proxies) {
        trusted = Arrays.stream(proxies.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(GuestClientAddress::normalize).collect(Collectors.toUnmodifiableSet());
    }
    public String resolve(HttpServletRequest request) {
        String address = normalize(request.getRemoteAddr());
        if (!trusted.contains(address)) return address;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.length() > 2048) return address;
        String[] hops = forwarded.split(",");
        for (int i = hops.length - 1; i >= 0 && trusted.contains(address); i--) {
            try { address = normalize(hops[i].trim()); }
            catch (IllegalArgumentException invalid) { return normalize(request.getRemoteAddr()); }
        }
        return address;
    }
    private static String normalize(String value) {
        if (value == null || !value.matches("[0-9a-fA-F:.]{2,45}")) throw new IllegalArgumentException("Expected a literal proxy IP address");
        try { return InetAddress.getByName(value).getHostAddress(); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid proxy IP address"); }
    }
}
