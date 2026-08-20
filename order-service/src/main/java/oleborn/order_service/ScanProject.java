package oleborn.order_service;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ScanProject {
    private static final List<String> INCLUDED_EXTENSIONS = List.of(
            ".java", ".gradle", ".kt", ".kts", ".xml", ".yml", ".yaml",
            ".properties", ".json", ".pom",
//            ".md",
            ".txt"
    );

    private static final List<String> EXCLUDED_DIRS = List.of(
            ".git", ".gradle", "build", "target", "out", "bin", ".idea", "node_modules"
    );

    public static void main(String[] args) {
        String projectRoot = args.length > 0 ? args[0] : ".";
        String outputFile = args.length > 1 ? args[1] : "project_code.txt";

        try {
            scanProject(projectRoot, outputFile);
            System.out.println("Project scanned successfully! Output: " + outputFile);
        } catch (IOException e) {
            System.err.println("Error scanning project: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void scanProject(String rootPath, String outputFile) throws IOException {
        Path root = Paths.get(rootPath).toAbsolutePath();
        List<Path> files = new ArrayList<>();

        // Собираем все файлы
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String dirName = dir.getFileName().toString();
                if (EXCLUDED_DIRS.contains(dirName)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (isIncludedFile(file)) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        // Сортируем файлы для удобства чтения
        files.sort(Comparator.comparingInt(Path::getNameCount).thenComparing(p -> p));

        // Записываем в выходной файл
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            writer.println("PROJECT STRUCTURE:");
            writer.println("==================");
            for (Path file : files) {
                writer.println(root.relativize(file));
            }

            writer.println("\n\nSOURCE CODE:");
            writer.println("============");

            for (Path file : files) {
                writer.println("\n" + "=".repeat(80));
                writer.println("FILE: " + root.relativize(file));
                writer.println("=".repeat(80));

                try {
                    List<String> lines = Files.readAllLines(file);
                    for (String line : lines) {
                        writer.println(line);
                    }
                } catch (IOException e) {
                    writer.println("ERROR READING FILE: " + e.getMessage());
                }
            }
        }
    }

    private static boolean isIncludedFile(Path file) {
        String fileName = file.getFileName().toString();
        return INCLUDED_EXTENSIONS.stream()
                .anyMatch(ext -> fileName.toLowerCase().endsWith(ext));
    }
}
