package me.apemanzilla.ccfuse;

import java.io.IOException;
import java.net.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Scanner;

import org.apache.commons.cli.*;
import org.java_websocket.framing.CloseFrame;

import lombok.val;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class CCFuse {
	private static final Options opts = new Options();

	static {
		opts.addOption(Option.builder("r").longOpt("relay").hasArg().desc("If passed, a relay will be run on this port")
				.build());

		opts.addOption(Option.builder("h").longOpt("host").hasArg()
				.desc("The relay to connect to. -c is also required when using this.").build());

		opts.addOption(Option.builder("c").longOpt("channel").hasArg()
				.desc("Specifies which channel to connect to on the relay.").build());

		opts.addOption(Option.builder("m").longOpt("mountpoint").hasArg()
				.desc("The base directory in which clients will be mounted").build());

		opts.addOption(Option.builder().longOpt("help").desc("Show usage information").build());
	}

	public static void printUsage() {
		new HelpFormatter().printHelp("ccfuse [options]", opts);
	}

	@FunctionalInterface
	private interface CloseCallback {
		void onClose() throws Exception;
	}

	public static void main(String[] args)
			throws IOException, InterruptedException, ParseException, URISyntaxException {
		val cli = new DefaultParser().parse(opts, args);

		if (cli.hasOption("help")) {
			printUsage();
			System.exit(1);
		}

		val onClose = new ArrayList<CloseCallback>();

		if (cli.hasOption('r')) {
			// run relay server
			val relayPort = Integer.parseInt(cli.getOptionValue('r'));

			val relay = new CCFuseRelay(new InetSocketAddress(relayPort));
			val relayThread = new Thread(relay);
			relayThread.setName("CCFuse Relay Server");
			relayThread.start();

			onClose.add(() -> relay.stop(3));
		}

		if (cli.hasOption('h')) {
			if (!cli.hasOption('c')) {
				printUsage();
				System.exit(1);
			}

			// run host
			val relayUri = new URI(cli.getOptionValue('h'));
			val mountpoint = Paths.get(cli.getOptionValue('m', "ccfuse-mnt"));
			val timeout = 10;

			val host = new CCFuseHost(relayUri.resolve("/" + cli.getOptionValue('c') + "/server"), mountpoint, timeout);
			val hostThread = new Thread(host);
			hostThread.setName("CCFuse Host");
			hostThread.start();

			onClose.add(() -> host.close(CloseFrame.GOING_AWAY, "Stopped"));
		}

		if (onClose.size() > 0) {
			val closer = new Thread(() -> {
				log.info("Press enter to stop");

				val scanner = new Scanner(System.in);
				scanner.nextLine();

				for (val c : onClose) {
					try {
						c.onClose();
					} catch (Exception e) {
						log.error("Error calling close callback", e);
					}
				}

				scanner.close();
			});

			closer.setName("CCFuse Closer");
			closer.setDaemon(true);
			closer.start();
		}
	}
}
