import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

// Author: Alexander Ye

public class POSTagger {

    public Map<String, Map<String, Double>> observations;
    public Map<String, Map<String, Double>> transitions;

    public POSTagger() {
        this.observations = new HashMap<>();
        this.transitions = new HashMap<>();
    }

    // function for reading part of speech tags
    public static List<String> readFile(String file_name) {
        List<String> lines = new ArrayList<>();
        try {
            BufferedReader input = new BufferedReader(new FileReader(file_name));
            String line;
            while ((line = input.readLine()) != null) {
                lines.add(line);
            }
            input.close();
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
        return lines;
    }

    // function for reading words and lower casing them
    public static List<String> readWords(String file_name) {
        List<String> lines = new ArrayList<>();
        try {
            BufferedReader input = new BufferedReader(new FileReader(file_name));
            String line;
            while ((line = input.readLine()) != null) {
                lines.add(line.toLowerCase());
            }
            input.close();
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
        return lines;
    }

    // helper function to make helper maps that will make observation and transition tables
    public static void updateMap(Map<String, Integer> map, String key) {
        if (map.containsKey(key)) {
            map.put(key, map.get(key) + 1);
        } else
            map.put(key, 1);
    }

    public void train(String word_file, String tag_file) {
        System.out.println("Train model with " + word_file + " and " + tag_file);

        List<String> tag_lines = readFile(tag_file);
        List<String> word_lines = readWords(word_file);

        Map<String, Integer> TagMap = new HashMap<>();
        Map<String, Integer> TagTagMap = new HashMap<>();
        Map<String, Integer> TagWordMap = new HashMap<>();

        int num_lines = tag_lines.size();

        for (int i = 0; i < num_lines; i++) {
            String tag_line = tag_lines.get(i);
            String word_line = word_lines.get(i);

            List<String> tags = Arrays.asList(tag_line.split(" "));
            List<String> words = Arrays.asList(word_line.split(" "));

            String previous = "#";
            TagMap.put(previous, 1);

            for (int j = 0; j < tags.size(); j++) {
                String current = tags.get(j);
                String tag_pair = previous + "_" + current;

                String word = words.get(j);
                String tag_word = current + "_" + word;

                updateMap(TagMap, current);
                updateMap(TagTagMap, tag_pair);
                updateMap(TagWordMap, tag_word);
                previous = current;
            }
        }
        makeTransition(TagMap, TagTagMap);
        makeObservation(TagMap, TagWordMap);
    }

    // creates the observation map
    public void makeObservation(Map<String, Integer> tagMap, Map<String, Integer> tagWordMap) {

        for (String tag_word : tagWordMap.keySet()) {
            double count = tagWordMap.get(tag_word);
            String[] split = tag_word.split("_");
            if (split.length < 2){
                continue;
            }
            String tag = split[0];
            String word = split[1];
            if (tagMap.containsKey(tag)) {

                // count the number of times you see each observation, used this to check for correctness

                //if (! observations.containsKey(tag)) {
                    //observations.put(tag, new HashMap<>());
                    //observations.get(tag).put(word, count);
                //}
                //observations.get(tag).put(word, count);

                // sets the log
                double total = tagMap.get(tag);
                Double prob = Math.log10(count / total);
                setObservationProb(tag, word, prob);
            }
        }
    }

    // creates the transition map
    public void makeTransition(Map<String, Integer> tagMap, Map<String, Integer> tagTagMap) {

        for (String tag_pair : tagTagMap.keySet()) {
            double count = tagTagMap.get(tag_pair);
            String[] pair = tag_pair.split("_");
            if (pair.length < 2)
                continue;
            String tag1 = pair[0];
            String tag2 = pair[1];
            if (tagMap.containsKey(tag1)) {
                // count the number of times of each transition, used this to check for correctness
                //if (! transitions.containsKey(tag1)) {
                    //transitions.put(tag1, new HashMap<>());
                    //transitions.get(tag1).put(tag2, count);
                //}
                //transitions.get(tag1).put(tag2, count);

                // sets the log
                double total = tagMap.get(tag1);
                Double prob = Math.log10(count / total);
                setTransitionProb(tag1, tag2, prob);
            }
        }
    }

    // helper setter functions for setting transition and observation probabilities
    private void setTransitionProb(String tag1, String tag2, Double prob) {
        if (transitions.containsKey(tag1)) {
            transitions.get(tag1).put(tag2,prob);
        }
        else {
            Map<String, Double> map = new HashMap<>();
            map.put(tag2,prob);
            transitions.put(tag1, map);
        }
    }

    private void setObservationProb(String tag, String word, Double prob) {
        if (observations.containsKey(tag)) {
            observations.get(tag).put(word,prob);
        }
        else {
            Map<String, Double> map = new HashMap<>();
            map.put(word,prob);
            observations.put(tag, map);
        }
    }

    // getter functions for setting transition and observation probabilities
    public Double getTransitionProb(String tag1, String tag2) {
        if (! transitions.containsKey(tag1))
            return 0.0;
        Map<String, Double> entry = transitions.get(tag1);
        if (entry.containsKey(tag2)) {
            return entry.get(tag2);
        }
        else
            return 0.0;
    }

    public Double getObservationProb(String tag, String word) {
        String lowercase_word = word;
        if (! observations.containsKey(tag))
            return 0.0;
        Map<String, Double> entry = observations.get(tag);
        if (entry.containsKey(lowercase_word)) {
            return entry.get(lowercase_word);
        }
        // unobserved
        else
            return -8.0;
    }

    // Viterbi algorithm
    public List<String> viterbi(List<String> words) {
        Map<Integer, Map<String, String>> backtrack = new HashMap<>();

        Set<String> currStates = new HashSet<>();
        Map<String, Double> currScores = new HashMap<>();
        currStates.add("#");
        currScores.put("#", 0.0);
        for (int i = 0; i < words.size(); i++) {
            String word = words.get(i);
            Set<String> nextStates = new HashSet<>();
            Map<String, Double> nextScores = new HashMap<>();
            for (String currState : currStates) {
                if (!transitions.containsKey(currState))
                    continue;
                Map<String, Double> tranMap = transitions.get(currState);
                for (String nextState : tranMap.keySet()) {
                    nextStates.add(nextState);
                    Double p1 = currScores.get(currState);
                    Double p2 = getTransitionProb(currState, nextState);
                    Double p3 = getObservationProb(nextState, word);
                    Double prob = p1 + p2 + p3;

                    if (!nextScores.containsKey(nextState) || prob > nextScores.get(nextState)) {
                        nextScores.put(nextState, prob);

                        if (!backtrack.containsKey(i)) {
                            Map<String, String> path = new HashMap<>();
                            path.put(nextState, currState);
                            backtrack.put(i, path);
                        }
                        else {
                            backtrack.get(i).put(nextState, currState);
                        }
                    }
                }
            }
            currStates = nextStates;
            currScores = nextScores;
        }

        List<String> state_name = new ArrayList<>(currStates);
        String currState = state_name.get(0);

        for (int i = 1; i < state_name.size(); i++) {
            if (currScores.get(state_name.get(i)) >= currScores.get(currState)) {
                currState = state_name.get(i);
            }
        }

        //back track
        Stack<String> stack = new Stack<>();
        stack.push(currState);

        for (int i = words.size()-1; i > 0; i--) {
            currState = backtrack.get(i).get(currState);
            stack.push(currState);
        }

        // now show forward path
        List<String> tagList = new ArrayList<>();
        while (!stack.isEmpty()) {
            String s = stack.pop();
            if (!s.equals("#")) {
                tagList.add(s);
            }
        }
        return tagList;
    }

    // run function for file-driven testing
    public void run(String test_word_file, String test_tag_file ) {
        System.out.println("Run test file " + test_word_file);

        List<String> word_lines = readWords(test_word_file);
        List<String> tag_lines = readFile(test_tag_file);

        int correct_cnt = 0;
        int word_cnt = 0;

        for (int i=0; i < word_lines.size(); i++) {
            String word_line = word_lines.get(i);
            String tag_line = tag_lines.get(i);

            List<String> words = Arrays.asList(word_line.split(" "));
            List<String> tags = Arrays.asList(tag_line.split(" "));
            List<String> model_tags = viterbi(words);

            word_cnt += words.size();
            for (int j = 0; j < tags.size(); j++) {
                if (tags.get(j).equals(model_tags.get(j)))
                    correct_cnt++;
            }
        }

        double ratio = (double) correct_cnt/word_cnt;
        System.out.println(word_cnt +" words, tag correct " + correct_cnt + ", wrong " + (word_cnt-correct_cnt) + ", accuracy " + ratio );
    }

    // run function for console-driven testing
    public static void run_input() {
        POSTagger model = new POSTagger();

        String train_word_file = "texts/brown-train-sentences.txt";
        String train_tag_file = "texts/brown-train-tags.txt";

        model.train(train_word_file,train_tag_file);

        System.out.println("Please enter a sentence to be tagged:");
        Scanner input = new Scanner(System.in);
        while (true) {
            String line = input.nextLine();
            List<String> words = Arrays.asList(line.split(" "));
            List<String> tags = model.viterbi(words);
            System.out.println(tags);
        }
    }

    public static void run_simple_tests() {
        POSTagger model = new POSTagger();

        String train_word_file = "texts/simple-train-sentences.txt";
        String train_tag_file = "texts/simple-train-tags.txt";
        model.train(train_word_file,train_tag_file);

        String test_word_file = "texts/simple-test-sentences.txt";
        String test_tag_file = "texts/simple-test-tags.txt";
        model.run(test_word_file, test_tag_file);
    }

    public static void run_brown() {
        POSTagger model = new POSTagger();

        String train_word_file = "texts/brown-train-sentences.txt";
        String train_tag_file = "texts/brown-train-tags.txt";
        model.train(train_word_file,train_tag_file);

        String test_word_file = "texts/brown-test-sentences.txt";
        String test_tag_file = "texts/brown-test-tags.txt";
        model.run(test_word_file, test_tag_file);
    }

    public static void main(String[] args) {
        //run_input();
        //run_simple_tests();
        run_brown();
    }
}
