package edu.monash.registration.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Fires N registration requests at the same instant. Each thread builds its own request object
 * (MockMvc itself is thread-safe, request builders are not shared).
 */
public final class BurstSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BurstSupport() {
    }

    /** The shape both response records share, so count helpers work on either list. */
    public interface BurstResponse {

        int status();

        String errorCode();
    }

    public record Response(int status, String body) implements BurstResponse {

        public String errorCode() {
            try {
                JsonNode node = MAPPER.readTree(body);
                return node.path("error").path("code").asText(null);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    /**
     * The same response plus the wall-clock time the request took. Used by the all-failure probe,
     * which must prove that a saturated pool never turns rejections into slow rejections.
     */
    public record TimedResponse(int status, String body, long durationMillis)
            implements BurstResponse {

        public String errorCode() {
            try {
                JsonNode node = MAPPER.readTree(body);
                return node.path("error").path("code").asText(null);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    public static List<Response> registerInParallel(MockMvc mockMvc, List<String> tokens,
            UUID courseId) throws Exception {
        int threads = tokens.size();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Response>> futures = new ArrayList<>(threads);
        try {
            for (String token : tokens) {
                futures.add(pool.submit(() -> {
                    start.await();
                    MvcResult result = mockMvc.perform(post("/api/registrations")
                                    .header("Authorization", "Bearer " + token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"courseId\":\"" + courseId + "\"}"))
                            .andReturn();
                    return new Response(result.getResponse().getStatus(),
                            result.getResponse().getContentAsString());
                }));
            }
            start.countDown();
            List<Response> responses = new ArrayList<>(threads);
            for (Future<Response> future : futures) {
                responses.add(future.get(60, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            pool.shutdownNow();
        }
    }

    public static long countStatus(List<? extends BurstResponse> responses, int status) {
        return responses.stream().filter(r -> r.status() == status).count();
    }

    public static long countErrorCode(List<? extends BurstResponse> responses, String code) {
        return responses.stream().filter(r -> code.equals(r.errorCode())).count();
    }

    /**
     * Fires N registration requests at the same instant and records how long each one took. The
     * duration includes the framework's after-completion callbacks, i.e. the failure-audit write,
     * so a pool-starved audit path shows up as a slow request rather than a silent one.
     */
    public static List<TimedResponse> registerInParallelTimed(MockMvc mockMvc, List<String> tokens,
            UUID courseId) throws Exception {
        int threads = tokens.size();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<TimedResponse>> futures = new ArrayList<>(threads);
        try {
            for (String token : tokens) {
                futures.add(pool.submit(() -> {
                    start.await();
                    long began = System.nanoTime();
                    MvcResult result = mockMvc.perform(post("/api/registrations")
                                    .header("Authorization", "Bearer " + token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"courseId\":\"" + courseId + "\"}"))
                            .andReturn();
                    long durationMillis = (System.nanoTime() - began) / 1_000_000L;
                    return new TimedResponse(result.getResponse().getStatus(),
                            result.getResponse().getContentAsString(), durationMillis);
                }));
            }
            start.countDown();
            List<TimedResponse> responses = new ArrayList<>(threads);
            for (Future<TimedResponse> future : futures) {
                responses.add(future.get(60, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            pool.shutdownNow();
        }
    }
}
