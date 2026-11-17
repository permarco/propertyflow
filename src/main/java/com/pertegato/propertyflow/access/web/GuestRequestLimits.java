package com.pertegato.propertyflow.access.web;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("propertyflow-backend & !frontend-demo")
public class GuestRequestLimits {

    private static final int MAX_TRACKED_ADDRESSES = 10_000;

    private final Window submissions;
    private final Window personalLinks;

    public GuestRequestLimits(
            @Value("${propertyflow.web.guest.submissions-per-minute:0}") int submissionsPerMinute,
            @Value("${propertyflow.web.guest.link-reads-per-minute:0}") int linkReadsPerMinute) {
        submissions = new Window(submissionsPerMinute);
        personalLinks = new Window(linkReadsPerMinute);
    }

    public boolean allowSubmission(String remoteAddress) {
        return submissions.allow(remoteAddress);
    }

    public boolean allowPersonalLink(String remoteAddress) {
        return personalLinks.allow(remoteAddress);
    }

    private static final class Window {

        private final int limit;
        private final Map<String, Integer> uses = new HashMap<>();
        private long currentMinute;

        private Window(int limit) {
            if (limit < 0) {
                throw new IllegalArgumentException("Guest request limits must be non-negative");
            }
            this.limit = limit;
        }

        private synchronized boolean allow(String address) {
            if (limit == 0) {
                return true;
            }
            long minute = Math.floorDiv(Clock.systemUTC().instant().getEpochSecond(), 60);
            if (minute != currentMinute) {
                uses.clear();
                currentMinute = minute;
            }
            int count = uses.getOrDefault(address, 0);
            if (count >= limit || (count == 0 && uses.size() >= MAX_TRACKED_ADDRESSES)) {
                return false;
            }
            uses.put(address, count + 1);
            return true;
        }
    }
}
