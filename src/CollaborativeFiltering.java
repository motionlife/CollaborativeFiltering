/**
 * Created by Hao Xiong haoxiong@outlook.com
 * Mar 17, 2017
 */

import java.io.*;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

public class CollaborativeFiltering {
    private static final String TRAININGDATA = "data/TrainingRatings.txt";
    private static final String TESTINGDATA = "data/TestingRatings.txt";
    private static final String RESULTTEXT = "result.txt";
    static final int ESTIMATED_SCORE = 3;
    static int[] uidArray;

    public static void main(String[] args) {
        long start = System.nanoTime();
        int[] numberOfItems = new int[1];
        double[] ERROR = new double[4];
        StringBuilder content = new StringBuilder();
        User[] allUsers = parseUsers(TRAININGDATA, true);
        Correlation core = new Correlation(allUsers);
        content.append("Matrix Calculation Finished.\nTime consumption(s): " + (System.nanoTime() - start) * 1.0e-9 + "\n");
        User[] testUsers = parseUsers(TESTINGDATA, false);
        //Todo::Use lambda expression to exploit parallelism
        Arrays.stream(testUsers).forEach(tUser -> {
            content.append("User:" + tUser.userId + "\n");
            Arrays.stream(tUser.movieIds).forEach(mid -> {
                int position;
                double pScore = (position = Arrays.binarySearch(uidArray, tUser.userId)) > -1 ?
                        allUsers[position].predictScore(core, allUsers, mid) : ESTIMATED_SCORE;
                int realRating = tUser.getRating(mid);
                int error = (int) Math.round(pScore) - realRating;
                double error1 = pScore - realRating;
                ERROR[0] += Math.abs(error);
                ERROR[1] += Math.abs(error1);
                ERROR[2] += error * error;
                ERROR[3] += error1 * error1;
                numberOfItems[0]++;
                content.append("Movie:" + mid + " => " + pScore + "(" + realRating + ")\n");
            });
        });
        ERROR[0] = ERROR[0] / numberOfItems[0];
        ERROR[1] = ERROR[1] / numberOfItems[0];
        ERROR[2] = Math.sqrt(ERROR[2] / numberOfItems[0]);
        ERROR[3] = Math.sqrt(ERROR[3] / numberOfItems[0]);
        content.append("Mean Absolute Error: " + ERROR[0] + ", " + ERROR[1]
                + "\nRoot Mean Squared Error: " + ERROR[2] + ", " + ERROR[3] + "\n");
        content.append("Time consumption(s): " + (System.nanoTime() - start) * 1.0e-9);
        saveRunningResult(content.toString(), RESULTTEXT);
        memoStat();
    }

    /**
     * Read the data set file convert them to user list
     */
    private static User[] parseUsers(String name, boolean train) {
        Map<Integer, Map<Integer, Integer>> userRatings = new TreeMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(name))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] s = line.split(",");
                int[] v = {Integer.parseInt(s[0]), Integer.parseInt(s[1]), (int) Float.parseFloat(s[2])};
                if (userRatings.containsKey(v[1])) {
                    userRatings.get(v[1]).put(v[0], v[2]);
                } else {
                    Map<Integer, Integer> rating = new TreeMap<>();
                    rating.put(v[0], v[2]);//if same user rated two movie the previous rating will be overwritten.
                    userRatings.put(v[1], rating);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        User[] users = new User[userRatings.size()];
        if (train) uidArray = new int[users.length];
        int i = 0;
        for (int uid : userRatings.keySet()) {
            if (train) uidArray[i] = uid;
            User user = new User(uid);
            user.unpack(userRatings.get(uid), train);
            users[i++] = user;
        }
        return users;
    }

    /**
     * Save the running result to file
     */
    private static boolean saveRunningResult(String content, String filename) {
        boolean success = false;
        File file = new File(filename);
        try {
            if (!file.exists()) success = file.createNewFile();
            PrintWriter pr = new PrintWriter(file);
            pr.write(content);
            pr.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return success;
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

    int[] movieIds;
    int[] ratings;
    double meanRating;
    int userId;

    //Construct a user by its first rating record
    public User(int uid) {
        userId = uid;
    }

    /**
     * Call after all the users has been read from database
     */
    void unpack(Map<Integer, Integer> scores, boolean train) {
        if (train) meanRating = (double) scores.values().stream().mapToInt(Integer::intValue).sum() / scores.size();
        int size = scores.size();
        movieIds = new int[size];
        ratings = new int[size];
        int i = 0;
        for (Map.Entry<Integer, Integer> rating : scores.entrySet()) {
            movieIds[i] = rating.getKey();
            ratings[i++] = rating.getValue();
        }
    }

    /**
     * return the rated score of movie specified by mid
     */
    int getRating(int mid) {
        int index;
        return (index = Arrays.binarySearch(movieIds, mid)) > -1 ? ratings[index] : CollaborativeFiltering.ESTIMATED_SCORE;
    }

    /**
     * Method used to calculate the predicted rating for one movie
     */
    double predictScore(Correlation core, User[] users, int mid) {
        final double[] result = {0};
        final double[] norm = {0};
        //Todo::Use lambda expression to exploit parallelism
        Arrays.stream(users).forEach(user -> {
            double w = core.getWeight(userId, user.userId);
            if (w != 0) {
                norm[0] += Math.abs(w);
                result[0] += (user.getRating(mid) - user.meanRating) * w;
            }
        });
        return meanRating + result[0] / norm[0];
    }
}

/**
 * Represent the Correlation Triangular Matrices W(i,j)
 * Calculate the correlation weight between user i and user j
 */
class Correlation {
    //weights[i][j] represents the correlation between user i and j
    private double[][] weights;

    public Correlation(User[] users) {
        int size = CollaborativeFiltering.uidArray.length;
        weights = new double[size][];
        for (int i = 0; i < size; i++) {
            weights[i] = new double[i + 1];
            User u1 = users[i];
            for (int j = 0; j < i; j++) {
                User u2 = users[j];
                int m = 0;
                int n = 0;
                double s1 = 0, s2 = 0, s3 = 0;
                while (m < u1.movieIds.length && n < u2.movieIds.length) {
                    int mid1 = u1.movieIds[m];
                    int mid2 = u2.movieIds[n];
                    if (mid1 < mid2) {
                        m++;
                    } else if (mid1 > mid2) {
                        n++;
                    } else {
                        double v1 = u1.getRating(mid1) - u1.meanRating;
                        double v2 = u2.getRating(mid1) - u2.meanRating;
                        s1 += v1 * v2;
                        s2 += v1 * v1;
                        s3 += v2 * v2;
                        m++;
                        n++;
                    }
                }
                if ((s3 *= s2) != 0) weights[i][j] = s1 / Math.sqrt(s3);
            }
            weights[i][i] = 1;
        }
    }

    double getWeight(int id1, int id2) {
        int i = Arrays.binarySearch(CollaborativeFiltering.uidArray, id1);//-!performance critical
        int j = Arrays.binarySearch(CollaborativeFiltering.uidArray, id2);//-!performance critical
        if (i > j) return weights[i][j];
        return weights[j][i];
    }
}