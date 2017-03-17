import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by Hao Xiong haoxiong@outlook.com
 * Mar 17, 2017
 */


public class CollaborativeFiltering {
    private static final String TRAININGDATA = "data/TrainingRatings.txt";
    private static final String TESTINGDATA = "data/TestingRatings.txt";

    public static void main(String[] args) {
        ArrayList<Rating> ratings = readData(TESTINGDATA);

    }

    private static ArrayList<Rating> readData(String name) {
        ArrayList<Rating> ratings = new ArrayList<>();
        String line;
        try (BufferedReader br = new BufferedReader(new FileReader(name))) {
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                ratings.add(new Rating(Integer.valueOf(values[0]), Integer.valueOf(values[1]), Float.valueOf(values[2])));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ratings;
    }

}

/**
 * Rating class to record each rating from data set
 */
class Rating {
    int movieID;
    int customerId;
    float rating;

    public Rating(int mid, int cid, Float rt) {
        movieID = mid;
        customerId = cid;
        rating = rt;
    }
}
