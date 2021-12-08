package beautifuljava;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import java.nio.charset.Charset;

import java.util.ArrayList;
import java.util.List;

import javax.tools.JavaFileObject;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.file.PathFileObject;


public class BeautifulJava {

	public final static String SYMBOLS = "symbols.json";

	private JavacTool javacTool;
	private JavacFileManager fileManager;

	private File symbolsFile;
	private String lineEnding;
	private boolean dumpSymbols;
	private boolean dumpMissingSymbols;

	@SuppressWarnings("deprecation")
	public BeautifulJava(List<String> options) {
		Context context = new Context();
		fileManager = new JavacFileManager(context, true, Charset.forName("UTF-8"));
		javacTool = new JavacTool();

		for (String option : options) {

			if (option.equals("--cr"))
				lineEnding = "\r";

			else if (option.equals("--crlf"))
				lineEnding = "\r\n";

			else if (option.equals("--dump"))
				dumpSymbols = true;

			else if (option.equals("--dump-missing"))
				dumpMissingSymbols = true;

			else if (option.startsWith("--symbols=")) {
				int index = option.indexOf('=');
				symbolsFile = new File(option.substring(index + 1));
			}
		}

		if (symbolsFile == null || !symbolsFile.exists())
			symbolsFile = getPathToSymbols();
	}

	private File getPathToSymbols() {
		File classFile = new File(BeautifulJava.class.getClassLoader().getResource("beautifuljava/BeautifulJava.class").getPath());
		File repoDir = classFile.getParentFile().getParentFile().getParentFile();
		return new File(repoDir, SYMBOLS);
	}

	private static void findFiles(File sourceFile, List<File> sourceFiles) {
		if (!sourceFile.exists())
			return;

		else if (sourceFile.isFile() && sourceFile.getName().endsWith(".java"))
			sourceFiles.add(sourceFile);

		else if (sourceFile.isDirectory()) {
			for (File file : sourceFile.listFiles())
				findFiles(file, sourceFiles);
		}
	}

	public static void main(String[] args) {

		String lineEnding = null;
		boolean dumpSymbols = false;
		boolean dumpMissingSymbols = false;

		if (args.length == 0) {
			System.out.println("Usage: BeautifulJava [Java source files]");
			return;
		}

		List<String> options = new ArrayList<>();
		List<File> sourceFiles = new ArrayList<>();
		for (String arg : args) {

			if (arg.startsWith("--")) {
				options.add(arg);
			}
			else {
				File file = new File(arg);
				if (file.exists())
					findFiles(file, sourceFiles);
			}
		}

		new BeautifulJava(options).parseJavaSourceFile(sourceFiles);
	}

	private String getSourcePath(CompilationUnitTree codeTree) {
		return ((PathFileObject)codeTree.getSourceFile()).getPath().toString();
	}

	private void parseJavaSourceFile(List<File> sourceFiles) {

		Iterable<? extends JavaFileObject> javaFiles = fileManager.getJavaFileObjectsFromFiles((Iterable<File>)sourceFiles);
		JavacTask javacTask = (JavacTask)javacTool.getTask(null, fileManager, null, null, null, javaFiles);

		try {

			Iterable<? extends CompilationUnitTree> codeResult = javacTask.parse();

			if (dumpSymbols) {

				String message = dumpMissingSymbols ? "missing" : "valid";
				System.err.println("Dumping " + message + " symbols...");

				DumperVisitor dumper = new DumperVisitor(dumpMissingSymbols);
				//dumper.setDebug(true);
				for (CompilationUnitTree codeTree : codeResult)
					codeTree.accept(dumper, null);

				//dumper.debugSymbols();
				dumper.saveSymbols(symbolsFile);
				System.err.println("Done.");
			}
			else {

				VariableVisitor variableVisitor = new VariableVisitor();
				OutputVisitor outputVisitor = new OutputVisitor();
				outputVisitor.setLineEnding(lineEnding);
				if (symbolsFile.exists())
					outputVisitor.loadSymbols(symbolsFile);

				for (CompilationUnitTree codeTree : codeResult) {

					String sourcePath = getSourcePath(codeTree);
					System.out.println("Fixing " + sourcePath);

					codeTree.accept(variableVisitor, null);

					//variableVisitor.debugSymbols();

					File sourceFile = new File(sourcePath);
					File outputFile = new File(sourcePath + ".fixed");

					PrintStream out = new PrintStream(new FileOutputStream(outputFile));

					outputVisitor.setOut(out);
					outputVisitor.copy(variableVisitor);
					//outputVisitor.debugSymbols();

					codeTree.accept(outputVisitor, "");

					out.close();
					sourceFile.delete();
					outputFile.renameTo(sourceFile);
					variableVisitor.clear();
				}
			}
		}
		catch (IOException ioerror) {
			ioerror.printStackTrace();
		}
	}
}
