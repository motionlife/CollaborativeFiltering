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
    private static final String DEBUGDATA = "data/debug.txt";
    static int[] uidArray;

    public static void main(String[] args) {
        long start = System.nanoTime();

        User[] AllUsers = parseUsers(TRAININGDATA);
        Correlation core = new Correlation(AllUsers);
        System.out.println("The Correlation Matrix Calculation has Finished.");

        int j = 0;
        for (double[] d : core.weights) {
            for (double dd : d) System.out.print(dd + " ");
            System.out.println();
            if(j++==200) break;
        }

        //Map<Integer, User> TestUsers = parseUsers(TESTINGDATA);

        //partition the test user user in order to do multi-thread batch
//        Set<Map.Entry<Integer, User>> tUsers = TestUsers.entrySet();
//        List<Map.Entry<Integer, User>> tusers = new ArrayList<>(tUsers);
//        int size = tusers.size();
//        predictVote(correlation, tusers.subList(0, size / 3), AllUsers);
//        predictVote(correlation, tusers.subList(size / 3, 2 * size / 3), AllUsers);
//        predictVote(correlation, tusers.subList(2 * size / 3, size - 1), AllUsers);

        System.out.println("Time consumption(s): " + (System.nanoTime() - start) * 1.0e-9);
        memoStat();
    }

//    private static void predictVote(Correlation correlation, List<Map.Entry<Integer, User>> tusers, Map<Integer, User> allUsers) {
//        new Thread(() -> {
//            for (Map.Entry<Integer, User> entry : tusers) {
//                User activeUser = entry.getValue();
//                wrong this test user may never appeared in training database before => pv=0
//                for (int mid : activeUser.ratings.keySet()) {
//                    double pv = activeUser.predictedVote(correlation, allUsers, mid);
//                    System.out.println("user id: " + activeUser.userId + " predicted vote for movie " + mid + ": " + pv);
//                }
//            }
//        }).start();
//    }

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
            int i = 0;
            for (int uid : userMap.keySet()) {
                uidArray[i++] = uid;
            }
            Arrays.sort(uidArray);//For binary search
        }
        int i = 0;
        for (int uid : uidArray) {
            User user = userMap.get(uid);
            user.calMeanVote();
            users[i++] = user;
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
//    double predictedVote(Correlation core, User[] users, int mid) {
//        //if (getRating(mid)!=0) return getRating(mid);
//        double result = 0;
//        double k = 0;
//        for (Map.Entry<Integer, User> entry : users.entrySet()) {
//            double weight = core.getWeight(userId, entry.getKey());
//            if (weight == 0) continue;
//            k += Math.abs(weight);
//            result += entry.getValue().ratingMeanError(mid) * weight;
//        }
//        return meanVote + (1 / k) * result;
//    }
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
                Set<Integer> common = new HashSet<>(set1);//fastest way to copy a set, performance crucial!!!-----------------------------
                common.retainAll(u2.ratings.keySet());
                if (!common.isEmpty()) {
                    double s1 = 0, s2 = 0, s3 = 0;
                    for (int k : common) {
                        double v1 = u1.ratings.get(k) - u1.meanRating;
                        double v2 = u2.ratings.get(k) - u2.meanRating;
                        s1 += v1 * v2;
                        s2 += v1 * v1;
                        s3 += v2 * v2;
                    }
                    if ((s3 *= s2) != 0) weights[i][j] = s1 / Math.sqrt(s3);
                }
            }
            weights[i][i] = 1;
        }
    }

    //If user id has never appeared in training database just return 0;
    double getWeight(int id1, int id2) {
        int i = Arrays.binarySearch(CollaborativeFiltering.uidArray, id1);//---------!!!!!!!!!!!!!!!!!!!!!!!!!!!!!performance critical
        int j = Arrays.binarySearch(CollaborativeFiltering.uidArray, id2);//---------!!!!!!!!!!!!!!!!!!!!!!!!!!!!!performance critical
        if (i != -1 && j != -1) {
            if (i > j) return weights[i][j];
            return weights[j][i];
        }
        return 0;
    }
}