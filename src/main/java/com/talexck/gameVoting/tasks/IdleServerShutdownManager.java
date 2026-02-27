package com.talexck.gameVoting.tasks;

import com.talexck.gameVoting.GameVoting;
import com.talexck.gameVoting.api.cloudnet.CloudNetAPI;
import eu.cloudnetservice.driver.service.ServiceInfoSnapshot;
import eu.cloudnetservice.modules.bridge.BridgeServiceHelper;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scans all CloudNet services and stops non-lobby/proxy services that stay empty for configured duration.
 */
public final class IdleServerShutdownManager {

    private final GameVoting plugin;
    private final ConcurrentHashMap<UUID, Long> idleSinceByService = new ConcurrentHashMap<>();
    private BukkitTask checkTask;

    public IdleServerShutdownManager(GameVoting plugin) {
        this.plugin = plugin;
    }

    public void start() {
        boolean enabled = plugin.getConfig().getBoolean("idle-shutdown.enabled", true);
        if (!enabled) {
            return;
        }

        int idleMinutes = plugin.getConfig().getInt("idle-shutdown.idle-minutes", 10);
        int intervalSeconds = plugin.getConfig().getInt("idle-shutdown.check-interval-seconds", 60);
        if (idleMinutes <= 0 || intervalSeconds <= 0) {
            plugin.getLogger().warning("idle-shutdown config is invalid, auto shutdown disabled.");
            return;
        }

        long periodTicks = intervalSeconds * 20L;
        checkTask = Bukkit.getScheduler().runTaskTimer(plugin, this::scanAndShutdownIdleServices, periodTicks, periodTicks);
        plugin.getLogger().info("Idle auto shutdown task started, threshold=" + idleMinutes + " minute(s).");
    }

    public void shutdown() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
        idleSinceByService.clear();
    }

    private void scanAndShutdownIdleServices() {
        int idleMinutes = plugin.getConfig().getInt("idle-shutdown.idle-minutes", 10);
        long thresholdMillis = idleMinutes * 60L * 1000L;
        long now = System.currentTimeMillis();

        try {
            List<ServiceInfoSnapshot> services = CloudNetAPI.getInstance().getServices().stream().toList();
            Set<UUID> scanned = new HashSet<>();

            for (ServiceInfoSnapshot service : services) {
                UUID serviceId = service.serviceId().uniqueId();
                scanned.add(serviceId);

                if (!isServiceEligible(service)) {
                    idleSinceByService.remove(serviceId);
                    continue;
                }

                if (!BridgeServiceHelper.emptyService(service)) {
                    idleSinceByService.remove(serviceId);
                    continue;
                }

                long idleSince = idleSinceByService.computeIfAbsent(serviceId, k -> now);
                if (now - idleSince < thresholdMillis) {
                    continue;
                }

                try {
                    plugin.getLogger().warning("Service " + service.name() + " has been idle for "
                        + idleMinutes + " minute(s), stopping via CloudNet.");
                    CloudNetAPI.getInstance().stopService(serviceId);
                } catch (Exception ex) {
                    plugin.getLogger().severe("Failed to stop idle service " + service.name() + ": " + ex.getMessage());
                    ex.printStackTrace();
                } finally {
                    idleSinceByService.remove(serviceId);
                }
            }

            idleSinceByService.keySet().retainAll(scanned);
        } catch (Exception ex) {
            plugin.getLogger().warning("Idle shutdown scan failed: " + ex.getMessage());
        }
    }

    private boolean isServiceEligible(ServiceInfoSnapshot service) {
        if (!"RUNNING".equalsIgnoreCase(service.lifeCycle().name())) {
            return false;
        }

        String task = service.serviceId().taskName() == null
            ? ""
            : service.serviceId().taskName().toLowerCase(Locale.ROOT);
        String name = service.name() == null
            ? ""
            : service.name().toLowerCase(Locale.ROOT);

        List<String> excludedTaskKeywords = plugin.getConfig().getStringList("idle-shutdown.excluded-task-keywords");
        List<String> excludedServiceKeywords = plugin.getConfig().getStringList("idle-shutdown.excluded-service-keywords");

        return !containsAny(task, excludedTaskKeywords) && !containsAny(name, excludedServiceKeywords);
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}

