import java.io.*;
import java.util.LinkedList;
import java.util.Queue;
import java.util.TreeSet;

class ExternalSort {

    private TreeSet<String> set;
    private File inputFileName;
    private File tempFileDirectory;
    private int fileCounter;
    private long timestamp;
    private final static String TEMP_DIR = "/temp-";
    private final static int CONVERSION_FACTOR = 1024 * 1024;

    public ExternalSort(String inputFileName) {
        this.inputFileName = new File(inputFileName);
        this.timestamp = System.currentTimeMillis();
        this.tempFileDirectory = new File(this.inputFileName.getParent() + TEMP_DIR + timestamp);
        this.tempFileDirectory.deleteOnExit();
        this.set = new TreeSet<>(String::compareToIgnoreCase);
        this.fileCounter = 0;
    }

    /**
     * Starts the external sort algorithm. If there is memory available (2MB) then keeps adding words
     * to the TreeSet instance. Otherwise flushes the data into disk and runs garbage collector.
     * It requires a minimum of 2MB of free memory to run successfully.
     * After the input file has been divided into sorted runs, runs the external merge step.
     *
     * @throws IOException
     */
    public void runExternalSort() throws IOException {
        System.out.println("Starting external sort....");
        System.out.println("Reading from input file to create sorted runs.");
        try (FileReader fr = new FileReader(inputFileName);
             BufferedReader bf = new BufferedReader(fr)) {
            while (bf.ready()) {
                if (Runtime.getRuntime().freeMemory() > 2 * CONVERSION_FACTOR) {
                    String line = bf.readLine();
                    addToSet(line);
                } else if (!set.isEmpty()) {
                    flushToDisk();
                } else {
                    System.out.println("Not enough memory,exiting the program...");
                    System.exit(1);
                }
            }
            flushToDisk();
            System.out.println("Completed creation of sorted runs, starting the merge step.");
            mergeAllSortedRuns();
            System.out.println("Completed external merge sort successfully.");
        }
    }

    /**
     * Sorts the data in-memory and writes result to disk.
     * Used only for verification purposes.
     *
     * @throws IOException
     */
    public void sortInMemory() throws IOException {

        try (FileReader fr = new FileReader(inputFileName);
             BufferedReader br = new BufferedReader(fr)) {
            while (br.ready()) {
                String line = br.readLine();
                addToSet(line);
            }
            flushToDisk();
            mergeAllSortedRuns();
        }
    }

    /**
     * Takes a string as input, splits into words using whitespaces.
     * Sanitises each word to remove all special characters and numbers
     * and adds to the TreeSet.
     * <p>
     * Assumption : A word should not have special characters or numbers
     *
     * @param line
     */
    private void addToSet(String line) {
        String[] arr = line.split("\\s+");
        for (String str : arr) {
            String sanitized = str.replaceAll("[^A-Za-z]", "");
            if (!sanitized.isEmpty()) {
                set.add(sanitized);
            }
        }
    }

    /**
     * Creates a new temporary file in the temporary directory and flushes the words
     * from the TreeSet into it. Calls the garbage collector to potentially free up space.
     *
     * @throws IOException
     */
    private void flushToDisk() throws IOException {
        if (set.isEmpty()) {
            System.out.println("No data to flush to disk");
            return;
        }
        if (!tempFileDirectory.exists() && !tempFileDirectory.mkdir()) {
            throw new IOException("Could not create temp directory. " +
                    "Please provide absolute/relative path of the input file");
        }
        try (FileWriter fw = new FileWriter(tempFileDirectory.getPath() + "/" + fileCounter++);
             BufferedWriter bw = new BufferedWriter(fw)) {
            while (!set.isEmpty()) {
                bw.write(set.pollFirst());
                bw.newLine();
            }
        }
        Runtime.getRuntime().gc();
    }

    /**
     * Takes two sorted runs and merges them.
     *
     * @param file1
     * @param file2
     * @return
     * @throws IOException
     */
    private File merge(File file1, File file2) throws IOException {
        File file = new File(tempFileDirectory.getPath() + "/" + fileCounter++);
        try (FileReader fr1 = new FileReader(file1);
             FileReader fr2 = new FileReader(file2);
             FileWriter fw = new FileWriter(file);
             BufferedReader br1 = new BufferedReader(fr1);
             BufferedReader br2 = new BufferedReader(fr2);
             BufferedWriter bw = new BufferedWriter(fw)) {
            String word1 = br1.ready() ? br1.readLine() : null;
            String word2 = br2.ready() ? br2.readLine() : null;
            while (word1 != null && word2 != null) {
                int compare = word1.compareToIgnoreCase(word2);
                if (compare < 0) {
                    bw.write(word1);
                    word1 = br1.ready() ? br1.readLine() : null;
                } else if (compare > 0) {
                    bw.write(word2);
                    word2 = br2.ready() ? br2.readLine() : null;
                } else {
                    bw.write(word1);
                    word1 = br1.ready() ? br1.readLine() : null;
                    word2 = br2.ready() ? br2.readLine() : null;
                }
                bw.newLine();
            }
            while (word1 != null) {
                bw.write(word1);
                bw.newLine();
                word1 = br1.ready() ? br1.readLine() : null;
            }
            while (word2 != null) {
                bw.write(word2);
                bw.newLine();
                word2 = br2.ready() ? br2.readLine() : null;
            }
            return file;
        }
    }

    /**
     * Reads filenames in the temporary directory to a Queue
     */
    private Queue<File> readFileNamesToQueue() throws IOException {
        Queue<File> tempFiles = new LinkedList<>();
        if (tempFileDirectory.isDirectory()) {
            File[] files = tempFileDirectory.listFiles();
            for (File file : files) {
                tempFiles.offer(file);
            }
        } else {
            throw new IOException("Temp file directory does not exist!");
        }
        return tempFiles;
    }

    /**
     * Uses the queue data structure to read and merge first two files and pushes
     * the resultant file to the end of the queue. This is done till only one file
     * is left in the queue, which is the final output file.
     *
     * @throws IOException
     */
    private void mergeAllSortedRuns() throws IOException {
        Queue<File> tempFiles = readFileNamesToQueue();
        System.out.println("Merging " + tempFiles.size() + " initial sorted runs");
        while (tempFiles.size() > 1) {
            File file1 = tempFiles.poll();
            File file2 = tempFiles.poll();
            tempFiles.offer(merge(file1, file2));
            file1.delete();
            file2.delete();
        }
        File outputFile = new File(inputFileName.getParent() + "/" +
                this.timestamp + ".out");
        tempFiles.poll().renameTo(outputFile);
        System.out.println("Output file created: " + outputFile.getPath());
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Please provide absolute/relative path of the input file");
            System.exit(0);
        }
        String inputFile = args[0];
        try {
            ExternalSort externalSort = new ExternalSort(inputFile);
            externalSort.runExternalSort();
        } catch (Exception e) {
            System.out.println("Program terminated unexpectedly with message: " + e.getMessage());
        }
    }
}