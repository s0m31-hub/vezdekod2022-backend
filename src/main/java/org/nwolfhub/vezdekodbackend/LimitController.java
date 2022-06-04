package org.nwolfhub.vezdekodbackend;

import java.util.HashMap;

//p.s. I prefer using another version of LimitController written on Kotlin, yet VK wants damn per-minute limit instead of hard calculations :(
public class LimitController {
    public Integer maxRequests;
    public HashMap<String, Integer> requests;
    public Long nextCleanup;

    public LimitController(Integer maxRequests) {
        this.maxRequests = maxRequests;
        this.requests = new HashMap<>();
        this.nextCleanup = System.currentTimeMillis() + 60000;
    }

    public boolean addRequest(String ip) {
        if(nextCleanup<=System.currentTimeMillis()) {
            requests = new HashMap<>();
            nextCleanup = System.currentTimeMillis() + 60000;
        }
        if (!requests.containsKey(ip)) requests.put(ip, 1);
        requests.replace(ip, requests.get(ip) + 1);
        return maxRequests>=requests.get(ip);
    }
}
