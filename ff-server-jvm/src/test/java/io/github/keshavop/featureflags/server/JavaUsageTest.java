package io.github.keshavop.featureflags.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.keshavop.featureflags.core.context.EvaluationContext;
import io.github.keshavop.featureflags.sync.TestDataSource;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** The server SDK must be usable from plain Java (LLD §10.4). */
class JavaUsageTest {
    @Test
    void createConfigureAndEvaluateFromJava() {
        TestDataSource td = new TestDataSource();
        td.update(td.flag("java-flag").variationForAll(true));
        ServerConfig config = ServerConfig.builder()
                .dataSource(new ServerDataSource.Custom(td))
                .startWait(Duration.ofSeconds(1))
                .build();
        try (FeatureFlagServerClient client = FeatureFlagServer.create("java-key", config)) {
            EvaluationContext user = EvaluationContext.user("u1").set("plan", "pro").build();
            assertTrue(client.boolVariation("java-flag", user, false));
            assertEquals("x", client.stringVariation("missing", user, "x"));
            client.track("java-event", user);
        }
    }
}
