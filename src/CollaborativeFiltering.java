/*
  Creator: Hao Xiong (haoxiong@outlook.com)
  Date: Mar 17, 2017
  Data Source: Netflix Prize
 */

import java.io.*;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

public class CollaborativeFiltering {
    private static final String TRAININGDATA = "data/TrainingRatings.txt";
    private static final String TESTINGDATA = "data/TestingRatings.txt";
    private static final String RESULTTEXT = "result.txt";
    private static long STARTTIME;
    private static double[][] weights;//weights[i][j] represents the correlation between user i and j

    public static void main(String[] args) {
        STARTTIME = System.nanoTime();
        int numberOfItems = 0;
        double mae = 0, rmse = 0;
        StringBuilder content = new StringBuilder(3600000);

        User.base = parseUsers(TRAININGDATA, true);
        content.append(log("Finished parsing training data."));

        User[] testUsers = parseUsers(TESTINGDATA, false);
        content.append(log("Finished parsing testing data."));

        calCorrelation();
        content.append(log("Finished Matrix Calculation."));

        //Todo::Use Java 8 stream and lambda expression to exploit parallelism
        Arrays.stream(testUsers).parallel()
                .filter(tu -> tu.index > -1)
                .forEach(tu -> {
                    for (int i = 0; i < tu.movieIds.length; i++) tu.predict(i);
                });

        //isolate the prediction calculation from output for thread safe reason
        DecimalFormat df = new DecimalFormat("#.####");
        for (int i = 0; i < testUsers.length; i++) {
            User tu = testUsers[i];
            content.append("User:" + User.Uids[i] + "\n");
            for (int j = 0; j < tu.movieIds.length; j++) {
                double error = tu.errors[j];
                int realRating = (int) tu.dRatings[j];
                double predicted = error + realRating;
                mae += Math.abs(error);
                rmse += error * error;
                numberOfItems++;
                content.append("\tMovie:" + tu.movieIds[j] + "=>" + df.format(predicted) + "(" + realRating + ")\n");
            }
        }
        content.append(log("\nMean Absolute Error: " + mae / numberOfItems
                + "\nRoot Mean Squared Error: " + Math.sqrt(rmse / numberOfItems)));
        content.append(log(memoStat()));
        if (saveRunningResult(content.toString(), RESULTTEXT))
            System.out.println("Success! Predicted results have been save to " + RESULTTEXT);
    }

    /**
     * Read the data set file convert them to user list
     */
    private static User[] parseUsers(String name, boolean isBase) {
        Map<Integer, Map<Integer, Float>> userRatings = new TreeMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(name))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] s = line.split(",");
                int[] v = {Integer.parseInt(s[0]), Integer.parseInt(s[1])};
                Float r = Float.parseFloat(s[2]);
                if (userRatings.containsKey(v[1])) {
                    userRatings.get(v[1]).put(v[0], r);
                } else {
                    Map<Integer, Float> rating = new TreeMap<>();
                    rating.put(v[0], r);//if same user rated two movie the previous rating will be overwritten.
                    userRatings.put(v[1], rating);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        User[] users = new User[userRatings.size()];
        if (isBase) User.Uids = new int[users.length];
        int i = 0;
        for (int uid : userRatings.keySet()) {
            if (isBase) User.Uids[i] = uid;//store uid into the mapping array Uid[]
            User user = new User(uid);
            user.preCompute(userRatings.get(uid), isBase, i);
            users[i++] = user;
        }
        return users;
    }

    static void calCorrelation() {
        int size = User.Uids.length;
        weights = new double[size][];
        for (int i = 0; i < size; i++) weights[i] = new double[i + 1];
        //Todo::Use Java 8 stream and lambda expression to exploit parallelism
        Arrays.stream(weights).parallel().forEach(ws -> {
            int i = ws.length - 1;
            User u1 = User.base[i];
            for (int j = 0; j < i; j++) {
                User u2 = User.base[j];
                double s1 = 0, s2 = 0, s3 = 0;
                int m = 0;
                int n = 0;
                while (m < u1.movieIds.length && n < u2.movieIds.length) {
                    int mid1 = u1.movieIds[m];
                    int mid2 = u2.movieIds[n];
                    if (mid1 < mid2) m++;
                    else if (mid1 > mid2) n++;
                    else {
                        float v1 = u1.dRatings[m++];
                        float v2 = u2.dRatings[n++];
                        s1 += v1 * v2;
                        s2 += v1 * v1;
                        s3 += v2 * v2;
                    }
                }
                if ((s3 *= s2) != 0) weights[i][j] = s1 / Math.sqrt(s3);
            }
            weights[i][i] = 1;
        });
    }

    static double getWeight(int i, int j) {
        return i > j ? weights[i][j] : weights[j][i];
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
        String output = "Time(s):" + (System.nanoTime() - STARTTIME) * 1.0e-9 + " " + debug + "\n";
        System.out.print(output);
        return output;
    }

    //Heap utilization statistics
    private static String memoStat() {
        float mb = 1024 * 1024;
        //Getting the runtime reference from system
        Runtime runtime = Runtime.getRuntime();
        return "\n" +
                "##### Heap utilization statistics [MB] #####"
                + "\nUsed Memory:" + (runtime.totalMemory() - runtime.freeMemory()) / mb
                + "\nFree Memory:" + runtime.freeMemory() / mb
                + "\nTotal Memory:" + runtime.totalMemory() / mb
                + "\nMax Memory:" + runtime.maxMemory() / mb;
    }
}

/**
 * User class contains its ratings
 */
class User {

    /**
     * The below 3 array are bond by their indices
     */
    int[] movieIds;
    float[] dRatings;
    double[] errors;

    float meanScore;
    int index;//Will eventually become consecutive id from 0 -> number of users for fast accessing
    static int[] Uids;//A map between the index of user in User[] and user id
    static User[] base;

    //Construct a user by its first rating record
    public User(int uid) {
        index = uid;
    }

    /**
     * Pre-compute some useful parameters for later usage
     */
    void preCompute(Map<Integer, Float> ratings, boolean isBase, int position) {
        int size = ratings.size();
        movieIds = new int[size];
        dRatings = new float[size];
        int i = 0;
        for (Map.Entry<Integer, Float> rating : ratings.entrySet()) {
            movieIds[i] = rating.getKey();
            float r = rating.getValue();
            dRatings[i++] = r;
            if (isBase) meanScore += r;
        }
        if (isBase) {
            index = position;
            meanScore /= size;
            for (int j = 0; j < size; j++) dRatings[j] -= meanScore;//cache (vote(j)-mean)
        } else {
            index = Arrays.binarySearch(Uids, index);//Will less than 0 if test user doesn't exist in database
            errors = new double[size];
        }
    }

    /**
     * Method used to calculate the difference between the predicted rating and real rating for one movie
     * return the real rating value
     */
    void predict(int i) {
        double result = 0;
        double norm = 0;
        for (User user : base) {
            int pos = Arrays.binarySearch(user.movieIds, movieIds[i]);
            //if the user has ever rated this movie
            if (pos > -1) {
                double w = CollaborativeFiltering.getWeight(index, user.index);
                if (w != 0) {
                    norm += Math.abs(w);
                    result += w * user.dRatings[pos];
                }
            }
        }
        errors[i] = base[index].meanScore + (norm > 0 ? result / norm : 0) - dRatings[i];
    }
}

