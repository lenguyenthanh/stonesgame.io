package io.stonesgame;

import io.stonesgame.bots.Bots;
import io.stonesgame.config.GameConfiguration;
import io.stonesgame.web.GameJoiner;
import io.stonesgame.web.Player;
import io.stonesgame.web.WebGame;
import io.stonesgame.web.health.HealthCheckPlayer;
import io.stonesgame.web.health.PlayersQueueCapacityHealthCheck;
import io.stonesgame.web.websocket.GameWebSocketServlet;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.servlet.ServletRegistration;

public class GameApplication extends Application<GameConfiguration> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameApplication.class);

    private BlockingQueue<Player> players;
    private final ConcurrentMap<UUID, WebGame> games = new ConcurrentHashMap<>();

    private final Meter wsRequests = new Meter();

    public static void main(final String[] args) throws Exception {
        new GameApplication().run(args);
    }

    @Override
    public void run(final GameConfiguration configuration, final Environment environment) throws Exception {
        this.players = new ArrayBlockingQueue<>(configuration.getQueueCapacity());
        this.registerMetrics(environment);
        this.registerHealthChecks(configuration, environment);
        this.runGameJoiner(environment);
        this.runBots(configuration, environment);
        this.registerGameWebSocketServlet(environment);
    }

    private void registerHealthChecks(final GameConfiguration configuration, final Environment environment) {
        environment.healthChecks().register("player", new HealthCheckPlayer(this.players));
        environment.healthChecks().register(
                "players.queue.capacity",
                new PlayersQueueCapacityHealthCheck(configuration.getQueueCapacity(), this.players));
    }

    private void registerGameWebSocketServlet(final Environment environment) {
        final ServletRegistration.Dynamic ws = environment.servlets()
                .addServlet("game", new GameWebSocketServlet(
                        this.players, this.games, environment.getObjectMapper(), this.wsRequests));
        ws.addMapping("/game");
        ws.setAsyncSupported(true);
    }

    private void runGameJoiner(final Environment environment) {
        final ExecutorService executorService = environment.lifecycle()
                .executorService("game-joiner-%d")
                .maxThreads(1)
                .build();
        executorService.execute(new GameJoiner(this.players, this.games));
    }

    private void runBots(final GameConfiguration configuration, final Environment environment) {
        final boolean botsEnabled = configuration.getBots().isEnabled();
        LOGGER.info("Bots support is {}.", botsEnabled ? "enabled" : "not enabled");
        if (botsEnabled) {
            final ScheduledExecutorService scheduledExecutorService = environment.lifecycle()
                    .scheduledExecutorService("bots-%d")
                    .threads(1)
                    .build();
            scheduledExecutorService.scheduleAtFixedRate(
                    new Bots(this.players, configuration.getBots().getPoolSize()),
                    Bots.INITIAL_DELAY_IN_SECONDS,
                    configuration.getBots().getScheduledPeriodInSeconds(),
                    TimeUnit.SECONDS);
        }
    }

    private void registerMetrics(final Environment environment) {
        environment.metrics().register("active.games", (Gauge<Integer>) this.games::size);
        environment.metrics().register(
                "players.queue.remaining.capacity", (Gauge<Integer>) this.players::remainingCapacity);
        environment.metrics().register("ws.requests", this.wsRequests);
    }

    @Override
    public void initialize(final Bootstrap<GameConfiguration> bootstrap) {
        // allow for ease of configuration via environment variables
        // according to The Twelve-Factor App [http://12factor.net/], and deployment on Heroku
        bootstrap.setConfigurationSourceProvider(
                new SubstitutingSourceProvider(
                        bootstrap.getConfigurationSourceProvider(),
                        new EnvironmentVariableSubstitutor(false))); // not strict, allows undefined env vars
        // serve static files from root "/" and configure index page
        bootstrap.addBundle(new AssetsBundle("/assets", "/", "index.html", "assets"));
    }
}
