import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import java.util.stream.Stream;

import org.json.simple.parser.ParseException;

public class Main {

	public static void main(String[] args) {
		boolean running = true;
		Scanner scanner = new Scanner(System.in);
		while (running) {
			System.out.println("Enter file directory:");
			String dir = scanner.nextLine();
			System.out.println("Enter output directory:");
			String dirOut = scanner.nextLine();
			
			File file = new File(dir);
			File[] models = file.listFiles();
			Stream.of(models).parallel().forEach((m) -> {
				Parser parser = new Parser();
				try {
					parser.parseJson(m.getAbsolutePath());
				} catch (IOException | ParseException e) {
					System.out.println("Invalid file at: " + m);
				}
				Builder builder = new Builder((dirOut.endsWith("\\") ? dirOut : dirOut + "\\") + removeFileEnding(m));
				builder.build(parser);
			});
			
			System.out.println("type exit to close or anything else to continue:");
			if (scanner.nextLine().equals("exit")) running = false;
		}
		scanner.close();
	  }
	
	/**
	 * Removes file ending from file
	 * @param file File whichs ending need to be removed
	 * @return file path without file ending
	 */
	private static String removeFileEnding(File file) {
		String fileName = file.getName();
		if (fileName.indexOf(".") > 0) {
			   return fileName.substring(0, fileName.lastIndexOf("."));
			} else {
			   return fileName;
			}
	}	

}
