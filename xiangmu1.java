package shujvjiegou;
import java.math.BigInteger;

/**
 * 算法复杂度函数值对比表
 * 计算 n = 1, 2, 4, 8, 16, 32 时各函数的值
 */
public class xiangmu1 {

    public static void main(String[] args) {
        int[] nValues = {1, 2, 4, 8, 16, 32};
        String[] functions = {"1", "log n", "n", "n log n", "n²", "n³", "2ⁿ", "n!"};

        Object[][] table = new Object[functions.length][nValues.length];
        for (int j = 0; j < nValues.length; j++) {
            int n = nValues[j];
            table[0][j] = 1L;                                          // 1
            table[1][j] = log2(n);                                     // log n
            table[2][j] = (long) n;                                    // n
            table[3][j] = n * log2(n);                                 // n log n
            table[4][j] = (long) n * n;                                // n²
            table[5][j] = (long) n * n * n;                            // n³
            table[6][j] = pow2(n);                                     // 2ⁿ
            table[7][j] = factorial(n);                                // n!
        }

        // 表格
        printSeparator(98);
        System.out.println(center("算法复杂度函数值对比表  (log以2为底)", 98));
        printSeparator(98);

        // 表头
        System.out.printf("│  %-8s │", "函数");
        for (int n : nValues) {
            System.out.printf("  n=%-12d │", n);
        }
        System.out.println();
        printSeparator(98);
        for (int i = 0; i < functions.length; i++) {
            System.out.printf("│  %-8s │", functions[i]);
            for (int j = 0; j < nValues.length; j++) {
                System.out.printf("  %-12s │", formatValue(table[i][j]));
            }
            System.out.println();
            if (i < functions.length - 1) {
                printThinSeparator(98);
            }
        }

        printSeparator(98);
        System.out.println("  注：2ⁿ 和 n! 在 n≥16 时数值极大，以科学计数法或精确大整数显示。");
        System.out.println();
    }

    /**
     * 计算以2为底的对数
     */
    private static double log2(int n) {
        if (n <= 0) return Double.NEGATIVE_INFINITY;
        return Math.log(n) / Math.log(2);
    }

    /**
     * 计算 2^n，n≤32 时用 long 即可
     */
    private static Object pow2(int n) {
        if (n <= 62) {
            return 1L << n;  
        }
        // 超出long范围,使用BigInteger?
        BigInteger result = BigInteger.ONE.shiftLeft(n);
        return result;
    }

    /**
     * 计算 n!
     */
    private static Object factorial(int n) {
        if (n <= 20) {
            long result = 1;
            for (int i = 2; i <= n; i++) {
                result *= i;
            }
            return result;
        }
        BigInteger result = BigInteger.ONE;
        for (int i = 2; i <= n; i++) {
            result = result.multiply(BigInteger.valueOf(i));
        }
        return result;
    }

   
    private static String formatValue(Object val) {
        if (val instanceof Double) {
            double d = (Double) val;
            if (d == (long) d) {
                return String.format("%.0f", d);
            }
            return String.format("%.2f", d);
        } else if (val instanceof Long) {
            long lv = (Long) val;
            return String.format("%,d", lv);
        } else if (val instanceof BigInteger) {
            BigInteger bv = (BigInteger) val;
            String s = bv.toString();
            if (s.length() > 15) {
                // 科学计数法
                int exp = s.length() - 1;
                String mantissa = s.substring(0, 4);
                return mantissa.substring(0, 1) + "." + mantissa.substring(1) + "e" + exp;
            }
            return String.format("%,d", bv);
        }
        return val.toString();
    }

    private static void printSeparator(int width) {
        System.out.println("┌" + "─".repeat(width - 2) + "┐");
    }

    private static void printThinSeparator(int width) {
        System.out.println("├" + "─".repeat(width - 2) + "┤");
    }

    private static String center(String text, int width) {
        int padding = width - 2 - text.length();
        int leftPad = padding / 2;
        int rightPad = padding - leftPad;
        return "│" + " ".repeat(leftPad) + text + " ".repeat(rightPad) + "│";
    }
}