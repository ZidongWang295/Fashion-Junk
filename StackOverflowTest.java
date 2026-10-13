package shujvjiegou;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 递归阶乘 StackOverflowError 边界测试
 * 
 * 本程序自动探测操作系统的实际栈大小限制（而非JVM默认的1MB软件限制），
 * 在该限制下测试最大递归深度，并与JVM默认栈大小下的结果对比。
 * 
 * 支持 Linux / macOS / Windows 平台。
 */
public class StackOverflowTest {

    // ==================== 递归方法 ====================

    /**
     * 最小栈帧的递归方法（void返回，仅一个int参数，无局部变量）
     * 用于测试纯粹的最大递归深度，不受返回值或局部变量影响
     */
    static void recurseVoid(int n) {
        if (n <= 0) return;
        recurseVoid(n - 1);
    }

    /**
     * 带返回值的递归阶乘（long），栈帧稍大，更贴近实际使用场景
     */
    static long factorial(int n) {
        if (n <= 1) return 1L;
        return n * factorial(n - 1);
    }

    // ==================== 系统栈限制探测 ====================

    /**
     * 尝试获取操作系统的线程栈大小软限制（单位：字节）
     * 支持 Linux/macOS（通过 ulimit -s）和 Windows（通过 PowerShell 或默认值）
     * @return 栈大小（字节），若无法获取返回 -1
     */
    static long getSystemStackLimit() {
        String osName = System.getProperty("os.name").toLowerCase();

        if (osName.contains("linux") || osName.contains("mac") || osName.contains("darwin")) {
            return getUnixStackLimit();
        } else if (osName.contains("windows")) {
            return getWindowsStackLimit();
        }
        return -1;
    }

    /**
     * Linux/macOS: 通过 ulimit -s 获取栈大小（单位：KB）
     */
    private static long getUnixStackLimit() {
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", "ulimit -s");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = reader.readLine();
                p.waitFor();
                if (line != null && !line.trim().isEmpty()) {
                    long limitKB = Long.parseLong(line.trim());
                    if (limitKB > 0) {
                        return limitKB * 1024L; // 转换为字节
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[警告] 无法通过 ulimit 获取系统栈限制: " + e.getMessage());
        }

        // 尝试从 /proc/self/limits 读取（Linux特有）
        try {
            ProcessBuilder pb = new ProcessBuilder("cat", "/proc/self/limits");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // 查找 "Max stack size" 行
                    if (line.contains("stack") && line.contains("bytes")) {
                        // 格式: Max stack size       8388608           unlimited            bytes
                        String[] parts = line.trim().split("\\s+");
                        for (int i = 0; i < parts.length - 1; i++) {
                            if (parts[i].equalsIgnoreCase("bytes") || 
                                (i > 0 && parts[i-1].equalsIgnoreCase("stack"))) {
                                try {
                                    return Long.parseLong(parts[i]);
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                        // 尝试解析数字部分
                        for (String part : parts) {
                            try {
                                long val = Long.parseLong(part);
                                if (val > 4096) { // 栈大小至少大于4KB
                                    return val;
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
                p.waitFor();
            }
        } catch (Exception e) {
            // 忽略
        }
        return -1;
    }

    /**
     * Windows: 尝试通过 PowerShell 获取，失败则返回默认值 1MB
     */
    private static long getWindowsStackLimit() {
        try {
            ProcessBuilder pb = new ProcessBuilder("powershell", "-Command",
                "(Get-ItemProperty -Path 'HKLM:\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\SubSystems' -Name Windows).Windows");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = reader.readLine();
                p.waitFor();
                if (line != null) {
                    // 查找 SharedSection= 后面的第三个数字（栈大小，单位KB）
                    int idx = line.indexOf("SharedSection=");
                    if (idx >= 0) {
                        String sub = line.substring(idx + "SharedSection=".length());
                        String[] parts = sub.split(",");
                        if (parts.length >= 3) {
                            String stackPart = parts[2].trim();
                            // 去除可能的非数字字符
                            stackPart = stackPart.replaceAll("[^0-9]", "");
                            if (!stackPart.isEmpty()) {
                                return Long.parseLong(stackPart) * 1024L;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 忽略
        }
        // Windows 默认线程栈大小通常为 1MB
        System.out.println("[信息] Windows 系统，使用默认栈大小 1MB（可通过注册表修改）");
        return 1024L * 1024L;
    }

    // ==================== 线程栈深度测试 ====================

    /**
     * 在指定栈大小的线程中测试递归深度
     * @param stackSize 栈大小（字节），0 表示使用 JVM 默认
     * @param useVoidRecurse 是否使用最小栈帧的 void 递归
     * @return 测试结果（最大深度、是否溢出等）
     */
    static TestResult testWithStackSize(long stackSize, boolean useVoidRecurse) {
        final int[] depthReached = {-1};
        final boolean[] overflow = {false};
        final boolean[] threadFailed = {false};
        final String[] failReason = {null};

        Runnable task = () -> {
            try {
                if (useVoidRecurse) {
                    // 使用二分搜索快速定位边界
                    int low = 0;
                    int high = Integer.MAX_VALUE - 1;
                    int lastSuccess = 0;

                    // 先用指数增长找到上界
                    int probe = 1;
                    while (probe > 0) {
                        try {
                            recurseVoid(probe);
                            lastSuccess = probe;
                            if (probe > Integer.MAX_VALUE / 2) break;
                            probe *= 2;
                        } catch (StackOverflowError e) {
                            high = probe;
                            break;
                        }
                    }

                    // 二分搜索精确边界
                    low = lastSuccess;
                    while (low < high - 1) {
                        int mid = low + (high - low) / 2;
                        try {
                            recurseVoid(mid);
                            low = mid; // 成功
                        } catch (StackOverflowError e) {
                            high = mid; // 溢出
                        }
                    }
                    depthReached[0] = low;
                } else {
                    // 使用阶乘递归测试
                    int low = 0;
                    int high = Integer.MAX_VALUE - 1;
                    int lastSuccess = 0;

                    int probe = 1;
                    while (probe > 0) {
                        try {
                            factorial(probe);
                            lastSuccess = probe;
                            if (probe > Integer.MAX_VALUE / 2) break;
                            probe *= 2;
                        } catch (StackOverflowError e) {
                            high = probe;
                            break;
                        }
                    }

                    low = lastSuccess;
                    while (low < high - 1) {
                        int mid = low + (high - low) / 2;
                        try {
                            factorial(mid);
                            low = mid;
                        } catch (StackOverflowError e) {
                            high = mid;
                        }
                    }
                    depthReached[0] = low;
                }
            } catch (StackOverflowError e) {
                overflow[0] = true;
                depthReached[0] = -1;
            } catch (OutOfMemoryError e) {
                threadFailed[0] = true;
                failReason[0] = "内存不足，无法分配" + formatBytes(stackSize) + "的栈空间";
            }
        };

        Thread t;
        if (stackSize == 0) {
            t = new Thread(null, task, "TestThread-Default", 0);
        } else {
            // 确保栈大小在合理范围内（至少64KB，这是JVM最小栈大小）
            long effectiveSize = Math.max(stackSize, 64 * 1024);
            t = new Thread(null, task, "TestThread-" + formatBytes(effectiveSize), effectiveSize);
        }

        try {
            t.start();
            t.join(120_000); // 最多等待2分钟（大栈下二分搜索可能较慢）
            if (t.isAlive()) {
                t.interrupt();
                return new TestResult(-1, true, "测试超时（超过2分钟）");
            }
        } catch (OutOfMemoryError e) {
            return new TestResult(-1, true, "无法创建线程：内存不足，栈大小" + formatBytes(stackSize));
        } catch (Exception e) {
            return new TestResult(-1, true, "测试异常：" + e.getMessage());
        }

        if (threadFailed[0]) {
            return new TestResult(-1, true, failReason[0]);
        }
        if (overflow[0]) {
            return new TestResult(-1, true, "首个递归调用即溢出");
        }
        return new TestResult(depthReached[0], false, null);
    }

    static class TestResult {
        int maxDepth;
        boolean failed;
        String errorMsg;

        TestResult(int maxDepth, boolean failed, String errorMsg) {
            this.maxDepth = maxDepth;
            this.failed = failed;
            this.errorMsg = errorMsg;
        }
    }

    // ==================== 工具方法 ====================

    static String formatBytes(long bytes) {
        if (bytes >= 1024 * 1024 * 1024) {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        } else if (bytes >= 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else if (bytes >= 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return bytes + " B";
        }
    }

    static void printSeparator(int width, char c) {
        for (int i = 0; i < width; i++) System.out.print(c);
        System.out.println();
    }

    // ==================== 主程序 ====================

    public static void main(String[] args) {
        System.out.println();
        printSeparator(80, '=');
        System.out.println("     递归栈深度极限测试 —— 使用操作系统真实栈限制");
        printSeparator(80, '=');
        System.out.println();

        // 1. 打印环境信息
        System.out.println("【系统环境信息】");
        System.out.println("  操作系统:      " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        System.out.println("  系统架构:      " + System.getProperty("os.arch"));
        System.out.println("  Java 版本:     " + System.getProperty("java.version"));
        System.out.println("  JVM 名称:      " + System.getProperty("java.vm.name"));
        System.out.println("  可用处理器数:  " + Runtime.getRuntime().availableProcessors());
        long maxMem = Runtime.getRuntime().maxMemory();
        System.out.println("  JVM 最大内存:  " + formatBytes(maxMem));
        System.out.println();

        // 2. 探测系统栈限制
        long systemStackLimit = getSystemStackLimit();
        long jvmDefaultStack;
        try {
            // 探测JVM默认栈大小（通过创建一个默认线程并读取其信息）
            jvmDefaultStack = getJvmDefaultStackSize();
        } catch (Exception e) {
            jvmDefaultStack = 1024 * 1024; // 假设1MB
        }

        System.out.println("【栈大小限制】");
        if (systemStackLimit > 0) {
            System.out.println("  操作系统栈限制: " + formatBytes(systemStackLimit) + " (系统真实限制)");
        } else {
            // 使用常见默认值
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("linux") || os.contains("mac")) {
                systemStackLimit = 8L * 1024 * 1024;
                System.out.println("  操作系统栈限制: " + formatBytes(systemStackLimit) + " (Linux/macOS 典型默认值)");
            } else {
                systemStackLimit = 1024L * 1024;
                System.out.println("  操作系统栈限制: " + formatBytes(systemStackLimit) + " (Windows 典型默认值)");
            }
        }
        System.out.println("  JVM 默认栈大小: " + formatBytes(jvmDefaultStack) + " (由 -Xss 参数决定)");
        System.out.println();

        // 3. 测试不同栈大小下的递归深度
        System.out.println("【递归深度测试】（使用最小栈帧 void 递归）");
        System.out.println();

        List<StackTestEntry> results = new ArrayList<>();

        // 测试1: JVM默认栈大小
        System.out.println("→ 测试 JVM 默认栈大小 (" + formatBytes(jvmDefaultStack) + ")...");
        TestResult r1 = testWithStackSize(0, true);
        results.add(new StackTestEntry("JVM默认栈", jvmDefaultStack, r1));
        System.out.println("   结果: 最大深度 = " + (r1.failed ? r1.errorMsg : String.valueOf(r1.maxDepth)));

        // 测试2: 操作系统栈限制
        System.out.println("→ 测试操作系统栈限制 (" + formatBytes(systemStackLimit) + ")...");
        TestResult r2 = testWithStackSize(systemStackLimit, true);
        results.add(new StackTestEntry("系统栈限制", systemStackLimit, r2));
        System.out.println("   结果: 最大深度 = " + (r2.failed ? r2.errorMsg : String.valueOf(r2.maxDepth)));

        // 测试3: 如果系统限制 > JVM默认，尝试更大的栈
        if (systemStackLimit > jvmDefaultStack * 2) {
            long hugeStack = systemStackLimit * 2;
            System.out.println("→ 测试超大栈 (" + formatBytes(hugeStack) + ", 系统限制的2倍)...");
            TestResult r3 = testWithStackSize(hugeStack, true);
            results.add(new StackTestEntry("超大栈(2x系统限制)", hugeStack, r3));
            System.out.println("   结果: 最大深度 = " + (r3.failed ? r3.errorMsg : String.valueOf(r3.maxDepth)));
        }

        // 测试4: 使用阶乘递归（栈帧更大）
        System.out.println();
        System.out.println("→ 测试阶乘递归（栈帧含long返回值）在系统栈限制下的深度...");
        TestResult r4 = testWithStackSize(systemStackLimit, false);
        results.add(new StackTestEntry("阶乘递归(系统栈)", systemStackLimit, r4));
        System.out.println("   结果: 最大深度 = " + (r4.failed ? r4.errorMsg : String.valueOf(r4.maxDepth)));

        // 4. 汇总表格
        System.out.println();
        printSeparator(80, '=');
        System.out.println("                        【测试结果汇总】");
        printSeparator(80, '=');
        System.out.printf("  %-25s | %-15s | %-20s%n", "测试场景", "栈大小", "最大递归深度");
        printSeparator(80, '-');
        for (StackTestEntry entry : results) {
            String depthStr = entry.result.failed ? "失败: " + entry.result.errorMsg : String.valueOf(entry.result.maxDepth);
            System.out.printf("  %-25s | %-15s | %-20s%n", entry.name, formatBytes(entry.stackSize), depthStr);
        }
        printSeparator(80, '=');

        // 5. 结论
        System.out.println();
        System.out.println("【结论】");
        TestResult systemResult = r2;
        TestResult defaultResult = r1;
        if (!systemResult.failed && !defaultResult.failed) {
            System.out.println("  JVM默认栈限制下的递归深度: " + defaultResult.maxDepth + " 层");
            System.out.println("  系统真实栈限制下的递归深度: " + systemResult.maxDepth + " 层");
            System.out.println("  系统限制是JVM默认限制的 " + 
                String.format("%.1f", (double) systemResult.maxDepth / defaultResult.maxDepth) + " 倍");
            System.out.println("  这得益于系统栈大小 (" + formatBytes(systemStackLimit) + 
                ") 远大于 JVM 默认栈 (" + formatBytes(jvmDefaultStack) + ")");
        } else {
            System.out.println("  系统真实栈限制下最大递归深度: " + 
                (systemResult.failed ? systemResult.errorMsg : String.valueOf(systemResult.maxDepth)));
        }
        System.out.println();
        System.out.println("  注: 若需更深递归，可在 Linux/macOS 上执行:");
        System.out.println("      ulimit -s unlimited   (设置栈为无限制)");
        System.out.println("    然后重新运行本程序。");
        System.out.println("    在 JVM 层面也可通过 -Xss<size> 调整，如 -Xss8m");
        System.out.println();
    }

    static class StackTestEntry {
        String name;
        long stackSize;
        TestResult result;

        StackTestEntry(String name, long stackSize, TestResult result) {
            this.name = name;
            this.stackSize = stackSize;
            this.result = result;
        }
    }

    /**
     * 尝试探测JVM的默认栈大小
     */
    static long getJvmDefaultStackSize() {
        // 通过创建一个默认线程并读取 /proc/self/status 或使用 JMX 来探测
        // 这里使用经验值：大多数JVM默认栈大小为1MB，但可以通过检测来验证
        // 在Linux上，可以读取 /proc/<pid>/task/<tid>/limits 来获取
        try {
            String osName = System.getProperty("os.name").toLowerCase();
            if (osName.contains("linux")) {
                // 获取当前线程的栈限制
                ProcessBuilder pb = new ProcessBuilder("sh", "-c",
                    "cat /proc/" + ProcessHandle.current().pid() + "/limits 2>/dev/null | grep 'Max stack size'");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line = reader.readLine();
                    p.waitFor();
                    if (line != null) {
                        // 格式: Max stack size       8388608           unlimited            bytes
                        String[] parts = line.trim().split("\\s+");
                        for (String part : parts) {
                            try {
                                long val = Long.parseLong(part);
                                if (val > 4096 && val < 1024L * 1024 * 1024) {
                                    return val;
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 忽略
        }
        // 默认假设1MB
        return 1024L * 1024L;
    }
}