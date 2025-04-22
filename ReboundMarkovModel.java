import java.io.*;
import java.util.*;

public class ReboundMarkovModel {
    String csvFile = "duke2025.csv";
    ArrayList<String> states = new ArrayList<>();
    Map<String, Map<String, Double>> transitions = new HashMap<>();

    public ReboundMarkovModel(String csvFile) {
        this.csvFile = csvFile;
    }

    public void initializeStates() {
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                System.out.println(Arrays.toString(values));
                boolean isWin = false;
                int trb = Integer.parseInt(values[25].trim());
                String result = values[6].trim();
                if (result.equals("W")) {
                    isWin = true;
                }
                if (trb >= 41 && isWin) {
                    states.add("HW");
                }
                else if (trb >= 41 && !isWin) {
                    states.add("HL");
                }
                else if (trb >= 33 && isWin) {
                    states.add("MW");
                }
                else if (trb >= 33 && !isWin) {
                    states.add("ML");
                }
                else if (isWin) {
                    states.add("LW");
                }
                else {
                    states.add("LL");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        Map<String, Integer> frequency = new HashMap<>();
        for (String s : states) {
            frequency.put(s, frequency.getOrDefault(s, 0) + 1);
        }
        System.out.println("State frequencies:");
        for (String s : frequency.keySet()) {
            System.out.println(s + ": " + frequency.get(s));
        }
    }

    public void initializeProbabilities(){
        String[] possibleStates = {"HW", "HL", "MW", "ML", "LW", "LL"};
        for (String from : possibleStates) {
            transitions.put(from, new HashMap<>());
            for (String to : possibleStates) {
                transitions.get(from).put(to, 0.0);
            }
        }

        for (int i = 0; i < states.size() - 1; i++) {
            String from = states.get(i);
            String to = states.get(i + 1);
            transitions.get(from).put(to, transitions.get(from).get(to) + 1.0);
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter("transition_matrix_output.txt"))) {
            writer.write("Transition Matrix (From → To):\n");
            writer.write("    HW     HL     MW     ML     LW     LL\n");

            for (String from : possibleStates) {
                double total = transitions.get(from).values().stream().mapToDouble(i -> i).sum();
                writer.write(from + " ");
                for (String to : possibleStates) {
                    double prob = total == 0 ? 0.0 : transitions.get(from).get(to) / total;
                    writer.write(String.format("%5.2f ", prob));
                }
                writer.write("\n");
            }

            System.out.println("Transition matrix written to transition_matrix_output.txt");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void initializeLaplace() {
        String[] possibleStates = {"HW", "HL", "MW", "ML", "LW", "LL"};
    
        // Step 1: Laplace-smooth all transitions with 1.0
        for (String from : possibleStates) {
            transitions.put(from, new HashMap<>());
            for (String to : possibleStates) {
                transitions.get(from).put(to, 1.0); // Initialize with 1 for Laplace smoothing
            }
        }
    
        // Step 2: Count observed transitions
        for (int i = 0; i < states.size() - 1; i++) {
            String from = states.get(i);
            String to = states.get(i + 1);
            transitions.get(from).put(to, transitions.get(from).get(to) + 1.0);
        }
    
        // Step 3: Normalize to get probabilities
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("laplace_matrix_output.txt"))) {
            writer.write("Normalized Laplace-Smoothed Transition Matrix (From → To):\n");
            writer.write("    HW     HL     MW     ML     LW     LL\n");
    
            for (String from : possibleStates) {
                Map<String, Double> row = transitions.get(from);
                double rowSum = row.values().stream().mapToDouble(Double::doubleValue).sum();
    
                writer.write(from + " ");
                for (String to : possibleStates) {
                    // Always safe: Laplace ensures all entries are nonzero
                    double prob = row.get(to) / rowSum;
                    row.put(to, prob); // Overwrite counts with normalized probabilities
                    writer.write(String.format("%5.4f ", prob));
                }
                writer.write("\n");
            }
    
            System.out.println("Normalized transition matrix written to laplace_matrix_output.txt");
    
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    

    public void computeLaplaceSteadyState(String[] states, int maxIterations, double tolerance, String outputFileName) {
        int n = states.length;
        double[] pi = new double[n];
        Arrays.fill(pi, 1.0 / n);  // Start with uniform distribution
    
        double[] piNext = new double[n];
    
        for (int iter = 0; iter < maxIterations; iter++) {
            for (int j = 0; j < n; j++) {
                String toState = states[j];
                piNext[j] = 0.0;
    
                for (int i = 0; i < n; i++) {
                    String fromState = states[i];
                    // Use safe access in case something's missing
                    double prob = transitions.getOrDefault(fromState, new HashMap<>()).getOrDefault(toState, 0.0);
                    piNext[j] += pi[i] * prob;
                }
            }
    
            // Check for convergence
            double maxDiff = 0.0;
            for (int k = 0; k < n; k++) {
                maxDiff = Math.max(maxDiff, Math.abs(piNext[k] - pi[k]));
            }
            if (maxDiff < tolerance) {
                break;
            }
    
            // Copy back
            System.arraycopy(piNext, 0, pi, 0, n);
        }
    
        // Output to file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFileName))) {
            writer.write("Steady-State Distribution (Laplace-smoothed):\n");
            for (int i = 0; i < n; i++) {
                writer.write(String.format("%s: %.6f\n", states[i], pi[i]));
                writer.write(String.format("Expected Return Time: %.6f\n", 1/(pi[i])));
            }
            System.out.println("Steady-state distribution written to " + outputFileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void evaluatePredictionAccuracy(String[] possibleStates, String outputFileName) {
        int correct = 0;
        int total = 0;
    
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFileName))) {
            writer.write("Prediction Accuracy Evaluation:\n");
            writer.write("Index,CurrentState,ActualNext,PredictedNext,Correct\n");
    
            for (int i = 0; i < states.size() - 1; i++) {
                String current = states.get(i);
                String actualNext = states.get(i + 1);
    
                Map<String, Double> probs = transitions.getOrDefault(current, new HashMap<>());
    
                String predictedNext = null;
                double maxProb = -1.0;
                for (String candidate : possibleStates) {
                    double prob = probs.getOrDefault(candidate, 0.0);
                    if (prob > maxProb) {
                        maxProb = prob;
                        predictedNext = candidate;
                    }
                }
    
                boolean isCorrect = predictedNext != null && predictedNext.equals(actualNext);
                if (isCorrect) correct++;
                total++;
    
                writer.write(String.format("%d,%s,%s,%s,%s\n", i, current, actualNext, predictedNext, isCorrect ? "YES" : "NO"));
            }
    
            double accuracy = (double) correct / total;
            writer.write(String.format("\nTotal Predictions: %d\nCorrect Predictions: %d\nAccuracy: %.2f%%\n",
                                       total, correct, accuracy * 100));
            System.out.println("Prediction accuracy written to " + outputFileName);
    
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void evaluateNaiveBaselineAccuracy(String outputFileName) {
        // Step 1: Count frequency of each state
        Map<String, Integer> freq = new HashMap<>();
        for (String s : states) {
            freq.put(s, freq.getOrDefault(s, 0) + 1);
        }
    
        // Step 2: Find the most common state
        String mostFrequentState = null;
        int maxCount = 0;
        for (Map.Entry<String, Integer> entry : freq.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                mostFrequentState = entry.getKey();
            }
        }
    
        // Step 3: Compare this prediction to the actual next state
        int correct = 0;
        int total = 0;
        for (int i = 0; i < states.size() - 1; i++) {
            String actualNext = states.get(i + 1);
            if (actualNext.equals(mostFrequentState)) {
                correct++;
            }
            total++;
        }
    
        double accuracy = (double) correct / total;
    
        // Output
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFileName))) {
            writer.write("Naive Baseline Accuracy:\n");
            writer.write("Most Frequent State: " + mostFrequentState + "\n");
            writer.write(String.format("Accuracy: %.2f%% (%d/%d)\n", accuracy * 100, correct, total));
            System.out.println("Naive baseline accuracy written to " + outputFileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    

    

    

    public static void main(String[] args) {
        String csvFile = "duke2025.csv";
        ReboundMarkovModel model = new ReboundMarkovModel(csvFile);
        ReboundMarkovModel laplace = new ReboundMarkovModel(csvFile);
        model.initializeStates();
        model.initializeProbabilities();
        laplace.initializeStates();
        laplace.initializeLaplace();

        String[] possibleStates = {"HW", "HL", "MW", "ML", "LW", "LL"};
        laplace.computeLaplaceSteadyState(possibleStates, 1000, 1e-6, "steady_state_output.txt");
        laplace.evaluatePredictionAccuracy(possibleStates, "prediction_accuracy_output.txt");
        laplace.evaluateNaiveBaselineAccuracy("naive_baseline_accuracy.txt");


    }
}
