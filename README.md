# Collaborative Filtering Algorithm
If you don't use intellij idea, please copy the source file CollaborativeFiltering.java out (with the same directory as "data" folder).
### Compile
`javac *.java`

### Run
Require at least 5GB memory to run given data set:<br>
`java -Xms5g -Xmx5g CollaborativeFiltering`<br>
 As a general rule, set minimum heap size (-Xms) equal to the maximum heap size (-Xmx) to minimize garbage collections.
 This is only for achieve the best performance.

### ATTENTION
This code uses java 8 stream technology to fully utilize the parallelism of your multicore cpu. 
It will consume almost all of your cpu resource and more than 4gb ram at peak. When tested on my lousy laptop, it took only about 7 minutes to finish the running.

### Result
Running Time(s):383.519034659 <br>
Mean Absolute Error: 0.6746778890285756<br>
Root Mean Squared Error: 0.893566392544765<br>
Time(s):383.519687885 <br>
---Heap utilization statistics [MB]---<br>
Used Memory:3316.5117<br>
Free Memory:1590.4882<br>
Total Memory:4907.0<br>
Max Memory:4907.0<br>
