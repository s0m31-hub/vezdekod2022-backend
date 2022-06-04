package org.nwolfhub.vezdekodbackend;

import com.sun.tools.javac.Main;
import org.apache.logging.log4j.LogManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

@SpringBootApplication
public class VezdekodBackendApplication {

	public static void main(String[] args) throws IOException {
		String next = "";
		int get = 20;
		int post = 3;
		for(String arg:args) {
			if(next.equals("get")) get = Integer.parseInt(arg);
			else if(next.equals("post")) post = Integer.parseInt(arg);
			else switch (arg) {
					case "--get":
					case "-g":
						next = "get";
						break;
					case "--post":
					case "-p":
						next = "post";
						break;
				}
		}
		MainController.initialize(get, post);
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			MainController.unloadVotes();
			LogManager.getLogger().info("Uploaded votes. Goodbye :)");
		}));
		SpringApplication.run(VezdekodBackendApplication.class, args);
	}

}
