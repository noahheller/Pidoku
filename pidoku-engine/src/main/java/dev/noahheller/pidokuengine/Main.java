package dev.noahheller.pidokuengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.noahheller.pidokuengine.generation.HumanPidokuGenerator;
import dev.noahheller.pidokuengine.generation.PidokuBacktrackSolver;
import dev.noahheller.pidokuengine.strategy.StrategyEnum;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        int seed = 1234;
        Random random = new Random(seed);

        PidokuRules rules = new PidokuRules(9);
        PidokuBacktrackSolver solver = new PidokuBacktrackSolver(rules, seed);
        HumanPidokuGenerator generator = new HumanPidokuGenerator(rules, solver, random.nextLong());
        PidokuBuilder builder = new PidokuBuilder(rules, solver, generator);
        for (DifficultyProfile profile: DifficultyProfile.premadeProfiles()){
            PidokuPuzzle generate = builder.generate(profile);
            writeStats(generate, String.format("output/Pidoku-%d-%s-stats.json", seed, profile.name));
            write2DArrayToCSV(generate.getPuzzle(), String.format("output/Pidoku-%d-%s.csv", seed, profile.name));
            write2DArrayToCSV(generate.getSolution(), String.format("output/Pidoku-%d-%s-solution.csv", seed, profile.name));
        }
        DifficultyProfile.Builder difficultyBuilder = new DifficultyProfile.Builder("relaxing");
        difficultyBuilder.clueRange(25,35);
        for (StrategyEnum e: StrategyEnum.values()){
            if (!e.equals(StrategyEnum.NAKED_SINGLE)){
                difficultyBuilder.maxUses(e, 0);
            }
        }
        DifficultyProfile profile = difficultyBuilder.build();
        PidokuPuzzle generate = builder.generate(profile);
        writeStats(generate, String.format("output/Pidoku-%d-%s-stats.json", seed, profile.name));
        write2DArrayToCSV(generate.getPuzzle(), String.format("output/Pidoku-%d-%s.csv", seed, profile.name));
        write2DArrayToCSV(generate.getSolution(), String.format("output/Pidoku-%d-%s-solution.csv", seed, profile.name));

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