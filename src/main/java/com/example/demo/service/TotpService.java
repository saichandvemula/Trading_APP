package com.example.demo.service;

import com.example.demo.exception.SmartApiException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class TotpService {

    private static final int TIME_STEP_SECONDS = 30;
    private static final int CODE_DIGITS = 6;

    public String generate(String base32Secret) {
        if (base32Secret == null || base32Secret.isBlank()) {
            throw new SmartApiException("TOTP is required. Provide request totp or ANGEL_ONE_TOTP_SECRET.");
        }

        try {
            byte[] key = decodeBase32(base32Secret);
            long counter = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;
            byte[] data = ByteBuffer.allocate(Long.BYTES).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % 1_000_000;
            return String.format("%0" + CODE_DIGITS + "d", otp);
        } catch (GeneralSecurityException ex) {
            throw new SmartApiException("Unable to generate TOTP", ex);
        }
    }

    private byte[] decodeBase32(String value) {
        String normalized = value.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        ByteBuffer buffer = ByteBuffer.allocate(normalized.length() * 5 / 8);
        int bits = 0;
        int bitBuffer = 0;
        for (char c : normalized.toCharArray()) {
            int index = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(c);
            if (index < 0) {
                throw new SmartApiException("Invalid TOTP secret format");
            }
            bitBuffer = (bitBuffer << 5) | index;
            bits += 5;
            if (bits >= 8) {
                buffer.put((byte) ((bitBuffer >> (bits - 8)) & 0xFF));
                bits -= 8;
            }
        }
        byte[] output = new byte[buffer.position()];
        buffer.flip();
        buffer.get(output);
        return output;
    }
}
