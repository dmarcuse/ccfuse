package me.apemanzilla.ccfuse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;

import org.apache.commons.cli.*;

import lombok.val;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class CCFuse {
	private static final Options opts = new Options();

	static {
		opts.addOption(
				Option.builder("p").longOpt("port").hasArg().desc("The port to run the websocket server on").build());

		opts.addOption(Option.builder("m").longOpt("mountpoint").hasArg()
				.desc("The base directory in which clients will be mounted").build());

		opts.addOption(Option.builder("h").longOpt("help").desc("Show usage information").build());
	}

	public static void printUsage() {
		new HelpFormatter().printHelp("ccfuse [options]", opts);
	}

	public static void main(String[] args) throws IOException, InterruptedException {
		CommandLine cli = null;
		try {
			cli = new DefaultParser().parse(opts, args);
		} catch (ParseException e) {
			log.error("Parse error: {}", e);
			printUsage();
			System.exit(-1);
		}

		if (cli.hasOption("h")) {
			new HelpFormatter().printHelp("ccfuse [options]", opts);
			System.exit(1);
		}

		val port = Integer.parseInt(cli.getOptionValue("p", "4533"));
		val mount = Paths.get(cli.getOptionValue("m", "ccfuse-mnt"));
		Files.createDirectories(mount);

		val server = new CCFuseHost(port, mount);
		server.start();

		val scanner = new Scanner(System.in);

		log.info("Press enter to stop.");
		scanner.nextLine();
		log.info("Stopping...");
		server.stop();
		log.info("Goodbye");

		scanner.close();
		System.exit(0);
	}
}
