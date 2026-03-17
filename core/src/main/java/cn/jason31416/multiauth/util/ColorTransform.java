package cn.jason31416.multiauth.util;

import lombok.SneakyThrows;

import java.util.ArrayList;
import java.util.List;

public class ColorTransform {

    public static String white(String colorCode, int size, int level) {
        return gradient(colorCode, "#ffffff", size).get(level);
    }

    public static List<String> gradient(String cA, String cB, int number) {
        int[] a = hex2RGB(cA);
        int[] b = hex2RGB(cB);
        List<String> colors = new ArrayList<>();

        for (int i = 0; i < number; i++) {
            int aR = a[0];
            int aG = a[1];
            int aB = a[2];
            int bR = b[0];
            int bG = b[1];
            int bB = b[2];

            colors.add(
                    rgb2Hex(
                            calculateColor(aR, bR, number - 1, i),
                            calculateColor(aG, bG, number - 1, i),
                            calculateColor(aB, bB, number - 1, i)
                    )
            );
        }

        return colors;
    }

    private static int calculateColor(int a, int b, int step, int number) {
        return a + (b - a) * number / step;
    }

    private static String rgb2Hex(int r, int g, int b) {
        return String.format("#%02X%02X%02X", r, g, b);
    }

    @SneakyThrows
    private static int[] hex2RGB(String hexStr) {
        if (hexStr.length() == 7) {
            int[] rgb = new int[3];
            rgb[0] = Integer.parseInt(hexStr.substring(1, 3), 16);
            rgb[1] = Integer.parseInt(hexStr.substring(3, 5), 16);
            rgb[2] = Integer.parseInt(hexStr.substring(5, 7), 16);
            return rgb;
        }
        throw new Exception();
    }
}

