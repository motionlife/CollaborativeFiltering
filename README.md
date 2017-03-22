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
Running Time(s):383.519034659 <b>
Mean Absolute Error: 0.6746778890285756<b>
Root Mean Squared Error: 0.893566392544765<b>
Time(s):383.519687885 <b>
---Heap utilization statistics [MB]---<b>
Used Memory:3316.5117<b>
Free Memory:1590.4882<b>
Total Memory:4907.0<b>
Max Memory:4907.0<b>
