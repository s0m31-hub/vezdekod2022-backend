package org.nwolfhub.vezdekodbackend;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

@RestController
public class MainController {
    public static List<String> actors;
    public static List<Vote> votes; //VK asked not to use any database so here's your shitcode :3
    public static HashMap<String, Integer> voteAmount;

    private static Logger logger;
    private static LimitController voteController;
    private static LimitController getController;

    /**
     * Initializes MainController. Methods should not be called before initialization
     * @param maxGets - maximum result obtaining requests from IP in 1 minute
     * @param maxPosts - maximum vote requests from IP in 1 minute
     */
    public static void initialize(Integer maxGets, Integer maxPosts) throws IOException {
        logger = LogManager.getLogger();
        File voteFile = new File("votes.inf");
        if(!voteFile.exists()) votes = new ArrayList<>();
        else {
            try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(voteFile))) {
                votes = (ArrayList<Vote>) in.readObject();
                logger.info("Imported" + votes.size() + " votes");
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Bad formed votes file:" + e);
            }
        }
        voteAmount = new HashMap<>();
        for(Vote vote:votes) {
            if(voteAmount.containsKey(vote.getActor())) voteAmount.replace(vote.getActor(), voteAmount.get(vote.getActor()) + 1);
            else voteAmount.put(vote.getActor(), 1);
        }
        File cfg = new File("actors.cfg");
        if(!cfg.exists()) {
            cfg.createNewFile();
            logger.error("File " + cfg.getAbsolutePath() + " was created. Fill in actors data");
            System.exit(2);
        }
        try (FileInputStream in = new FileInputStream(cfg)) {
            String raw = new String(in.readAllBytes()).replace("\n", "");
            actors = Arrays.asList(raw.split(";"));
        }
        voteController = new LimitController(maxPosts);
        getController = new LimitController(maxGets);
        logger.info("Finished initialization");
    }

    /**
     * Vote - vote for any actor
     * !!!RATE LIMITED!!!
     * @param phone - phone number of a user who votes for this actor
     * @param actor - actor himself. Should be configured in config first
     * @param ip - ip of a user. Obtained using X-Forwarded-For, make sure that web server is configured to set it
     * @return Http code of a result
     */
    @GetMapping("/vote")
    public static ResponseEntity<String> vote (@RequestParam(value = "phone", defaultValue = "") String phone, @RequestParam(value = "artist", defaultValue = "") String actor, @RequestHeader(value = "X-Forwarded-For", defaultValue = "none") String ip) {
        HttpServletRequest request = ((ServletRequestAttributes) (RequestContextHolder.getRequestAttributes())).getRequest();
        if(ip.equals("none")) {
            logger.warn("Web server is not configured to set ip using a header. You will see this warning every request without X-Forwarded-For header. For nginx, add \"proxy_set_header X-Forwarded-For $remote_addr;\" to your config inside proxy pass body");
            ip = request.getRemoteAddr();
        }
        if(voteController.addRequest(ip)) {
            try {
                Vote vote = new Vote(phone, actor);
                votes.add(vote);
                if(voteAmount.containsKey(vote.getActor())) voteAmount.replace(vote.getActor(), voteAmount.get(vote.getActor()) + 1);
                else voteAmount.put(vote.getActor(), 1);
                return ResponseEntity.status(HttpStatus.CREATED).body("Voted");
            } catch (InvalidArgumentException e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
            }
        } else return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Too many requests");
    }

    public static void unloadVotes() {
        File f = new File("votes.inf");
        if(!f.exists()) {
            try {
                f.createNewFile();
            }catch (IOException e) {
                logger.error("Failed to unload votes. Cannot create votes.inf (" + e + ")");
            }
        }
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(f))) { //not using buffered cuz uploading just to 1 file btw
            out.writeObject(votes);
            out.flush();
            out.close();
        } catch (IOException e) {
            logger.error("Failed to unload votes (" + e + ")");
        }
    }

    /**
     * Get all votes with params
     * !!!RATE LIMITED!!!
     * @param fromString - start date
     * @param toString - end date
     * @param intervalsString - intervals. Will take me 1+ day to make my brains working so have to leave it unreleased
     * @param artist - required artist
     * @param ip - ip of a user. Obtained using X-Forwarded-For, make sure that web server is configured to set it
     * @return As documented (excluding intervals)
     */
    @GetMapping("/getVotes")
    public static ResponseEntity<String> getVotes (@RequestParam(value = "from", defaultValue = "0") String fromString, @RequestParam(value = "to", defaultValue = "ns") String toString,
                                                   @RequestParam(value = "intervals", defaultValue = "10") String intervalsString, @RequestParam(value = "artists", defaultValue = "") String artist,
                                                   @RequestHeader(value = "X-Forwarded-For", defaultValue = "none") String ip) {
        Long from;
        Long to;
        Integer intervals = 10;
        List<String> artists;
        HttpServletRequest request = ((ServletRequestAttributes) (RequestContextHolder.getRequestAttributes())).getRequest();
        if(ip.equals("none")) {
            logger.warn("Web server is not configured to set ip using a header. You will see this warning every request without X-Forwarded-For header. For nginx, add \"proxy_set_header X-Forwarded-For $remote_addr;\" to your config inside proxy pass body");
            ip = request.getRemoteAddr();
        }
        if(getController.addRequest(ip)) {
            try {
                from = Long.valueOf(fromString);
                to = Long.valueOf(toString.replace("ns", String.valueOf(System.currentTimeMillis() / 1000)));
                intervals = Integer.valueOf(intervalsString);
                artists = artist.equals("") ? actors : Arrays.asList(artist.split(","));
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("{\"text\": \"Server returned error" + e + "\"}");
            }
            Integer voteAmount = 0;
            for (Vote vote : votes) {
                if (artists.contains(vote.getActor())) {
                    if (vote.unix > from && vote.unix < to) voteAmount++;
                }
            }
            return ResponseEntity.status(HttpStatus.OK).body("{\"data\": [{\"start\": " + from + ", \"to\": " + to + ", \"votes\": " + voteAmount + "}]}");
        } else {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("{\"text\": \"too many requests\"}");
        }
    }

    public static class Vote implements Serializable {
        public String phone;
        public String actor;
        public Long unix;

        public String getPhone() {
            return phone;
        }

        public Vote setPhone(String phone) {
            this.phone = phone;
            return this;
        }

        public String getActor() {
            return actor;
        }

        public Vote setActor(String actor) {
            this.actor = actor;
            return this;
        }

        public Long getUnix() {
            return unix;
        }

        public Vote setUnix(Long unix) {
            this.unix = unix;
            return this;
        }

        public Vote() {
            this.unix = System.currentTimeMillis()/1000;
        }

        public Vote(String phone, String actor) {
            if(!actors.contains(actor)) {
                throw new InvalidArgumentException("Actor " + actor + " does not exist");
            }
            this.actor = actor;
            if(phone.length()==10 && phone.split("")[0].equals("9")) {
                this.phone = phone;
            } else throw new InvalidArgumentException(phone + " is not a legal phone number");
            this.unix = System.currentTimeMillis()/1000;
        }
    }

    public static class InvalidArgumentException extends RuntimeException {
        public InvalidArgumentException(String text) {
            super(text);
        }

        public InvalidArgumentException() {
            super();
        }
    }
}
