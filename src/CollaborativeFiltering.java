/**
 * Created by Hao Xiong haoxiong@outlook.com
 * Mar 17, 2017
 */

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class CollaborativeFiltering {
    private static final String TRAININGDATA = "data/TrainingRatings.txt";
    private static final String TESTINGDATA = "data/TestingRatings.txt";

    public static void main(String[] args) {
        long start = System.nanoTime();

        Map<Integer, User> AllUsers = parseUsers(TRAININGDATA);
        Correlation correlation = new Correlation(AllUsers);
        System.out.println("Correlation Matrix Calculation Finished.");

        Map<Integer, User> TestUsers = parseUsers(TESTINGDATA);
        Set<Map.Entry<Integer, User>> tUsers = TestUsers.entrySet();
        //partition the test user user in order to do multi-thread batch
        List<Map.Entry<Integer, User>> tusers = new ArrayList<>(tUsers);
        int size = tusers.size();
        predictVote(correlation, tusers.subList(0, size / 3), AllUsers);
        predictVote(correlation, tusers.subList(size / 3, 2 * size / 3), AllUsers);
        predictVote(correlation, tusers.subList(2 * size / 3, size - 1), AllUsers);

        System.out.println("Time consumption(s): " + (System.nanoTime() - start) * 1.0e-9);
        memoStat();
    }

    private static void predictVote(Correlation correlation, List<Map.Entry<Integer, User>> tusers, Map<Integer, User> allUsers) {
        new Thread(() -> {
            for (Map.Entry<Integer, User> entry : tusers) {
                User activeUser = entry.getValue();
                int uId = entry.getKey();

                for (int mid : activeUser.ratings.keySet()) {
                    double pv = activeUser.predictedVote(correlation, uId, mid, allUsers);
                    System.out.println("user id: " + uId + " predicted vote for movie " + mid + ": " + pv);
                }
            }
        }).start();
    }

    /**
     * Read the data set file convert them to user list
     */
    private static Map<Integer, User> parseUsers(String name) {
        Map<Integer, User> users = new HashMap<>(30000);
        String line;
        try (BufferedReader br = new BufferedReader(new FileReader(name))) {
            while ((line = br.readLine()) != null) {
                String[] s = line.split(",");
                int[] v = {Integer.parseInt(s[0]), Integer.parseInt(s[1]), (int) Float.parseFloat(s[2])};
                if (users.containsKey(v[1])) {
                    users.get(v[1]).ratings.put(v[0], v[2]);
                } else {
                    users.put(v[1], new User(v[0], v[2]));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (User user : users.values()) {
            user.calMeanVote();
        }
        return users;
    }

    //Heap utilization statistics
    private static void memoStat() {
        double mb = 1024 * 1024;
        //Getting the runtime reference from system
        Runtime runtime = Runtime.getRuntime();
        System.out.println("##### Heap utilization statistics [MB] #####");
        //Print used memory
        System.out.println("Used Memory:"
                + (runtime.totalMemory() - runtime.freeMemory()) / mb);
        //Print free memory
        System.out.println("Free Memory:"
                + runtime.freeMemory() / mb);
        //Print total available memory
        System.out.println("Total Memory:" + runtime.totalMemory() / mb);
        //Print Maximum available memory
        System.out.println("Max Memory:" + runtime.maxMemory() / mb);
    }
}

/**
 * User class contains its ratings
 */
class User {

    private double meanVote;

    //key-Movie Id; value-vote(1-5), the average size is 112 based on given information
    Map<Integer, Integer> ratings = new HashMap<>();

    //Construct a user by its first rating record
    public User(int mid, int vote) {
        ratings.put(mid, vote);
    }

    //Get the rating score user voted for a movie return 0 if had never rate this movie
    double ratingMeanError(int mid) {
        if (ratings.containsKey(mid)) return meanError(mid);
        return -meanVote;
    }

    double meanError(int mid) {
        return ratings.get(mid) - meanVote;
    }

    void calMeanVote() {
        meanVote = (double) ratings.values().stream().mapToInt(Integer::intValue).sum() / ratings.size();
    }

    double predictedVote(Correlation core, int uid, int mid, Map<Integer, User> users) {
        //if (getRating(mid)!=0) return getRating(mid);
        double result = 0;
        double k = 0;
        for (Map.Entry<Integer, User> entry : users.entrySet()) {
            double weight = core.getWeight(uid, entry.getKey());
            if (weight == 0) continue;
            k += Math.abs(weight);
            result += entry.getValue().ratingMeanError(mid) * weight;
        }
        return meanVote + (1 / k) * result;
    }
}

/**
 * Represent the Correlation Triangular Matrices W(i,j)
 */
class Correlation {

    //weights[i][j] represents the correlation between user i and j
    private double[][] weights;

    //The users among which weights are calculated
    private List<Integer> userIds;

    public Correlation(Map<Integer, User> users) {
        userIds = new ArrayList<>(users.keySet());
        Collections.sort(userIds);
        calWeights(users);
    }

    private void calWeights(Map<Integer, User> users) {
        int size = userIds.size();
        weights = new double[size][];
        for (int i = 0; i < size; i++) {
            weights[i] = new double[i + 1];
            User u1 = users.get(userIds.get(i));
            Set<Integer> bothVoted = u1.ratings.keySet();
            for (int j = 0; j < i; j++) {
                //calculate the correlation between user i and user j
                User u2 = users.get(userIds.get(j));
                bothVoted.retainAll(u2.ratings.keySet());
                double s1 = 0, s2 = 0, s3 = 0;
                for (int k : bothVoted) {
                    double v1 = u1.meanError(k);
                    double v2 = u2.meanError(k);
                    s1 += v1 * v2;
                    s2 += v1 * v1;
                    s3 += v2 * v2;
                }
                weights[i][j] = s2 == 0 || s3 == 0 ? 0 : s1 / Math.sqrt(s2 * s3);
            }
            weights[i][i] = 1;
        }
    }

    //If user id has never appeared in training database just return 0;
    double getWeight(int id1, int id2) {
        int i = userIds.indexOf(id1);
        int j = userIds.indexOf(id2);
        if (i != -1 && j != -1) {
            if (i > j) return weights[i][j];
            return weights[j][i];
        }
        return 0;
    }
}