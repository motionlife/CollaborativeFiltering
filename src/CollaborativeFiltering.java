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

    public static void main(String[] args) {
        STARTTIME = System.nanoTime();
        int numberOfRatings = 0;
        double mae = 0, rmse = 0;
        StringBuilder content = new StringBuilder(3600000);

        User.base = parseRatings(TRAININGDATA, true);
        content.append(log("Finished parsing training data."));

        User[] testUsers = parseRatings(TESTINGDATA, false);
        content.append(log("Finished parsing testing data."));

        //Todo::Use Java 8 stream and lambda expression to exploit parallelism
        for (User user : User.base) {
            Arrays.stream(User.base).parallel()
                    .filter(user::needCalculate)
                    .forEach(user::correlation);
        }
        content.append(log("Finished Matrix Calculation."));

        //Todo::Use Java 8 stream and lambda expression to exploit parallelism
        Arrays.stream(testUsers).parallel()
                .filter(User::canBePredicted)
                .forEach(User::predict);

        //Isolate the output code from the prediction calculation for thread safe reason
        DecimalFormat df = new DecimalFormat("#.####");
        for (int i = 0; i < testUsers.length; i++) {
            User tu = testUsers[i];
            content.append("User:" + User.Uids[i] + "\n");
            for (int j = 0; j < tu.movieIds.length; j++) {
                double pr = tu.pRatings[j];
                int rr = (int) tu.dRatings[j];
                double error = pr - rr;
                mae += Math.abs(error);
                rmse += error * error;
                numberOfRatings++;
                content.append("\tMovie:" + tu.movieIds[j] + "=>" + df.format(pr) + "(" + rr + ")\n");
            }
        }
        content.append(log("\nMean Absolute Error: " + mae / numberOfRatings
                + "\nRoot Mean Squared Error: " + Math.sqrt(rmse / numberOfRatings)));
        content.append(log(memoStat()));
        if (saveResult(content.toString(), RESULTTEXT))
            System.out.println("Success! Predicted results have been save to " + RESULTTEXT);
    }

    /**
     * Read the data set file convert them to user list
     */
    private static User[] parseRatings(String name, boolean isBase) {
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

        int size = userRatings.size();
        User[] users = new User[size];
        if (isBase) {
            User.Uids = new int[size];
            User.weights = new double[size][];
            for (int i = 0; i < size; i++) User.weights[i] = new double[i + 1];
        }
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
    private static boolean saveResult(String content, String filename) {
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

    static User[] base;//Store all the training examples, serve as an user database
    static int[] Uids;//A map between the user id an its position in base array
    static double[][] weights;//weights[i][j] represents the correlation between user i and j

    int index;//the index of an user object in base array

    /**
     * Those 3 arrays are bond by their indices
     */
    int[] movieIds;//store the movie ids
    float[] dRatings;//store real rating of a movie, later updated to its deviation: v(i)-mean

    double[] pRatings;//store predicted ratings, only constructed for test users
    float ratingMean;

    //Construct a user by its first rating record
    public User(int uid) {
        index = uid;
    }

    /**
     * Pre-compute some useful variables and cache them for later usage
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
            if (isBase) ratingMean += r;
        }
        if (isBase) {
            ratingMean /= size;
            index = position;
            for (int j = 0; j < size; j++) {
                dRatings[j] -= ratingMean;//cache (vote(j)-mean)
            }
        } else {
            index = Arrays.binarySearch(Uids, index);//Will less than 0 if test user doesn't exist in database
            pRatings = new double[size];
        }
    }

    /**
     * Tell if the test user could be predicted, that is if it has ever been recorded before
     */
    boolean canBePredicted() {
        return index > -1;
    }

    /**
     * Method used to calculate the difference between the predicted rating and real rating for one movie
     * return the real rating value
     */
    void predict() {
        for (int i = 0; i < movieIds.length; i++) {
            double result = 0;
            double norm = 0;
            for (User user : base) {
                int pos = Arrays.binarySearch(user.movieIds, movieIds[i]);
                //if the user has ever rated this movie
                if (pos > -1) {
                    double w = User.getWeight(index, user.index);
                    norm += Math.abs(w);
                    result += w * user.dRatings[pos];
                }
            }
            pRatings[i] = base[index].ratingMean + (norm > 0 ? result / norm : 0);
        }
    }

    /**
     * If this user need to calculate correlation with the specified user
     */
    boolean needCalculate(User u) {
        return index < u.index;
    }

    /**
     * calculation the correlation between this user and another user
     */
    void correlation(User u) {
        double s1 = 0, s2 = 0, s3 = 0;
        int i = 0;
        int j = 0;
        while (i < movieIds.length && j < u.movieIds.length) {
            int m = movieIds[i];
            int n = u.movieIds[j];
            if (m < n) i++;
            else if (m > n) j++;
            else {
                float v1 = dRatings[i++];
                float v2 = u.dRatings[j++];
                s1 += v1 * v2;
                s2 += v1 * v1;
                s3 += v2 * v2;
            }
        }
        if ((s3 *= s2) != 0) weights[u.index][index] = s1 / Math.sqrt(s3);
    }

    static double getWeight(int i, int j) {
        return i > j ? weights[i][j] : weights[j][i];
    }
}
