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
    private static final int ESTIMATED_SCORE = 3;
    private static long STARTTIME;

    public static void main(String[] args) {
        STARTTIME = System.nanoTime();
        int[] numberOfItems = {0};
        double[] ERROR = {0, 0};
        StringBuilder content = new StringBuilder();
        DecimalFormat df = new DecimalFormat("#.###");

        User[] allUsers = parseUsers(TRAININGDATA, true);
        content.append(log("Finished parsing training data."));

        User[] testUsers = parseUsers(TESTINGDATA, false);
        content.append(log("Finished parsing testing data."));

        Correlation.calCorrelation(allUsers);
        content.append(log("Finished Matrix Calculation."));

        //Todo::Use lambda expression to exploit parallelism
        Arrays.stream(testUsers).forEach(tsr -> {
            content.append("User:" + User.Uids[tsr.index] + "\n");
            Arrays.stream(tsr.movieIds).forEach(mid -> {
                double pScore = tsr.index > -1 ? allUsers[tsr.index].predictScore(allUsers, mid) : ESTIMATED_SCORE;
                int realRating = (int) tsr.dRatings[Arrays.binarySearch(tsr.movieIds, mid)];
                double error = pScore - realRating;
                ERROR[0] += Math.abs(error);
                ERROR[1] += error * error;
                numberOfItems[0]++;
                content.append("\tMovie:" + mid + "=>" + df.format(pScore) + "(" + realRating + ")\n");
            });
        });
        ERROR[0] = ERROR[0] / numberOfItems[0];
        ERROR[1] = Math.sqrt(ERROR[1] / numberOfItems[0]);
        content.append(log("\n\nMean Absolute Error: " + ERROR[0] + "\nRoot Mean Squared Error: " + ERROR[1]));
        content.append(log(memoStat()));
        saveRunningResult(content.toString(), RESULTTEXT);
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

    int[] movieIds;
    float[] dRatings;//for the locality of cpu cache we don't choose double
    float[] dSquares;
    float meanScore;
    //Will eventually become consecutive id from 0 -> number of users for fast accessing
    int index;
    //Cache the rating of latest found movie
    private float cache;
    //A map between the index of user in User[] and user id
    static int[] Uids;

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
            meanScore += r;
        }
        meanScore /= size;
        if (isBase) {
            index = position;
            dSquares = new float[size];
            for (int j = 0; j < size; j++) {
                dRatings[j] -= meanScore;//cache (vote(j)-mean)
                dSquares[j] = dRatings[j] * dRatings[j];
                //While consider the size and locality of cpu cache, may also store (vote(j)-mean)^2 to boost performance
            }
        } else {
            index = Arrays.binarySearch(Uids, index);//Will less than 0 if test user doesn't exist in database
        }
    }

    /**
     * return true if the user has rated movie specified by mid
     */
    boolean hasRated(int mid) {
        int i = Arrays.binarySearch(movieIds, mid);//performance crucial!!!
        boolean rated = i > -1;
        if (rated) cache = dRatings[i];
        return rated;
    }

    /**
     * Method used to calculate the predicted rating for one movie
     */
    double predictScore(User[] users, int mid) {
        final double[] result = {0};
        final double[] norm = {0};
        //Todo::Use lambda expression to exploit parallelism
        Arrays.stream(users).filter(usr -> usr.hasRated(mid)).forEach(usr -> {
            double w = Correlation.getWeight(index, usr.index);
            if (w != 0) {
                norm[0] += Math.abs(w);
                result[0] += usr.cache * w;
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
    private static double[][] weights;

    static void calCorrelation(User[] users) {
        int size = User.Uids.length;
        weights = new double[size][];
        for (int i = 0; i < size; i++) {
            weights[i] = new double[i + 1];
            User u1 = users[i];
            for (int j = 0; j < i; j++) {
                User u2 = users[j];
                double s1 = 0, s2 = 0, s3 = 0;
                int m = 0;
                int n = 0;
                while (m < u1.movieIds.length && n < u2.movieIds.length) {
                    if (u1.movieIds[m] < u2.movieIds[n]) m++;
                    else if (u1.movieIds[m] > u2.movieIds[n]) n++;
                    else {
                        s1 += u1.dRatings[m] * u2.dRatings[n];
                        s2 += u1.dSquares[m++];
                        s3 += u2.dSquares[n++];
                    }
                }
                if ((s3 *= s2) != 0) weights[i][j] = s1 / Math.sqrt(s3);
            }
            weights[i][i] = 1;
        }
    }

    static double getWeight(int i, int j) {
        return i > j ? weights[i][j] : weights[j][i];
    }
}