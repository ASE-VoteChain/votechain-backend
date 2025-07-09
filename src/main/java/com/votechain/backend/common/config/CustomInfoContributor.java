package com.votechain.backend.common.config;

import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.actuate.info.Info;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class CustomInfoContributor implements InfoContributor {

    @Override
    public void contribute(Info.Builder builder) {
        Map<String, Object> details = new HashMap<>();
        details.put("app", "VoteChain Backend");
        details.put("description", "Blockchain-based voting system");
        details.put("version", "1.0.0");
        details.put("lastStartup", LocalDateTime.now().toString());
        details.put("features", new String[]{"JWT Authentication", "Blockchain Integration", "PostgreSQL", "Swagger API"});

        builder.withDetails(details);
    }
}
