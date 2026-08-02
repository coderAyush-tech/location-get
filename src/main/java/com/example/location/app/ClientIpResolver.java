package com.example.location.app;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

@Component
public class ClientIpResolver {
    private final List<CidrBlock> trustedProxies;

    public ClientIpResolver(@Value("${app.proxy.trusted-cidrs}") String[] trustedProxyCidrs) {
        this.trustedProxies = Arrays.stream(trustedProxyCidrs)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(CidrBlock::parse)
                .toList();
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = normalizeLiteral(request.getRemoteAddr());
        if (isTrustedProxy(remoteAddress)) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                String[] chain = forwardedFor.split(",");
                // Walk from the proxy nearest to this app towards the client. This prevents
                // a caller from winning by prepending a forged value to X-Forwarded-For.
                for (int index = chain.length - 1; index >= 0; index--) {
                    String address = normalizeLiteral(chain[index]);
                    if (address != null && !isTrustedProxy(address)) {
                        return address;
                    }
                }
            }

            String realIp = normalizeLiteral(request.getHeader("X-Real-IP"));
            if (realIp != null) {
                return realIp;
            }
        }

        return remoteAddress == null ? request.getRemoteAddr() : remoteAddress;
    }

    private boolean isTrustedProxy(String remoteAddress) {
        if (remoteAddress == null) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(remoteAddress);
            return trustedProxies.stream().anyMatch(block -> block.contains(address));
        } catch (UnknownHostException exception) {
            return false;
        }
    }

    private static String normalizeLiteral(String value) {
        if (value == null || value.isBlank() || "unknown".equalsIgnoreCase(value.trim())) {
            return null;
        }
        String candidate = value.trim();
        if (candidate.startsWith("[") && candidate.endsWith("]")) {
            candidate = candidate.substring(1, candidate.length() - 1);
        }
        int zone = candidate.indexOf('%');
        if (zone >= 0) {
            candidate = candidate.substring(0, zone);
        }
        if (!candidate.matches("[0-9a-fA-F:.]+")) {
            return null;
        }
        try {
            return InetAddress.getByName(candidate).getHostAddress();
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    private record CidrBlock(byte[] network, int prefixLength) {
        static CidrBlock parse(String value) {
            String[] parts = value.split("/", 2);
            try {
                byte[] address = InetAddress.getByName(parts[0]).getAddress();
                int prefix = parts.length == 2 ? Integer.parseInt(parts[1]) : address.length * 8;
                if (prefix < 0 || prefix > address.length * 8) {
                    throw new IllegalArgumentException("Invalid trusted proxy CIDR: " + value);
                }
                return new CidrBlock(address, prefix);
            } catch (UnknownHostException | NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid trusted proxy CIDR: " + value, exception);
            }
        }

        boolean contains(InetAddress candidate) {
            byte[] address = candidate.getAddress();
            if (address.length != network.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            for (int index = 0; index < fullBytes; index++) {
                if (address[index] != network[index]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xff << (8 - remainingBits);
            return (address[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}
