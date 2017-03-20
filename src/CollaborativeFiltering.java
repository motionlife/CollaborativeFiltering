/**
 * Created by Hao Xiong haoxiong@outlook.com
 * Mar 17, 2017
 */

import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class CollaborativeFiltering {
    private static final String TRAININGDATA = "data/TrainingRatings.txt";
    private static final String TESTINGDATA = "data/TestingRatings.txt";
    private static final String RESULTTEXT = "result.txt";
    private static final int ESTIMATED_SCORE = 3;
    private static long STARTTIME;

    public static void main(String[] args) {
        STARTTIME = System.nanoTime();
        int[] numberOfItems = new int[1];
        double[] ERROR = new double[2];
        StringBuilder content = new StringBuilder();
        User[] allUsers = parseUsers(TRAININGDATA, true);
        content.append(log("Finished parsing training data."));
        Correlation core = new Correlation(allUsers);
        content.append(log("Finished Matrix Calculation."));
        User[] testUsers = parseUsers(TESTINGDATA, false);
        content.append(log("Finished parsing testing data."));
        //Todo::Use lambda expression to exploit parallelism
        Arrays.stream(testUsers).forEach(tUser -> {
            content.append("User:" + tUser.userId + "\n");
            tUser.ratings.keySet().forEach(mid -> {
                Integer position;
                double pScore = (position = User.IdMap.get(tUser.userId)) != null ?
                        allUsers[position].predictScore(core, allUsers, mid) : ESTIMATED_SCORE;
                int realRating = tUser.ratings.get(mid);
                double error = pScore - realRating;
                ERROR[0] += Math.abs(error);
                ERROR[1] += error * error;
                numberOfItems[0]++;
                content.append("Movie:" + mid + " => " + pScore + "(" + realRating + ")\n");
            });
        });
        ERROR[0] = ERROR[0] / numberOfItems[0];
        ERROR[1] = Math.sqrt(ERROR[1] / numberOfItems[0]);
        content.append(log("\n\nMean Absolute Error: " + ERROR[0] + "\nRoot Mean Squared Error: " + ERROR[1]));
        saveRunningResult(content.toString(), RESULTTEXT);
        memoStat();
    }

    /**
     * Read the data set file convert them to user list
     */
    private static User[] parseUsers(String name, boolean isTrain) {
        Map<Integer, User> userMap = new HashMap<>(30000);
        try (BufferedReader br = new BufferedReader(new FileReader(name))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] s = line.split(",");
                int[] v = {Integer.parseInt(s[0]), Integer.parseInt(s[1]), (int) Float.parseFloat(s[2])};
                if (userMap.containsKey(v[1])) {
                    userMap.get(v[1]).ratings.put(v[0], v[2]);
                } else {
                    userMap.put(v[1], new User(v[0], v[1], v[2]));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        User[] users = new User[userMap.size()];
        int i = 0;
        for (int uid : userMap.keySet()) {
            if (isTrain) User.IdMap.put(uid, i);
            User user = userMap.get(uid);
            user.doJobs(isTrain);
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

    private static String log(String debug) {
        String ouput = "Time(s):" + (System.nanoTime() - STARTTIME) * 1.0e-9 + " " + debug + "\n";
        System.out.print(ouput);
        return ouput;
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

    Map<Integer, Integer> ratings;
    int[] movieIds;
    double meanScore;
    int userId;
    //cache the rating of latest found movie
    private Integer cache;
    //map between the index of user in array and user id
    static Map<Integer, Integer> IdMap = new HashMap<>(30000);

    //Construct a user by its first rating record
    public User(int mid, int uid, int rating) {
        userId = uid;
        ratings = new HashMap<>();
        ratings.put(mid, rating);
    }

    /**
     * Call after all the users has been read from database
     */
    void doJobs(boolean isTrain) {
        if (isTrain) {
            int size = ratings.size();
            meanScore = (double) ratings.values().stream().mapToInt(Integer::intValue).sum() / size;
            movieIds = new int[size];
            size = 0;
            for (int mid : ratings.keySet()) movieIds[size++] = mid;
            Arrays.sort(movieIds);//crucial!!!
        }
    }

    /**
     * return true if the user has rated movie specified by mid
     */
    boolean hasRated(int mid) {
        return (cache = ratings.get(mid)) != null;
    }

    /**
     * Method used to calculate the predicted rating for one movie
     */
    double predictScore(Correlation core, User[] users, int mid) {
        final double[] result = {0};
        final double[] norm = {0};
        //Todo::Use lambda expression to exploit parallelism
        Arrays.stream(users).filter(user -> user.hasRated(mid)).forEach(user -> {
            double w = core.getWeight(userId, user.userId);
            if (w != 0) {
                norm[0] += Math.abs(w);
                result[0] += (user.cache - user.meanScore) * w;
            }
        });
        return meanScore + (norm[0] > 0 ? result[0] / norm[0] : 0);
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
        int size = User.IdMap.size();
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
                        double v1 = u1.ratings.get(mid1) - u1.meanScore;//-!performance critical
                        double v2 = u2.ratings.get(mid1) - u2.meanScore;
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
            System.out.println(i);//--------------speed check out-------------------
        }
    }

    double getWeight(int id1, int id2) {
        int i = User.IdMap.get(id1);//-!performance critical
        int j = User.IdMap.get(id2);//-!performance critical
        if (i > j) return weights[i][j];
        return weights[j][i];
    }
}