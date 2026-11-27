package dev.noahheller.pidokuengine;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes one puzzle per premade profile, with its stats and solution, to {@code output/}. */
public class Main {
    public static void main(String[] args) throws IOException {
        Files.createDirectories(Path.of("output"));
        int seed = 1234;
        PidokuBuilder builder = PidokuBuilder.standard(new PidokuRules(9));
        for (DifficultyProfile profile: DifficultyProfile.premadeProfiles()){
            generateAndWrite(builder, profile, seed);
        }
    }

    /** Generates one puzzle and writes its files; reports and skips the profile if generation fails. */
    private static void generateAndWrite(PidokuBuilder builder, DifficultyProfile profile, int seed) {
        PidokuPuzzle puzzle = builder.generate(profile, seed);
        if (puzzle == null) {
            System.err.println("Could not generate a puzzle for " + profile + "; skipping it.");
            return;
        }
        writeStats(puzzle, String.format("output/Pidoku-%d-%s-stats.json", seed, profile.name));
        write2DArrayToCSV(puzzle.getPuzzle(), String.format("output/Pidoku-%d-%s.csv", seed, profile.name));
        write2DArrayToCSV(puzzle.getSolution(), String.format("output/Pidoku-%d-%s-solution.csv", seed, profile.name));
    }

    private static void writeStats(PidokuPuzzle puzzle, String filePath) {
        ObjectMapper mapper = new ObjectMapper();
        try (FileWriter writer = new FileWriter(filePath)) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(writer, puzzle);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void write2DArrayToCSV(int[][] data, String filePath) {
        try (FileWriter writer = new FileWriter(filePath)) {
            for (int[] row : data) {
                for (int i = 0; i < row.length; i++) {
                    writer.append(String.valueOf(row[i]).charAt(0));
                    if (i < row.length - 1) {
                        writer.append(",");
                    }
                }
                writer.append("\n");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
