package dev.noahheller.pidokuengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.noahheller.pidokuengine.strategy.Tier;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) throws IOException {
        Files.createDirectories(Path.of("output"));
        int seed = 1234;
        PidokuBuilder builder = PidokuBuilder.standard(new PidokuRules(9));
        for (DifficultyProfile profile: DifficultyProfile.premadeProfiles()){
            generateAndWrite(builder, profile, seed);
        }
        // Between Trivial and Easy: singles only, with enough clues that there's
        // usually more than one move available.
        DifficultyProfile profile = new DifficultyProfile.Builder("relaxing")
                .clueRange(38, 45)
                .scoreRange(25, 45)
                .ceiling(Tier.BEGINNER)
                .floor(Tier.BEGINNER)
                .build();
        generateAndWrite(builder, profile, seed);
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

    public static void write2DArrayToCSV(int[][] data, String filePath) {
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

    private static void printGrid(int[][] grid) {
        for (int row = 0; row < grid.length; row++) {
            StringBuilder line = new StringBuilder();
            for (int col = 0; col < grid.length; col++) {
                int value = grid[row][col];
                if (value == 0) {
                    line.append(".");
                } else {
                    line.append(value);
                }
                if (col < grid.length - 1) {
                    line.append(' ');
                }
            }
            System.out.println(line);
        }
    }
}