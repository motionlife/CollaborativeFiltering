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
    static int[] uidArray;

    public static void main(String[] args) {
        long start = System.nanoTime();

        User[] allUsers = parseUsers(TRAININGDATA);
        Correlation core = new Correlation(allUsers);
        System.out.println("Matrix Calculation Finished.\nTime consumption(s): " + (System.nanoTime() - start) * 1.0e-9);
        User[] testUsers = parseUsers(TESTINGDATA);
        for (User actUser : testUsers) {
            for (int mid : actUser.ratings.keySet()) {
                double pv = 0;
                int position;
                if ((position = Arrays.binarySearch(uidArray, actUser.userId)) != -1) {
                    pv = allUsers[position].predictedVote(core, allUsers, mid);
                }
                System.out.println("User: " + actUser.userId + " Movie: " + mid + " => " + pv + "(" + actUser.ratings.get(mid) + ")");
            }
        }

        System.out.println("Time consumption(s): " + (System.nanoTime() - start) * 1.0e-9);
        memoStat();
    }

    /**
     * Read the data set file convert them to user list
     */
    private static User[] parseUsers(String name) {
        Map<Integer, User> userMap = new HashMap<>(30000);
        String line;
        try (BufferedReader br = new BufferedReader(new FileReader(name))) {
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
        if (uidArray == null) {
            uidArray = new int[users.length];
            int i = 0, j = 0;
            for (int uid : userMap.keySet()) {
                uidArray[i++] = uid;
            }
            Arrays.sort(uidArray);//For binary search
            for (int uid : uidArray) {
                User user = userMap.get(uid);
                user.calMeanVote();
                users[j++] = user;
            }
        } else {
            users = userMap.values().toArray(users);
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

    double meanRating;

    //key-Movie Id; value-vote(1-5), the average size is 112 based on given information
    Map<Integer, Integer> ratings;

    int userId;

    //Construct a user by its first rating record
    public User(int mid, int uid, int vote) {
        userId = uid;
        ratings = new HashMap<>();
        ratings.put(mid, vote);
    }

    //Get the rating score user voted for a movie return 0 if had never rate this movie
    double ratingMeanError(int mid) {
        if (ratings.containsKey(mid)) return ratings.get(mid) - meanRating;
        return -meanRating;
    }

    void calMeanVote() {
        meanRating = (double) ratings.values().stream().mapToInt(Integer::intValue).sum() / ratings.size();
    }

    //Method used to calculate the predicted rating for one movie
    double predictedVote(Correlation core, User[] users, int mid) {
        //if (getRating(mid)!=0) return getRating(mid);
        double result = 0;
        double k = 0;
        for (User user : users) {
            double weight = core.getWeight(userId, user.userId);
            if (weight == 0) continue;
            k += Math.abs(weight);
            result += user.ratingMeanError(mid) * weight;
        }
        return meanRating + (1 / k) * result;
    }
}

/**
 * Represent the Correlation Triangular Matrices W(i,j)
 * Calculate the correlation weight between user i and user j
 */
class Correlation {
    //weights[i][j] represents the correlation between user i and j
    double[][] weights;

    public Correlation(User[] users) {
        int size = CollaborativeFiltering.uidArray.length;
        weights = new double[size][];
        for (int i = 0; i < size; i++) {
            weights[i] = new double[i + 1];
            User u1 = users[i];
            Set<Integer> set1 = u1.ratings.keySet();
            for (int j = 0; j < i; j++) {
                User u2 = users[j];
                Set<Integer> commons = new HashSet<>(set1);//fastest way to copy a set, performance crucial!!!-----------------------------
                commons.retainAll(u2.ratings.keySet());//way too slow!!!!!!!!
                double s1 = 0, s2 = 0, s3 = 0;
                for (int k : commons) {
                    double v1 = u1.ratings.get(k) - u1.meanRating;
                    double v2 = u2.ratings.get(k) - u2.meanRating;
                    s1 += v1 * v2;
                    s2 += v1 * v1;
                    s3 += v2 * v2;
                }
                if ((s3 *= s2) != 0) weights[i][j] = s1 / Math.sqrt(s3);
            }
            weights[i][i] = 1;
        }
    }

    //If user id has never appeared in training database just return 0;
    double getWeight(int id1, int id2) {
        int i = Arrays.binarySearch(CollaborativeFiltering.uidArray, id1);//-!performance critical
        int j = Arrays.binarySearch(CollaborativeFiltering.uidArray, id2);//-!performance critical
        if (i != -1 && j != -1) {
            if (i > j) return weights[i][j];
            return weights[j][i];
        }
        return 0;
    }
}