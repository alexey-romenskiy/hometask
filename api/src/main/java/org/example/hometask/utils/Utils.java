package org.example.hometask.utils;

import org.example.hometask.api.DecimalDecoder;
import org.example.hometask.api.DecimalEncoder;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

public final class Utils {

    @NotNull
    public static BigDecimal getBigDecimal(@NotNull DecimalDecoder decoder) {
        return BigDecimal.valueOf(decoder.mantissa(), decoder.exponent());
    }

    public static void setBigDecimal(@NotNull DecimalEncoder encoder, @NotNull BigDecimal value) {
        encoder.mantissa(value.unscaledValue().longValueExact());
        encoder.exponent((byte) value.scale());
    }

    private Utils() {
        // empty
    }
}
