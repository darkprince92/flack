package flack;

import flack.locator.runone;

import java.io.*;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class icse21 {
    static {
//        configureNativeSolvers();
    }

    private static void configureNativeSolvers() {
        final String mappedLibraryName = System.mapLibraryName("minisatprover");
        final File nativeDir = resolveNativeDir(mappedLibraryName);

        if (nativeDir == null) {
            System.err.println("Warning: native solver directory not found. Set -Dflack.solver.dir=<dir containing " + mappedLibraryName + ">.");
            return;
        }

        final File libraryFile = new File(nativeDir, mappedLibraryName);
        try {
            // Preload with absolute path; this avoids brittle java.library.path mutation at runtime.
            System.load(libraryFile.getAbsolutePath());

            String current = System.getProperty("java.library.path", "");
            if (!current.contains(nativeDir.getAbsolutePath())) {
                System.setProperty("java.library.path", nativeDir.getAbsolutePath() + File.pathSeparator + current);
            }
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Warning: could not load native solver from " + libraryFile.getAbsolutePath() + ": " + e.getMessage());
        }
    }

    private static File resolveNativeDir(String mappedLibraryName) {
        List<File> candidates = new ArrayList<>();

        String override = System.getProperty("flack.solver.dir");
        if (override != null && !override.trim().isEmpty()) {
            candidates.add(new File(override.trim()));
        }

        String os = System.getProperty("os.name", "").toLowerCase();
        File cwd = new File(".").getAbsoluteFile();
        if (os.contains("mac")) {
            candidates.add(new File(cwd, "src/main/alloy/x86-mac"));
            candidates.add(new File(cwd, "x86-mac"));
        }
        candidates.add(new File(cwd, "solvers"));

        try {
            File codeSource = new File(icse21.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsoluteFile();
            File base = codeSource.isDirectory() ? codeSource : codeSource.getParentFile();
            if (os.contains("mac")) {
                candidates.add(new File(base, "x86-mac"));
                candidates.add(new File(base, "../resources/main/x86-mac"));
            }
            candidates.add(new File(base, "solvers"));
        } catch (URISyntaxException ignored) {
            // Fall back to the working-directory candidates above.
        }

        for (File candidate : candidates) {
            File libFile = new File(candidate, mappedLibraryName);
            if (libFile.exists()) {
                return candidate;
            }
        }
        return null;
    }

    public static void main(String args[]){
        File file = new File("table3.txt");
        BufferedReader reader;
        try{
            reader = new BufferedReader( new FileReader(file));
            String line = reader.readLine();
            while(line != null){
                if(!line.startsWith("#")) {
                    String path = "benchmark/alloyfl/" + line + ".als";
                    runone.run(path, 5, true);
                }
                line = reader.readLine();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}