package pl.htgmc.htgeconomy.utils;

public final class MoneyUtil {
    private MoneyUtil() {}

    public static Long parseAmount(String s) {
        if (s == null) return null;
        s = s.trim().replace("_", "");
        if (s.isEmpty()) return null;

        // tylko całe liczby (MVP)
        try {
            long v = Long.parseLong(s);
            if (v < 0) return null;
            return v;
        } catch (Exception e) {
            return null;
        }
    }

    public static String fmt(long v, String symbol) {
        // 12_345 -> "12 345$"
        String raw = String.valueOf(Math.max(0L, v));
        StringBuilder out = new StringBuilder();
        int len = raw.length();
        for (int i = 0; i < len; i++) {
            out.append(raw.charAt(i));
            int left = len - i - 1;
            if (left > 0 && left % 3 == 0) out.append(' ');
        }
        return out + symbol;
    }
}